package com.disaster;

import com.backup.BackupException;
import com.backup.BackupListener;
import com.backup.BackupManager;
import com.backup.BackupResult;
import com.backup.BackupStorage;
import com.backup.BackupStrategy;
import com.backup.dialect.SqlServerDialect;
import com.backup.strategy.EncryptingBackupStrategy;
import com.backup.strategy.JdbcSqlDumpBackupStrategy;
import com.backup.strategy.SqlServerNativeBackupStrategy;
import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.incident.DbHealthMonitor;
import com.incident.FileIncidentSink;
import com.incident.IncidentLogger;
import com.incident.IncidentType;
import com.security.AppConfig;

import com.event.DataChangedEvent;
import com.service.CartService;

import java.io.File;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Supplier;

public final class DisasterRecoveryBootstrap {

    private static volatile BackupManager backupManager;
    private static volatile FileIncidentSink incidentSink;
    private static volatile DbHealthMonitor healthMonitor;
    private static volatile boolean initialized = false;
    private static volatile String lastInitFailureMessage;

    private DisasterRecoveryBootstrap() {}

    private static long parseLong(String value, long fallback) {
        try { return Long.parseLong(value.trim()); } catch (Exception e) { return fallback; }
    }

    public static synchronized void init() {
        if (initialized) return;

        AppConfig config = AppConfig.getInstance();

        File backupDir = new File(config.get("BACKUP_DIR", "backups"));
        File incidentDir = new File(config.get("INCIDENT_DIR", "incidents"));
        long intervalMinutes = parseLong(config.get("BACKUP_INTERVAL_MINUTES", "1440"), 1440L);
        int retentionCount = config.getInt("BACKUP_RETENTION_COUNT", 14);
        long healthCheckIntervalSec = parseLong(config.get("DB_HEALTH_CHECK_INTERVAL_SEC", "30"), 30L);
        int failureThreshold = config.getInt("DB_HEALTH_FAILURE_THRESHOLD", 5);
        long staleHours = parseLong(config.get("EMERGENCY_BACKUP_STALE_HOURS", "1"), 1L);

        incidentSink = new FileIncidentSink(incidentDir);
        IncidentLogger.getInstance().addSink(incidentSink);

        IncidentLogger.getInstance().addListener(incident -> {
            try {
                switch (incident.getSeverity()) {
                    case CRITICAL:
                    case HIGH:
                        AppLogger.getInstance().error(ErrorCode.SYSTEM_UNCAUGHT, incident.toString(), null);
                        break;
                    case MEDIUM:
                        AppLogger.getInstance().warn("system", incident.toString());
                        break;
                    default:
                        AppLogger.getInstance().info("system", incident.toString());
                }
            } catch (Exception ignored) {
                // DB co the dang la nguon su co - incident goc van an toan trong FileIncidentSink.
            }
        });

        BackupStorage storage = new BackupStorage(backupDir);
        String dbName = SIMSBackupTarget.currentDatabaseName();

        SqlServerNativeBackupStrategy nativeStrategy = new SqlServerNativeBackupStrategy(
                SIMSBackupTarget.appConnectionProvider(),
                SIMSBackupTarget.masterConnectionProvider(),
                dbName);

        JdbcSqlDumpBackupStrategy jdbcFallback = new JdbcSqlDumpBackupStrategy(
                SIMSBackupTarget.appConnectionProvider(),
                new SqlServerDialect(), "dbo",
                JdbcSqlDumpBackupStrategy.noExclusions());

        List<BackupStrategy> strategiesInOrder = buildStrategyChain(config, nativeStrategy, jdbcFallback);

        backupManager = new BackupManager(storage, strategiesInOrder);
        backupManager.addListener(new BackupListener() {
            @Override
            public void onBackupSucceeded(BackupResult result) {
                IncidentLogger.getInstance().low(IncidentType.BACKUP_SUCCEEDED, "BackupManager",
                        "Backup thanh cong bang " + result.getStrategyName() + " -> " + result.getFile().getName()
                                + " (" + result.getSizeBytes() + " bytes, " + result.getDuration() + ")");
                storage.cleanupOldBackups(retentionCount);
            }
            @Override
            public void onBackupFailed(String strategyName, Exception error) {
                IncidentLogger.getInstance().high(IncidentType.BACKUP_FAILED, "BackupManager",
                        "Backup that bai voi strategy " + strategyName + ": " + error.getMessage(), error);
            }
            @Override
            public void onRestoreSucceeded(String strategyName, File file) {
                IncidentLogger.getInstance().critical(IncidentType.RESTORE_PERFORMED, "BackupManager",
                        "Da phuc hoi DB tu file " + file.getName() + " (strategy " + strategyName + ")", null);

                // Xóa cache session trong process Java (giỏ hàng vẫn giữ data cũ nếu không clear).
                try {
                    CartService.getInstance().clear();
                } catch (Exception ignored) {
                    // CartService có thể chưa được dùng ở process admin — bỏ qua.
                }

                // Báo mọi panel (BaseCrudPanel / AutoRefresher) tự reload từ DB mới.
                DataChangedEvent.publishFullRefresh();
            }
            @Override
            public void onRestoreFailed(String strategyName, File file, Exception error) {
                IncidentLogger.getInstance().critical(IncidentType.RESTORE_FAILED, "BackupManager",
                        "Phuc hoi DB THAT BAI tu file " + file.getName() + ": " + error.getMessage(), error);
            }
        });

        backupManager.startScheduled(1, intervalMinutes);

        healthMonitor = new DbHealthMonitor(
                SIMSBackupTarget::tryConnect,
                failureThreshold,
                () -> maybeEmergencyBackup(storage, staleHours),
                () -> IncidentLogger.getInstance().medium(IncidentType.OTHER, "DbHealthMonitor",
                        "He thong da hoat dong binh thuong tro lai."));
        healthMonitor.start(healthCheckIntervalSec);

        initialized = true;
    }

    /**
     * Neu BACKUP_ENCRYPTION_ENABLED=true (mac dinh), boc moi strategy trong
     * EncryptingBackupStrategy de MOI file backup ghi ra dia deu da duoc ma
     * hoa AES-256-GCM (khoa dan xuat tu BACKUP_ENCRYPTION_PASSPHRASE qua
     * PBKDF2). That bai ngay luc khoi dong neu thieu passphrase - tranh
     * tinh trang backup "tuong nhu an toan" nhung thuc ra dang chay khong
     * ma hoa vi thieu cau hinh.
     *
     * Dat BACKUP_ENCRYPTION_ENABLED=false trong secure-config.enc neu THAT
     * SU chap nhan luu backup dang plain text (khong khuyen nghi).
     */
    private static List<BackupStrategy> buildStrategyChain(AppConfig config,
                                                             SqlServerNativeBackupStrategy nativeStrategy,
                                                             JdbcSqlDumpBackupStrategy jdbcFallback) {
        boolean encryptionEnabled = Boolean.parseBoolean(config.get("BACKUP_ENCRYPTION_ENABLED", "true"));
        if (!encryptionEnabled) {
            AppLogger.getInstance().warn("system",
                    "BACKUP_ENCRYPTION_ENABLED=false - file backup se duoc luu KHONG ma hoa. "
                            + "Khong khuyen nghi cho moi truong production.");
            return List.of(nativeStrategy, jdbcFallback);
        }

        String passphrase = config.get("BACKUP_ENCRYPTION_PASSPHRASE", null);
        if (passphrase == null || passphrase.isBlank()) {
            throw new IllegalStateException(
                    "BACKUP_ENCRYPTION_ENABLED=true (mac dinh) nhung thieu BACKUP_ENCRYPTION_PASSPHRASE "
                            + "trong secure-config.enc.\n"
                            + "Cach khac phuc:\n"
                            + "  1) Them dong BACKUP_ENCRYPTION_PASSPHRASE=<passphrase manh> vao file properties "
                            + "goc, roi ma hoa lai bang ConfigTool ('encrypt').\n"
                            + "  2) Hoac dat BACKUP_ENCRYPTION_ENABLED=false neu CHAP NHAN backup khong ma hoa "
                            + "(khong khuyen nghi).");
        }
        Supplier<String> passphraseSupplier = () -> AppConfig.getInstance().get("BACKUP_ENCRYPTION_PASSPHRASE", null);

        return List.of(
                new EncryptingBackupStrategy(nativeStrategy, passphraseSupplier),
                new EncryptingBackupStrategy(jdbcFallback, passphraseSupplier));
    }

    private static void maybeEmergencyBackup(BackupStorage storage, long staleHours) {
        File latest = storage.getLatestBackup();
        boolean stale = latest == null
                || Instant.ofEpochMilli(latest.lastModified()).isBefore(Instant.now().minus(staleHours, ChronoUnit.HOURS));
        if (!stale) return;
        try {
            backupManager.backupNow(true);
        } catch (BackupException e) {
            IncidentLogger.getInstance().critical(IncidentType.BACKUP_FAILED, "DisasterRecoveryBootstrap",
                    "Backup khan cap cung that bai: " + e.getMessage(), e);
        }
    }

    public static BackupManager getBackupManager() {
        if (!initialized) throw new IllegalStateException("DisasterRecoveryBootstrap.init() chua duoc goi.");
        return backupManager;
    }
    public static FileIncidentSink getIncidentSink() {
        if (!initialized) throw new IllegalStateException("DisasterRecoveryBootstrap.init() chua duoc goi.");
        return incidentSink;
    }
    public static DbHealthMonitor getHealthMonitor() {
        if (!initialized) throw new IllegalStateException("DisasterRecoveryBootstrap.init() chua duoc goi.");
        return healthMonitor;
    }

    /** True neu init() da chay thanh cong - cac man hinh UI nen kiem tra truoc khi goi getBackupManager()/... */
    public static boolean isInitialized() { return initialized; }

    /** Ly do init() that bai lan gan nhat (Main.java goi recordInitFailure() khi bat duoc exception tu init()). */
    public static String getLastInitFailureMessage() { return lastInitFailureMessage; }

    /** Goi tu noi bat exception cua init() (vd Main.java) de UI sau nay hien thi ly do ro rang thay vi crash mo ho. */
    public static void recordInitFailure(String message) { lastInitFailureMessage = message; }
}