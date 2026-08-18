package com.service;

import com.core.log.ActivityLogHelper;
import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.dao.ShiftDAO;
import com.event.AppEventBus;
import com.event.DataChangedEvent;
import com.model.ActivityLog;
import com.model.Shift;
import com.model.ShiftCashSummary;
import com.model.ShiftCashTransaction;
import com.model.User;
import com.model.permission.AppPermission;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

public class ShiftService {
	
	/**
	 * Gia tri lon nhat cua DECIMAL(18,0).
	 */
	private static final BigDecimal MAX_MONEY =
	        new BigDecimal("999999999999999999");

	private final ShiftDAO shiftDAO;
	private final AuthService authService;

	/**
	 * Constructor dung trong ung dung that.
	 */
	public ShiftService() {
	    this(
	        new ShiftDAO(),
	        AuthService.getInstance()
	    );
	}

	/**
	 * Constructor dung cho kiem thu.
	 *
	 * Khong dat public vi chi cac class cung package
	 * hoac test phu hop moi can su dung.
	 */
	ShiftService(
	        ShiftDAO shiftDAO,
	        AuthService authService
	) {
	    this.shiftDAO = shiftDAO;
	    this.authService = authService;
	}
	
	/**
	 * Lay ca OPEN cua nguoi dang dang nhap.
	 */
	public Shift getMyOpenShift() {
	    User user = authService.getCurrentUser();

	    if (user == null) {
	        return null;
	    }

	    return shiftDAO.findOpenShiftByUserId(
	            user.getUserId()
	    );
	}
	
	/**
	 * Lay lich su ca theo pham vi quyen.
	 *
	 * SHIFT_VIEW_ALL:
	 *     xem tat ca nhan vien.
	 *
	 * Khong co SHIFT_VIEW_ALL:
	 *     chi xem ca cua chinh minh.
	 */
	public List<Shift> getVisibleHistory(int limit) {
	    User user = authService.getCurrentUser();

	    if (user == null) {
	        return Collections.emptyList();
	    }

	    Integer ownerFilter;

	    if (
	        authService.can(
	                AppPermission.SHIFT_VIEW_ALL
	        )
	    ) {
	        ownerFilter = null;
	    } else {
	        ownerFilter = user.getUserId();
	    }

	    return shiftDAO.findRecent(
	            ownerFilter,
	            limit
	    );
	}
	
	/**
	 * Lấy tất cả ca đang OPEN — yêu cầu quyền SHIFT_MONITOR.
	 */
	public List<Shift> getAllOpenShifts() {
	    if (!authService.can(AppPermission.SHIFT_MONITOR)) {
	        return Collections.emptyList();
	    }
	    return shiftDAO.findAllOpenShifts();
	}

	/**
	 * Lấy danh sách ca cho trang giám sát (lọc theo ngày bắt đầu ca).
	 * Yêu cầu quyền SHIFT_MONITOR (Admin / Quản lý bán hàng).
	 *
	 * @param from     từ ngày (null = không giới hạn)
	 * @param to       đến ngày (null = không giới hạn)
	 * @param openOnly true = chỉ ca đang mở
	 */
	public List<Shift> getShiftsForMonitor(
	        java.time.LocalDate from,
	        java.time.LocalDate to,
	        boolean openOnly
	) {
	    if (!authService.can(AppPermission.SHIFT_MONITOR)) {
	        return Collections.emptyList();
	    }
	    return shiftDAO.findShiftsForMonitor(from, to, openOnly);
	}

	/**
	 * Lay danh sach thu/chi cua mot ca neu nguoi dung
	 * co quyen xem ca do.
	 */
	public List<ShiftCashTransaction> getTransactions(
	        int shiftId
	) {
	    User user = authService.getCurrentUser();

	    if (user == null) {
	        return Collections.emptyList();
	    }

	    Shift shift = shiftDAO.findById(shiftId);

	    if (shift == null) {
	        return Collections.emptyList();
	    }

	    boolean ownShift =
	            shift.getUserId() == user.getUserId();

	    boolean canViewAll =
	            authService.can(
	                    AppPermission.SHIFT_VIEW_ALL
	            );

	    if (!ownShift && !canViewAll) {
	        return Collections.emptyList();
	    }

	    return shiftDAO.findTransactions(shiftId);
	}
	
	/**
	 * Mo ca cho nguoi dang dang nhap.
	 */
	public OperationResult<Shift> openMyShift(
	        BigDecimal openingCash,
	        String openingNote
	) {
	    User user = authService.getCurrentUser();

	    String permissionError =
	            validateOperator(user);

	    if (permissionError != null) {
	        return OperationResult.failure(
	                permissionError
	        );
	    }

	    String moneyError =
	            validateMoney(
	                    openingCash,
	                    true
	            );

	    if (moneyError != null) {
	        return OperationResult.failure(
	                "Tien dau ca: " + moneyError
	        );
	    }

	    if (
	        openingNote != null
	        && openingNote.trim().length() > 500
	    ) {
	        return OperationResult.failure(
	                "Ghi chu dau ca khong duoc "
	              + "vuot qua 500 ky tu."
	        );
	    }

	    Shift currentOpenShift =
	            shiftDAO.findOpenShiftByUserId(
	                    user.getUserId()
	            );

	    if (currentOpenShift != null) {
	        return OperationResult.failure(
	                "Ban dang co mot ca mo. "
	              + "Hay dong ca hien tai truoc."
	        );
	    }

	    try {
	        Shift shift = shiftDAO.openShift(
	                user.getUserId(),
	                openingCash,
	                openingNote
	        );

	        ActivityLogHelper.record(
	                "ca bán hàng",
	                ActivityLog.ACTION_SHIFT_OPEN,
	                "Mo ca #"
	                    + shift.getShiftId()
	                    + " voi tien dau ca "
	                    + openingCash.toPlainString(),
	                null,
	                shift
	        );

	        publishChanged();

	        return OperationResult.success(
	                "Mo ca thanh cong.",
	                shift
	        );

	    } catch (SQLException e) {
	        logMutationError(
	                "ShiftService.openMyShift",
	                e
	        );

	        return OperationResult.failure(
	                friendlySqlMessage(
	                        e,
	                        "Khong the mo ca. "
	                      + "Vui long thu lai."
	                )
	        );
	    }
	}
	
	/**
	 * Ghi mot khoan thu hoac chi tien mat
	 * vao ca cua nguoi dang dang nhap.
	 */
	public OperationResult<ShiftCashTransaction>
	        addCashMovement(
	                String type,
	                BigDecimal amount,
	                String reason
	        ) {

	    User user = authService.getCurrentUser();

	    String permissionError =
	            validateOperator(user);

	    if (permissionError != null) {
	        return OperationResult.failure(
	                permissionError
	        );
	    }

	    boolean validType =
	            ShiftCashTransaction.CASH_IN.equals(type)
	            || ShiftCashTransaction.CASH_OUT.equals(type);

	    if (!validType) {
	        return OperationResult.failure(
	                "Loai giao dich quy khong hop le."
	        );
	    }

	    String moneyError =
	            validateMoney(
	                    amount,
	                    false
	            );

	    if (moneyError != null) {
	        return OperationResult.failure(
	                "So tien: " + moneyError
	        );
	    }

	    if (reason == null || reason.isBlank()) {
	        return OperationResult.failure(
	                "Phai nhap ly do thu/chi "
	              + "de co the doi soat."
	        );
	    }

	    String normalizedReason = reason.trim();

	    if (normalizedReason.length() > 255) {
	        return OperationResult.failure(
	                "Ly do khong duoc vuot qua 255 ky tu."
	        );
	    }

	    Shift openShift =
	            shiftDAO.findOpenShiftByUserId(
	                    user.getUserId()
	            );

	    if (openShift == null) {
	        return OperationResult.failure(
	                "Ban chua mo ca ban hang."
	        );
	    }

	    try {
	        ShiftCashTransaction transaction =
	                shiftDAO.addCashTransaction(
	                        openShift.getShiftId(),
	                        user.getUserId(),
	                        type,
	                        amount,
	                        normalizedReason
	                );

	        String action;

	        if (ShiftCashTransaction.CASH_IN.equals(type)) {
	            action = ActivityLog.ACTION_CASH_IN;
	        } else {
	            action = ActivityLog.ACTION_CASH_OUT;
	        }

	        String actionLabel =
	                transaction.isCashIn()
	                ? "Thu tien"
	                : "Chi tien";

	        ActivityLogHelper.record(
	                "giao dịch quỹ",
	                action,
	                actionLabel
	                    + " trong ca #"
	                    + openShift.getShiftId()
	                    + ": "
	                    + amount.toPlainString()
	                    + " - "
	                    + normalizedReason,
	                null,
	                transaction
	        );

	        publishChanged();

	        return OperationResult.success(
	                "Da ghi nhan giao dich quy.",
	                transaction
	        );

	    } catch (SQLException e) {
	        logMutationError(
	                "ShiftService.addCashMovement",
	                e
	        );

	        return OperationResult.failure(
	                friendlySqlMessage(
	                        e,
	                        "Khong the ghi nhan thu/chi."
	                )
	        );
	    }
	}
	
	/**
	 * Tinh tien he thong truoc khi mo hop thoai dong ca.
	 */
	public OperationResult<ShiftCashSummary>
	        previewClose() {

	    User user = authService.getCurrentUser();

	    String permissionError =
	            validateOperator(user);

	    if (permissionError != null) {
	        return OperationResult.failure(
	                permissionError
	        );
	    }

	    Shift openShift =
	            shiftDAO.findOpenShiftByUserId(
	                    user.getUserId()
	            );

	    if (openShift == null) {
	        return OperationResult.failure(
	                "Ban chua co ca dang mo."
	        );
	    }

	    try {
	        ShiftCashSummary summary =
	                shiftDAO.calculateCashSummary(
	                        openShift.getShiftId()
	                );

	        return OperationResult.success(
	                "Da tinh so tien he thong.",
	                summary
	        );

	    } catch (SQLException e) {
	        logMutationError(
	                "ShiftService.previewClose",
	                e
	        );

	        return OperationResult.failure(
	                "Khong the tinh so tien he thong."
	        );
	    }
	}
	
	/**
	 * Dong ca cua nguoi dang dang nhap.
	 */
	public OperationResult<Shift> closeMyShift(
	        BigDecimal countedCash,
	        String closingNote
	) {
	    User user = authService.getCurrentUser();

	    String permissionError =
	            validateOperator(user);

	    if (permissionError != null) {
	        return OperationResult.failure(
	                permissionError
	        );
	    }

	    String moneyError =
	            validateMoney(
	                    countedCash,
	                    true
	            );

	    if (moneyError != null) {
	        return OperationResult.failure(
	                "Tien kiem thuc te: " + moneyError
	        );
	    }

	    if (
	        closingNote != null
	        && closingNote.trim().length() > 500
	    ) {
	        return OperationResult.failure(
	                "Ghi chu dong ca khong duoc "
	              + "vuot qua 500 ky tu."
	        );
	    }

	    Shift openShift =
	            shiftDAO.findOpenShiftByUserId(
	                    user.getUserId()
	            );

	    if (openShift == null) {
	        return OperationResult.failure(
	                "Ban chua co ca dang mo."
	        );
	    }

	    try {
	        ShiftCashSummary preview =
	                shiftDAO.calculateCashSummary(
	                        openShift.getShiftId()
	                );

	        BigDecimal difference =
	                preview.differenceFrom(
	                        countedCash
	                );

	        if (
	            difference.signum() != 0
	            && (
	                closingNote == null
	                || closingNote.isBlank()
	            )
	        ) {
	            return OperationResult.failure(
	                    "Tien thuc te dang chenh lech. "
	                  + "Ban phai nhap giai trinh "
	                  + "truoc khi dong ca."
	            );
	        }

	        Shift closedShift =
	                shiftDAO.closeShift(
	                        openShift.getShiftId(),
	                        user.getUserId(),
	                        countedCash,
	                        closingNote
	                );

	        ActivityLogHelper.record(
	                "ca bán hàng",
	                ActivityLog.ACTION_SHIFT_CLOSE,
	                "Dong ca #"
	                    + closedShift.getShiftId()
	                    + ", chenh lech "
	                    + closedShift
	                        .getCashDifference()
	                        .toPlainString(),
	                openShift,
	                closedShift
	        );

	        publishChanged();

	        return OperationResult.success(
	                "Dong ca va doi soat quy thanh cong.",
	                closedShift
	        );

	    } catch (SQLException e) {
	        logMutationError(
	                "ShiftService.closeMyShift",
	                e
	        );

	        return OperationResult.failure(
	                friendlySqlMessage(
	                        e,
	                        "Khong the dong ca. "
	                      + "Vui long thu lai."
	                )
	        );
	    }
	}
	
	private String validateOperator(User user) {
	    if (user == null) {
	        return "Phien dang nhap da het han.";
	    }

	    if (
	        !authService.can(
	                AppPermission.SHIFT_OPERATE
	        )
	    ) {
	        return "Tai khoan khong co quyen "
	             + "thao tac ca ban hang.";
	    }

	    return null;
	}
	
	private String validateMoney(
	        BigDecimal amount,
	        boolean allowZero
	) {
	    if (amount == null) {
	        return "khong duoc de trong.";
	    }

	    if (amount.signum() < 0) {
	        return "phai lon hon hoac bang 0.";
	    }

	    if (
	        !allowZero
	        && amount.signum() == 0
	    ) {
	        return "phai lon hon 0.";
	    }

	    /*
	     * VND trong project khong su dung phan thap phan.
	     */
	    if (
	        amount
	            .remainder(BigDecimal.ONE)
	            .signum() != 0
	    ) {
	        return "phai la so nguyen VND, "
	             + "khong nhap phan thap phan.";
	    }

	    if (amount.compareTo(MAX_MONEY) > 0) {
	        return "vuot qua gioi han cho phep.";
	    }

	    return null;
	}
	
	private String friendlySqlMessage(
	        SQLException e,
	        String fallback
	) {
	    /*
	     * Loi nghiep vu do DAO chu dong nem.
	     */
	    if (
	        "45000".equals(e.getSQLState())
	        && e.getMessage() != null
	    ) {
	        return e.getMessage() + ".";
	    }

	    /*
	     * MySQL error 1062:
	     * trung unique key, vi du mo hai ca cung user.
	     */
	    if (e.getErrorCode() == 1062) {
	        return "Ban da co mot ca dang mo.";
	    }

	    return fallback;
	}
	
	private void logMutationError(
	        String context,
	        SQLException e
	) {
	    AppLogger.getInstance().error(
	            ErrorCode.DB_UPDATE_FAIL,
	            context,
	            e
	    );
	}
	
	private void publishChanged() {
	    AppEventBus.getInstance().publish(
	            new DataChangedEvent(
	                    DataChangedEvent.SHIFT
	            )
	    );
	}
	
	/**
	 * Ket qua nghiep vu tra ve cho giao dien.
	 *
	 * @param <T> kieu du lieu khi thanh cong
	 */
	public static final class OperationResult<T> {

	    private final boolean success;
	    private final String message;
	    private final T data;

	    private OperationResult(
	            boolean success,
	            String message,
	            T data
	    ) {
	        this.success = success;
	        this.message = message;
	        this.data = data;
	    }

	    public static <T> OperationResult<T> success(
	            String message,
	            T data
	    ) {
	        return new OperationResult<>(
	                true,
	                message,
	                data
	        );
	    }

	    public static <T> OperationResult<T> failure(
	            String message
	    ) {
	        return new OperationResult<>(
	                false,
	                message,
	                null
	        );
	    }

	    public boolean isSuccess() {
	        return success;
	    }

	    public String getMessage() {
	        return message;
	    }

	    public T getData() {
	        return data;
	    }
	}

}
