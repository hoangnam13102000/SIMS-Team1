// src/main/java/com/service/TwoFactorAuthService.java
package com.service;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.dao.UserDAO;
import com.dao.UserTwoFactorDAO;
import com.model.ActivityLog;
import com.model.TwoFactorMethod;
import com.model.User;
import com.model.UserTwoFactor;
import com.security.AppConfig;
import com.security.CryptoUtil;
import com.utils.PasswordUtils;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trung tam nghiep vu 2FA: enrollment (Email/TOTP) + xac thuc luc dang nhap +
 * backup codes. Kien truc challenge-based mirror PasswordResetService: UI chi
 * cam challengeId, moi trang thai nhay cam (OTP, secret dang cho xac nhan,
 * so lan thu sai) giu noi bo trong service (singleton).
 */
public final class TwoFactorAuthService {

    private static final long CHALLENGE_TTL_MS = 15 * 60 * 1000L;
    private static final long OTP_TTL_MS = 5 * 60 * 1000L;
    private static final long RESEND_COOLDOWN_MS = 60 * 1000L;
    private static final int MAX_VERIFY_ATTEMPTS = 5;
    private static final int BACKUP_CODE_COUNT = 10;

    private final UserTwoFactorDAO twoFactorDAO;
    private final UserDAO userDAO;
    private final TwoFactorMailService mailService;
    private final SecureRandom random = new SecureRandom();

    private final ConcurrentHashMap<String, Challenge> challenges = new ConcurrentHashMap<>();

    private static final class Holder {
        private static final TwoFactorAuthService INSTANCE =
                new TwoFactorAuthService(new UserTwoFactorDAO(), new UserDAO(), new TwoFactorMailService());
    }

    public static TwoFactorAuthService getInstance() {
        return Holder.INSTANCE;
    }

    TwoFactorAuthService(UserTwoFactorDAO twoFactorDAO, UserDAO userDAO, TwoFactorMailService mailService) {
        this.twoFactorDAO = twoFactorDAO;
        this.userDAO = userDAO;
        this.mailService = mailService;
    }

    // ==================== STATUS ====================

    public UserTwoFactor getStatus(int userId) {
        return twoFactorDAO.getStatus(userId);
    }

    // ==================== ENROLLMENT: TOTP ====================

    /** Sinh secret moi (CHUA luu DB) + otpauth URI de ve QR. Goi confirmTotpEnrollment() de kich hoat that su. */
    public TotpEnrollment startTotpEnrollment(User user) {
        String challengeId = UUID.randomUUID().toString();
        String secretBase32 = TotpUtil.generateSecretBase32();
        long now = System.currentTimeMillis();

        Challenge challenge = new Challenge(challengeId, user.getUserId(), Purpose.ENROLL_TOTP, now);
        challenge.totpSecretPending = secretBase32;
        challenges.put(challengeId, challenge);

        String otpAuthUri = TotpUtil.buildOtpAuthUri("SIMS", user.getUsername(), secretBase32);
        return new TotpEnrollment(challengeId, secretBase32, otpAuthUri);
    }

    /** Xac nhan ma 6 so dau tien tu app -> neu dung, luu secret (da ma hoa) va bat 2FA=TOTP. */
    public EnrollResult confirmTotpEnrollment(String challengeId, String code) {
        Challenge challenge = challenges.get(challengeId);
        if (challenge == null || challenge.purpose != Purpose.ENROLL_TOTP) {
            return new EnrollResult(EnrollStatus.NOT_FOUND, null);
        }
        if (isExpired(challenge)) {
            challenges.remove(challengeId);
            return new EnrollResult(EnrollStatus.EXPIRED, null);
        }
        if (!TotpUtil.verifyCode(challenge.totpSecretPending, code)) {
            challenge.attempts++;
            if (challenge.attempts >= MAX_VERIFY_ATTEMPTS) {
                challenges.remove(challengeId);
                return new EnrollResult(EnrollStatus.TOO_MANY_ATTEMPTS, null);
            }
            return new EnrollResult(EnrollStatus.INVALID_CODE, null);
        }

        String encryptedSecret = CryptoUtil.encrypt(challenge.totpSecretPending, AppConfig.getMasterKey());
        boolean saved = twoFactorDAO.enableTotp(challenge.userId, encryptedSecret);
        challenges.remove(challengeId);
        if (!saved) {
            return new EnrollResult(EnrollStatus.SYSTEM_ERROR, null);
        }

        List<String> backupCodes = regenerateBackupCodesInternal(challenge.userId);
        logAudit(challenge.userId, ActivityLog.ACTION_2FA_ENABLED, "Bật xác thực 2 yếu tố bằng ứng dụng Authenticator (TOTP)");
        return new EnrollResult(EnrollStatus.SUCCESS, backupCodes);
    }

    // ==================== ENROLLMENT: EMAIL ====================

    /** Gui OTP xac nhan toi email hien tai cua user de bat 2FA qua Email. */
    public RequestResult startEmailEnrollment(User user) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return new RequestResult(RequestStatus.NO_EMAIL, null, 0);
        }
        return sendOtpChallenge(user.getUserId(), user.getEmail(), Purpose.ENROLL_EMAIL, mailService::sendEnrollOtp);
    }

    public EnrollResult confirmEmailEnrollment(String challengeId, String code) {
        VerifyStatus status = verifyOtpInternal(challengeId, code, Purpose.ENROLL_EMAIL);
        if (status != VerifyStatus.SUCCESS) {
            return new EnrollResult(mapVerifyToEnroll(status), null);
        }
        Challenge challenge = challenges.remove(challengeId);
        boolean saved = twoFactorDAO.enableEmail(challenge.userId);
        if (!saved) {
            return new EnrollResult(EnrollStatus.SYSTEM_ERROR, null);
        }
        List<String> backupCodes = regenerateBackupCodesInternal(challenge.userId);
        logAudit(challenge.userId, ActivityLog.ACTION_2FA_ENABLED, "Bật xác thực 2 yếu tố qua Email");
        return new EnrollResult(EnrollStatus.SUCCESS, backupCodes);
    }

    // ==================== LOGIN-TIME VERIFY ====================

    /**
     * Bat dau buoc 2FA luc dang nhap (goi SAU KHI mat khau da dung).
     * EMAIL: gui OTP ngay va tra ve challengeId. TOTP: tra ve challengeId,
     * KHONG gui gi (nguoi dung tu mo app lay ma).
     */
    public LoginChallengeResult startLoginChallenge(User user, TwoFactorMethod method) {
        if (method == TwoFactorMethod.TOTP) {
            String challengeId = UUID.randomUUID().toString();
            challenges.put(challengeId, new Challenge(challengeId, user.getUserId(), Purpose.LOGIN_TOTP, System.currentTimeMillis()));
            return new LoginChallengeResult(RequestStatus.ACCEPTED, challengeId, 0);
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return new LoginChallengeResult(RequestStatus.NO_EMAIL, null, 0);
        }
        RequestResult r = sendOtpChallenge(user.getUserId(), user.getEmail(), Purpose.LOGIN_EMAIL, mailService::sendLoginOtp);
        return new LoginChallengeResult(r.status, r.challengeId, r.retryAfterSeconds);
    }

    public VerifyResult verifyLoginCode(String challengeId, String code) {
        Challenge challenge = challenges.get(challengeId);
        if (challenge == null) {
            return VerifyResult.NOT_FOUND;
        }
        if (challenge.purpose == Purpose.LOGIN_TOTP) {
            if (isExpired(challenge)) {
                challenges.remove(challengeId);
                return VerifyResult.EXPIRED;
            }
            String encryptedSecret = twoFactorDAO.getEncryptedTotpSecret(challenge.userId);
            if (encryptedSecret == null) {
                challenges.remove(challengeId);
                return VerifyResult.NOT_FOUND;
            }
            String secretBase32 = CryptoUtil.decrypt(encryptedSecret, AppConfig.getMasterKey());
            if (TotpUtil.verifyCode(secretBase32, code)) {
                challenges.remove(challengeId);
                logAudit(challenge.userId, ActivityLog.ACTION_LOGIN_2FA_SUCCESS, "Xác thực 2FA (TOTP) thành công khi đăng nhập");
                return VerifyResult.SUCCESS;
            }
            return registerFailedAttempt(challenge);
        }
        // LOGIN_EMAIL
        VerifyStatus status = verifyOtpInternal(challengeId, code, Purpose.LOGIN_EMAIL);
        if (status == VerifyStatus.SUCCESS) {
            challenges.remove(challengeId);
            logAudit(challenge.userId, ActivityLog.ACTION_LOGIN_2FA_SUCCESS, "Xác thực 2FA (Email OTP) thành công khi đăng nhập");
            return VerifyResult.SUCCESS;
        }
        if (status == VerifyStatus.TOO_MANY_ATTEMPTS) {
            registerLockout(challenge.userId);
        }
        return VerifyResult.valueOf(status.name());
    }

    /** Dung backup code thay cho OTP/TOTP - dung 1 lan. */
    public VerifyResult verifyBackupCode(String challengeId, String rawCode) {
        Challenge challenge = challenges.get(challengeId);
        if (challenge == null) {
            return VerifyResult.NOT_FOUND;
        }
        if (isExpired(challenge)) {
            challenges.remove(challengeId);
            return VerifyResult.EXPIRED;
        }
        String normalized = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        for (UserTwoFactorDAO.BackupCodeRecord record : twoFactorDAO.findUnusedBackupCodes(challenge.userId)) {
            if (PasswordUtils.verify(normalized, record.codeHash)) {
                twoFactorDAO.markBackupCodeUsed(record.backupCodeId);
                challenges.remove(challengeId);
                logAudit(challenge.userId, ActivityLog.ACTION_2FA_BACKUP_CODE_USED,
                        "Đăng nhập bằng mã dự phòng 2FA (còn lại " + (twoFactorDAO.countUnusedBackupCodes(challenge.userId)) + " mã)");
                return VerifyResult.SUCCESS;
            }
        }
        return registerFailedAttempt(challenge);
    }

    public ResendResult resendOtp(String challengeId) {
        Challenge challenge = challenges.get(challengeId);
        if (challenge == null || (challenge.purpose != Purpose.LOGIN_EMAIL && challenge.purpose != Purpose.ENROLL_EMAIL)) {
            return ResendResult.NOT_FOUND;
        }
        long now = System.currentTimeMillis();
        if (isExpired(challenge)) {
            challenges.remove(challengeId);
            return ResendResult.EXPIRED;
        }
        long cooldownRemaining = challenge.lastSentAt + RESEND_COOLDOWN_MS - now;
        if (cooldownRemaining > 0) {
            return ResendResult.COOLDOWN;
        }
        try {
            String otp = generateOtp();
            challenge.otpHash = PasswordUtils.hash(otp);
            challenge.otpExpiresAt = now + OTP_TTL_MS;
            challenge.attempts = 0;
            if (challenge.purpose == Purpose.LOGIN_EMAIL) {
                mailService.sendLoginOtp(challenge.email, otp);
            } else {
                mailService.sendEnrollOtp(challenge.email, otp);
            }
            challenge.lastSentAt = now;
            return ResendResult.SUCCESS;
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.EMAIL_SEND_FAIL, "TwoFactorAuthService.resendOtp", e);
            return ResendResult.MAIL_FAILED;
        }
    }

    public void cancelChallenge(String challengeId) {
        if (challengeId != null) {
            challenges.remove(challengeId);
        }
    }

    // ==================== QUAN LY / BACKUP CODES ====================

    public List<String> regenerateBackupCodes(int userId) {
        List<String> codes = regenerateBackupCodesInternal(userId);
        logAudit(userId, ActivityLog.ACTION_2FA_ENABLED, "Tạo lại bộ mã dự phòng 2FA");
        return codes;
    }

    public int countRemainingBackupCodes(int userId) {
        return twoFactorDAO.countUnusedBackupCodes(userId);
    }

    public boolean disableTwoFactor(int userId, String actingUsername) {
        boolean ok = twoFactorDAO.disable(userId);
        if (ok) {
            logAudit(userId, ActivityLog.ACTION_2FA_DISABLED, "Tắt xác thực 2 yếu tố");
        }
        return ok;
    }

    // ==================== HELPERS ====================

    private RequestResult sendOtpChallenge(int userId, String email, Purpose purpose, MailAction mailAction) {
        long now = System.currentTimeMillis();
        String challengeId = UUID.randomUUID().toString();
        Challenge challenge = new Challenge(challengeId, userId, purpose, now);
        challenge.email = email;
        try {
            String otp = generateOtp();
            challenge.otpHash = PasswordUtils.hash(otp);
            challenge.otpExpiresAt = now + OTP_TTL_MS;
            challenges.put(challengeId, challenge);
            mailAction.send(email, otp);
            challenge.lastSentAt = now;
            return new RequestResult(RequestStatus.ACCEPTED, challengeId, secondsCeil(RESEND_COOLDOWN_MS));
        } catch (Exception e) {
            challenges.remove(challengeId);
            AppLogger.getInstance().error(ErrorCode.EMAIL_SEND_FAIL, "TwoFactorAuthService.sendOtpChallenge", e);
            return new RequestResult(RequestStatus.MAIL_FAILED, null, 0);
        }
    }

    private VerifyStatus verifyOtpInternal(String challengeId, String inputCode, Purpose expectedPurpose) {
        if (inputCode == null || !inputCode.matches("\\d{6}")) {
            return VerifyStatus.INVALID_CODE;
        }
        Challenge challenge = challenges.get(challengeId);
        if (challenge == null || challenge.purpose != expectedPurpose) {
            return VerifyStatus.NOT_FOUND;
        }
        long now = System.currentTimeMillis();
        if (isExpired(challenge)) {
            challenges.remove(challengeId);
            return VerifyStatus.EXPIRED;
        }
        if (now >= challenge.otpExpiresAt) {
            return VerifyStatus.EXPIRED;
        }
        if (challenge.otpHash == null || !PasswordUtils.verify(inputCode, challenge.otpHash)) {
            challenge.attempts++;
            if (challenge.attempts >= MAX_VERIFY_ATTEMPTS) {
                challenges.remove(challengeId);
                return VerifyStatus.TOO_MANY_ATTEMPTS;
            }
            return VerifyStatus.INVALID_CODE;
        }
        return VerifyStatus.SUCCESS;
    }

    private VerifyResult registerFailedAttempt(Challenge challenge) {
        challenge.attempts++;
        if (challenge.attempts >= MAX_VERIFY_ATTEMPTS) {
            challenges.remove(challenge.id);
            registerLockout(challenge.userId);
            return VerifyResult.TOO_MANY_ATTEMPTS;
        }
        return VerifyResult.INVALID_CODE;
    }

    /** Qua so lan thu sai 2FA -> coi nhu dang nhap that bai, dung lai co che khoa cua UserDAO (R5). */
    private void registerLockout(int userId) {
        userDAO.registerFailedTwoFactorAttempt(userId);
        logAudit(userId, ActivityLog.ACTION_LOGIN_2FA_FAILED, "Nhập sai mã 2FA quá số lần cho phép");
    }

    private List<String> regenerateBackupCodesInternal(int userId) {
        List<String> plainCodes = new ArrayList<>(BACKUP_CODE_COUNT);
        List<String> hashedCodes = new ArrayList<>(BACKUP_CODE_COUNT);
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            String code = generateBackupCode();
            plainCodes.add(code);
            hashedCodes.add(PasswordUtils.hash(code));
        }
        twoFactorDAO.replaceBackupCodes(userId, hashedCodes);
        return plainCodes;
    }

    private String generateBackupCode() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // bo O/0, I/1 de tranh nham lan
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i == 4) {
                sb.append('-');
            }
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private String generateOtp() {
        return String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
    }

    private boolean isExpired(Challenge challenge) {
        return System.currentTimeMillis() >= challenge.createdAt + CHALLENGE_TTL_MS;
    }

    private int secondsCeil(long ms) {
        return (int) Math.max(1, (ms + 999L) / 1000L);
    }

    private void logAudit(int userId, String action, String description) {
        User user = userDAO.findById(userId);
        String username = user != null ? user.getUsername() : ("userId:" + userId);
        AppLogger.getInstance().log(username, action, ActivityLog.ENTITY_USER, description);
    }

    private EnrollStatus mapVerifyToEnroll(VerifyStatus status) {
        switch (status) {
            case EXPIRED: return EnrollStatus.EXPIRED;
            case TOO_MANY_ATTEMPTS: return EnrollStatus.TOO_MANY_ATTEMPTS;
            case NOT_FOUND: return EnrollStatus.NOT_FOUND;
            default: return EnrollStatus.INVALID_CODE;
        }
    }

    @FunctionalInterface
    private interface MailAction {
        void send(String email, String otp) throws Exception;
    }

    private enum Purpose { ENROLL_TOTP, ENROLL_EMAIL, LOGIN_EMAIL, LOGIN_TOTP }

    private static final class Challenge {
        final String id;
        final int userId;
        final Purpose purpose;
        final long createdAt;

        String email;
        String otpHash;
        long otpExpiresAt;
        long lastSentAt;
        int attempts;
        String totpSecretPending;

        Challenge(String id, int userId, Purpose purpose, long createdAt) {
            this.id = id;
            this.userId = userId;
            this.purpose = purpose;
            this.createdAt = createdAt;
        }
    }

    // ==================== DTO / ENUM CONG KHAI ====================

    public enum RequestStatus { ACCEPTED, NO_EMAIL, MAIL_FAILED }
    public enum VerifyStatus { SUCCESS, INVALID_CODE, EXPIRED, TOO_MANY_ATTEMPTS, NOT_FOUND }
    public enum ResendResult { SUCCESS, COOLDOWN, EXPIRED, MAIL_FAILED, NOT_FOUND }
    public enum VerifyResult { SUCCESS, INVALID_CODE, EXPIRED, TOO_MANY_ATTEMPTS, NOT_FOUND }
    public enum EnrollStatus { SUCCESS, INVALID_CODE, EXPIRED, TOO_MANY_ATTEMPTS, NOT_FOUND, SYSTEM_ERROR }

    public static final class RequestResult {
        public final RequestStatus status;
        public final String challengeId;
        public final int retryAfterSeconds;
        RequestResult(RequestStatus status, String challengeId, int retryAfterSeconds) {
            this.status = status; this.challengeId = challengeId; this.retryAfterSeconds = retryAfterSeconds;
        }
    }

    public static final class LoginChallengeResult {
        public final RequestStatus status;
        public final String challengeId;
        public final int retryAfterSeconds;
        LoginChallengeResult(RequestStatus status, String challengeId, int retryAfterSeconds) {
            this.status = status; this.challengeId = challengeId; this.retryAfterSeconds = retryAfterSeconds;
        }
    }

    public static final class TotpEnrollment {
        public final String challengeId;
        public final String secretBase32;
        public final String otpAuthUri;
        TotpEnrollment(String challengeId, String secretBase32, String otpAuthUri) {
            this.challengeId = challengeId; this.secretBase32 = secretBase32; this.otpAuthUri = otpAuthUri;
        }
    }

    public static final class EnrollResult {
        public final EnrollStatus status;
        public final List<String> backupCodes;
        EnrollResult(EnrollStatus status, List<String> backupCodes) {
            this.status = status; this.backupCodes = backupCodes;
        }
    }
}