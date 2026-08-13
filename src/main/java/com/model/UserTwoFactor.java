package com.model;

import java.time.LocalDateTime;

/**
 * Trang thai 2FA cua 1 user. KHONG bao gio mang TotpSecretEnc ra khoi DAO -
 * giong nguyen tac PasswordHash khong duoc SELECT trong cac truy van thuong.
 */
public class UserTwoFactor {

    private int userId;
    private TwoFactorMethod method = TwoFactorMethod.NONE;
    private boolean enabled;
    private LocalDateTime enrolledAt;

    public UserTwoFactor() {
    }

    public UserTwoFactor(int userId, TwoFactorMethod method, boolean enabled, LocalDateTime enrolledAt) {
        this.userId = userId;
        this.method = method;
        this.enabled = enabled;
        this.enrolledAt = enrolledAt;
    }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public TwoFactorMethod getMethod() { return method; }
    public void setMethod(TwoFactorMethod method) { this.method = method; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public LocalDateTime getEnrolledAt() { return enrolledAt; }
    public void setEnrolledAt(LocalDateTime enrolledAt) { this.enrolledAt = enrolledAt; }

    /** true neu user CHUA tung thiet lap 2FA (can bi ep enroll ngay lan dang nhap dau). */
    public boolean needsEnrollment() {
        return !enabled || method == TwoFactorMethod.NONE;
    }
}