package com.service;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.dao.UserDAO;
import com.model.ActivityLog;
import com.model.User;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * Quan ly toan bo authorization cho password recovery.
 *
 * UI chi nhan challengeId; userId, OTP hash va trang thai verified duoc giu
 * noi bo. Service la singleton de cooldown/rate limit khong bi reset moi lan
 * mo dialog.
 */
public final class PasswordResetService {

    static final long OTP_TTL_MS = 5 * 60 * 1000L;
    static final long VERIFIED_TTL_MS = 10 * 60 * 1000L;
    static final long CHALLENGE_TTL_MS = 15 * 60 * 1000L;
    static final long RESEND_COOLDOWN_MS = 60 * 1000L;
    static final long RATE_WINDOW_MS = 15 * 60 * 1000L;
    static final int MAX_SENDS_PER_WINDOW = 3;
    static final int MAX_VERIFY_ATTEMPTS = 5;

    private static final int OTP_BOUND = 1_000_000;
    private static final int BCRYPT_MAX_BYTES = 72;
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$");

    private final ConcurrentHashMap<String, Challenge> challenges = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RateBucket> sendRates = new ConcurrentHashMap<>();

    private final AccountGateway accountGateway;
    private final OtpDelivery otpDelivery;
    private final LongSupplier clock;
    private final SecureRandom random;

    private static final class Holder {
        private static final PasswordResetService INSTANCE = createDefault();
    }

    public static PasswordResetService getInstance() {
        return Holder.INSTANCE;
    }

    private static PasswordResetService createDefault() {
        UserDAO userDAO = new UserDAO();
        PasswordResetMailService mailService = new PasswordResetMailService();
        AccountGateway gateway = new AccountGateway() {
            @Override
            public User findForPasswordReset(String username, String email) throws Exception {
                return userDAO.findForPasswordReset(username, email);
            }

            @Override
            public UserDAO.PasswordResetUpdateResult resetPassword(
                    int userId, String newRawPassword) {
                return userDAO.resetPasswordFromRecovery(userId, newRawPassword);
            }
        };
        return new PasswordResetService(
                gateway,
                mailService::sendResetOtp,
                System::currentTimeMillis,
                new SecureRandom()
        );
    }

    PasswordResetService(AccountGateway accountGateway,
                         OtpDelivery otpDelivery,
                         LongSupplier clock,
                         SecureRandom random) {
        this.accountGateway = accountGateway;
        this.otpDelivery = otpDelivery;
        this.clock = clock;
        this.random = random;
    }

    public RequestResult requestOtp(String usernameInput, String emailInput) {
        String username = normalizeUsername(usernameInput);
        String email = normalizeEmail(emailInput);
        if (!isValidIdentityInput(username, email)) {
            return new RequestResult(RequestStatus.INVALID_INPUT, null, null, 0);
        }

        long now = clock.getAsLong();
        cleanupExpired(now);
        RateDecision rate = acquireSendPermit(rateKey(username, email), now);
        if (!rate.allowed) {
            return new RequestResult(
                    RequestStatus.RATE_LIMITED, null, null, rate.retryAfterSeconds);
        }

        User account;
        try {
            account = accountGateway.findForPasswordReset(username, email);
        } catch (Exception e) {
            AppLogger.getInstance().error(
                    ErrorCode.AUTH_PASSWORD_RESET_FAIL,
                    "PasswordResetService.requestOtp lookup",
                    e
            );
            return new RequestResult(RequestStatus.SYSTEM_ERROR, null, null, 0);
        }

        String challengeId = UUID.randomUUID().toString();
        boolean decoy = account == null;
        Challenge challenge = new Challenge(
                challengeId,
                decoy ? -1 : account.getUserId(),
                username,
                email,
                decoy,
                now
        );
        challenges.put(challengeId, challenge);

        if (decoy) {
            synchronized (challenge) {
                challenge.lastSentAt = now;
                challenge.sendCount = 1;
            }
            return acceptedRequest(challengeId, email);
        }

        String otp = generateOtp();
        synchronized (challenge) {
            challenge.sending = true;
            challenge.otpHash = hashOtp(challengeId, otp);
            challenge.otpExpiresAt = now + OTP_TTL_MS;
        }

        try {
            otpDelivery.send(email, otp);
        } catch (Exception e) {
            invalidateAndRemove(challenge);
            AppLogger.getInstance().error(
                    ErrorCode.EMAIL_SEND_FAIL,
                    "PasswordResetService.requestOtp delivery",
                    e
            );
            return new RequestResult(RequestStatus.MAIL_FAILED, null, null, 0);
        }

        synchronized (challenge) {
            if (challenge.cancelled || challenges.get(challengeId) != challenge) {
                clearOtpHash(challenge);
                return new RequestResult(RequestStatus.SYSTEM_ERROR, null, null, 0);
            }
            challenge.sending = false;
            challenge.lastSentAt = now;
            challenge.sendCount = 1;
        }
        return acceptedRequest(challengeId, email);
    }

    public VerifyResult verifyOtp(String challengeId, String inputCode) {
        if (challengeId == null || inputCode == null || !inputCode.matches("\\d{6}")) {
            return new VerifyResult(VerifyStatus.INVALID_CODE, MAX_VERIFY_ATTEMPTS);
        }

        long now = clock.getAsLong();
        Challenge challenge = challenges.get(challengeId);
        cleanupExpired(now, challengeId);
        if (challenge == null) {
            return new VerifyResult(VerifyStatus.NOT_FOUND, 0);
        }

        synchronized (challenge) {
            if (challenge.cancelled || challenges.get(challengeId) != challenge) {
                return new VerifyResult(VerifyStatus.NOT_FOUND, 0);
            }
            if (now >= challenge.challengeExpiresAt) {
                invalidateAndRemove(challenge);
                return new VerifyResult(VerifyStatus.EXPIRED, 0);
            }
            if (challenge.verified) {
                return new VerifyResult(VerifyStatus.ALREADY_VERIFIED, 0);
            }
            if (challenge.sending || challenge.otpHash == null) {
                return recordInvalidAttempt(challenge);
            }
            if (now >= challenge.otpExpiresAt) {
                return new VerifyResult(
                        VerifyStatus.EXPIRED,
                        Math.max(0, MAX_VERIFY_ATTEMPTS - challenge.verifyAttempts)
                );
            }

            byte[] inputHash = hashOtp(challengeId, inputCode);
            boolean matches = MessageDigest.isEqual(challenge.otpHash, inputHash);
            Arrays.fill(inputHash, (byte) 0);

            if (!matches || challenge.decoy) {
                return recordInvalidAttempt(challenge);
            }

            challenge.verified = true;
            challenge.verifiedUntil = now + VERIFIED_TTL_MS;
            challenge.verifyAttempts = 0;
            clearOtpHash(challenge);
            return new VerifyResult(VerifyStatus.SUCCESS, MAX_VERIFY_ATTEMPTS);
        }
    }

    public ResendResult resendOtp(String challengeId) {
        if (challengeId == null) {
            return new ResendResult(ResendStatus.NOT_FOUND, 0);
        }

        long now = clock.getAsLong();
        Challenge challenge = challenges.get(challengeId);
        cleanupExpired(now, challengeId);
        if (challenge == null) {
            return new ResendResult(ResendStatus.NOT_FOUND, 0);
        }

        String otp = null;
        synchronized (challenge) {
            if (challenge.cancelled || challenges.get(challengeId) != challenge) {
                return new ResendResult(ResendStatus.NOT_FOUND, 0);
            }
            if (now >= challenge.challengeExpiresAt) {
                invalidateAndRemove(challenge);
                return new ResendResult(ResendStatus.EXPIRED, 0);
            }
            if (challenge.verified) {
                return new ResendResult(ResendStatus.ALREADY_VERIFIED, 0);
            }
            if (challenge.sending || challenge.resetting) {
                return new ResendResult(ResendStatus.IN_PROGRESS, 1);
            }

            long cooldownRemaining = challenge.lastSentAt + RESEND_COOLDOWN_MS - now;
            if (cooldownRemaining > 0) {
                return new ResendResult(
                        ResendStatus.COOLDOWN, toSecondsCeiling(cooldownRemaining));
            }

            RateDecision rate = acquireSendPermit(
                    rateKey(challenge.username, challenge.email), now);
            if (!rate.allowed) {
                return new ResendResult(
                        ResendStatus.RATE_LIMITED, rate.retryAfterSeconds);
            }

            challenge.sending = true;
            challenge.verifyAttempts = 0;
            clearOtpHash(challenge);
            if (!challenge.decoy) {
                otp = generateOtp();
                challenge.otpHash = hashOtp(challenge.id, otp);
                challenge.otpExpiresAt = now + OTP_TTL_MS;
            }
        }

        if (!challenge.decoy) {
            try {
                otpDelivery.send(challenge.email, otp);
            } catch (Exception e) {
                synchronized (challenge) {
                    clearOtpHash(challenge);
                    challenge.sending = false;
                }
                AppLogger.getInstance().error(
                        ErrorCode.EMAIL_SEND_FAIL,
                        "PasswordResetService.resendOtp delivery",
                        e
                );
                return new ResendResult(ResendStatus.MAIL_FAILED, 0);
            }
        }

        synchronized (challenge) {
            if (challenge.cancelled || challenges.get(challengeId) != challenge) {
                clearOtpHash(challenge);
                return new ResendResult(ResendStatus.NOT_FOUND, 0);
            }
            challenge.sending = false;
            challenge.lastSentAt = now;
            challenge.sendCount++;
            if (challenge.decoy) {
                challenge.otpExpiresAt = now + OTP_TTL_MS;
            }
        }
        return new ResendResult(
                ResendStatus.SUCCESS, toSecondsCeiling(RESEND_COOLDOWN_MS));
    }

    public ResetResult resetPassword(String challengeId, char[] newPassword) {
        if (challengeId == null) {
            clearPassword(newPassword);
            return new ResetResult(ResetStatus.NOT_FOUND, PasswordValidationStatus.VALID);
        }
        PasswordValidationStatus validation = validatePassword(newPassword);
        if (validation != PasswordValidationStatus.VALID) {
            clearPassword(newPassword);
            return new ResetResult(ResetStatus.INVALID_PASSWORD, validation);
        }

        String password = new String(newPassword);
        clearPassword(newPassword);

        long now = clock.getAsLong();
        Challenge challenge = challenges.get(challengeId);
        cleanupExpired(now, challengeId);
        if (challenge == null) {
            return new ResetResult(ResetStatus.NOT_FOUND, PasswordValidationStatus.VALID);
        }

        synchronized (challenge) {
            if (challenge.cancelled || challenges.get(challengeId) != challenge) {
                return new ResetResult(ResetStatus.NOT_FOUND, PasswordValidationStatus.VALID);
            }
            if (!challenge.verified) {
                return new ResetResult(
                        ResetStatus.NOT_VERIFIED, PasswordValidationStatus.VALID);
            }
            if (now >= challenge.verifiedUntil || now >= challenge.challengeExpiresAt) {
                invalidateAndRemove(challenge);
                return new ResetResult(
                        ResetStatus.SESSION_EXPIRED, PasswordValidationStatus.VALID);
            }
            if (challenge.resetting) {
                return new ResetResult(ResetStatus.IN_PROGRESS, PasswordValidationStatus.VALID);
            }
            challenge.resetting = true;
        }

        UserDAO.PasswordResetUpdateResult updateResult;
        try {
            updateResult = accountGateway.resetPassword(challenge.userId, password);
        } catch (Exception e) {
            AppLogger.getInstance().error(
                    ErrorCode.AUTH_PASSWORD_RESET_FAIL,
                    "PasswordResetService.resetPassword update",
                    e
            );
            updateResult = UserDAO.PasswordResetUpdateResult.UPDATE_FAILED;
        }

        if (updateResult == UserDAO.PasswordResetUpdateResult.SUCCESS) {
            challenges.remove(challengeId, challenge);
            synchronized (challenge) {
                challenge.cancelled = true;
                challenge.resetting = false;
                clearOtpHash(challenge);
            }
            AppLogger.getInstance().log(
                    challenge.username,
                    ActivityLog.ACTION_PASSWORD_RESET,
                    ActivityLog.ENTITY_USER,
                    "Người dùng đã đặt lại mật khẩu bằng OTP"
            );
            return new ResetResult(ResetStatus.SUCCESS, PasswordValidationStatus.VALID);
        }

        synchronized (challenge) {
            challenge.resetting = false;
        }
        if (updateResult == UserDAO.PasswordResetUpdateResult.SAME_AS_OLD_PASSWORD) {
            return new ResetResult(
                    ResetStatus.SAME_AS_OLD_PASSWORD, PasswordValidationStatus.VALID);
        }
        if (updateResult == UserDAO.PasswordResetUpdateResult.ACCOUNT_UNAVAILABLE) {
            invalidateAndRemove(challenge);
            return new ResetResult(
                    ResetStatus.ACCOUNT_UNAVAILABLE, PasswordValidationStatus.VALID);
        }
        return new ResetResult(ResetStatus.UPDATE_FAILED, PasswordValidationStatus.VALID);
    }

    public void cancelChallenge(String challengeId) {
        if (challengeId == null) {
            return;
        }
        Challenge challenge = challenges.remove(challengeId);
        if (challenge != null) {
            synchronized (challenge) {
                challenge.cancelled = true;
                clearOtpHash(challenge);
            }
        }
    }

    public static PasswordValidationStatus validatePassword(char[] password) {
        if (password == null || password.length == 0) {
            return PasswordValidationStatus.REQUIRED;
        }
        if (password.length < 8 || password.length > 72) {
            return PasswordValidationStatus.LENGTH;
        }
        if (Character.isWhitespace(password[0])
                || Character.isWhitespace(password[password.length - 1])) {
            return PasswordValidationStatus.WHITESPACE;
        }

        boolean hasLetter = false;
        boolean hasDigit = false;
        boolean hasNonWhitespace = false;
        for (char c : password) {
            hasLetter |= Character.isLetter(c);
            hasDigit |= Character.isDigit(c);
            hasNonWhitespace |= !Character.isWhitespace(c);
        }
        if (!hasNonWhitespace) {
            return PasswordValidationStatus.WHITESPACE;
        }
        if (!hasLetter) {
            return PasswordValidationStatus.LETTER;
        }
        if (!hasDigit) {
            return PasswordValidationStatus.DIGIT;
        }
        if (new String(password).getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            return PasswordValidationStatus.BYTE_LENGTH;
        }
        return PasswordValidationStatus.VALID;
    }

    public static String maskEmail(String emailInput) {
        String email = normalizeEmail(emailInput);
        int at = email.indexOf('@');
        if (at <= 0 || at == email.length() - 1) {
            return "***";
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() == 1) {
            return local + "***" + domain;
        }
        return local.charAt(0) + "***" + domain;
    }

    void cleanupExpiredForTest() {
        cleanupExpired(clock.getAsLong());
    }

    int activeChallengeCountForTest() {
        return challenges.size();
    }

    private RequestResult acceptedRequest(String challengeId, String email) {
        return new RequestResult(
                RequestStatus.ACCEPTED,
                challengeId,
                maskEmail(email),
                toSecondsCeiling(RESEND_COOLDOWN_MS)
        );
    }

    private VerifyResult recordInvalidAttempt(Challenge challenge) {
        challenge.verifyAttempts++;
        int remaining = Math.max(0, MAX_VERIFY_ATTEMPTS - challenge.verifyAttempts);
        if (remaining == 0) {
            clearOtpHash(challenge);
            return new VerifyResult(VerifyStatus.TOO_MANY_ATTEMPTS, 0);
        }
        return new VerifyResult(VerifyStatus.INVALID_CODE, remaining);
    }

    private String generateOtp() {
        return String.format(Locale.ROOT, "%06d", random.nextInt(OTP_BOUND));
    }

    private byte[] hashOtp(String challengeId, String otp) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(
                    (challengeId + ":" + otp).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private void cleanupExpired(long now) {
        cleanupExpired(now, null);
    }

    private void cleanupExpired(long now, String excludedChallengeId) {
        challenges.forEach((id, challenge) -> {
            if (id.equals(excludedChallengeId)) {
                return;
            }
            boolean remove;
            synchronized (challenge) {
                remove = !challenge.sending
                        && !challenge.resetting
                        && (challenge.cancelled
                        || now >= challenge.challengeExpiresAt
                        || (challenge.verified && now >= challenge.verifiedUntil));
            }
            if (remove && challenges.remove(id, challenge)) {
                synchronized (challenge) {
                    challenge.cancelled = true;
                    clearOtpHash(challenge);
                }
            }
        });

        sendRates.forEach((key, bucket) -> {
            if (bucket.isEmptyAfterCleanup(now)) {
                sendRates.remove(key, bucket);
            }
        });
    }

    private void invalidateAndRemove(Challenge challenge) {
        challenges.remove(challenge.id, challenge);
        synchronized (challenge) {
            challenge.cancelled = true;
            challenge.sending = false;
            challenge.resetting = false;
            clearOtpHash(challenge);
        }
    }

    private static void clearOtpHash(Challenge challenge) {
        if (challenge.otpHash != null) {
            Arrays.fill(challenge.otpHash, (byte) 0);
            challenge.otpHash = null;
        }
    }

    private static void clearPassword(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }

    private RateDecision acquireSendPermit(String key, long now) {
        RateBucket bucket = sendRates.computeIfAbsent(key, ignored -> new RateBucket());
        return bucket.tryAcquire(now);
    }

    private static String normalizeUsername(String username) {
        return username == null ? "" : username.trim();
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isValidIdentityInput(String username, String email) {
        return username.length() >= 3
                && username.length() <= 50
                && EMAIL_PATTERN.matcher(email).matches();
    }

    private static String rateKey(String username, String email) {
        return username + '\n' + email;
    }

    private static int toSecondsCeiling(long milliseconds) {
        return (int) Math.max(1, (milliseconds + 999L) / 1000L);
    }

    @FunctionalInterface
    interface OtpDelivery {
        void send(String email, String otp) throws Exception;
    }

    interface AccountGateway {
        User findForPasswordReset(String username, String email) throws Exception;

        UserDAO.PasswordResetUpdateResult resetPassword(
                int userId, String newRawPassword) throws Exception;
    }

    public enum RequestStatus {
        ACCEPTED,
        INVALID_INPUT,
        RATE_LIMITED,
        MAIL_FAILED,
        SYSTEM_ERROR
    }

    public enum VerifyStatus {
        SUCCESS,
        INVALID_CODE,
        EXPIRED,
        TOO_MANY_ATTEMPTS,
        NOT_FOUND,
        ALREADY_VERIFIED
    }

    public enum ResendStatus {
        SUCCESS,
        COOLDOWN,
        RATE_LIMITED,
        MAIL_FAILED,
        EXPIRED,
        NOT_FOUND,
        ALREADY_VERIFIED,
        IN_PROGRESS
    }

    public enum ResetStatus {
        SUCCESS,
        INVALID_PASSWORD,
        NOT_VERIFIED,
        SESSION_EXPIRED,
        SAME_AS_OLD_PASSWORD,
        ACCOUNT_UNAVAILABLE,
        UPDATE_FAILED,
        NOT_FOUND,
        IN_PROGRESS
    }

    public enum PasswordValidationStatus {
        VALID,
        REQUIRED,
        LENGTH,
        LETTER,
        DIGIT,
        WHITESPACE,
        BYTE_LENGTH
    }

    public static final class RequestResult {
        private final RequestStatus status;
        private final String challengeId;
        private final String maskedEmail;
        private final int retryAfterSeconds;

        RequestResult(RequestStatus status, String challengeId,
                      String maskedEmail, int retryAfterSeconds) {
            this.status = status;
            this.challengeId = challengeId;
            this.maskedEmail = maskedEmail;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public RequestStatus getStatus() {
            return status;
        }

        public String getChallengeId() {
            return challengeId;
        }

        public String getMaskedEmail() {
            return maskedEmail;
        }

        public int getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    public static final class VerifyResult {
        private final VerifyStatus status;
        private final int remainingAttempts;

        VerifyResult(VerifyStatus status, int remainingAttempts) {
            this.status = status;
            this.remainingAttempts = remainingAttempts;
        }

        public VerifyStatus getStatus() {
            return status;
        }

        public int getRemainingAttempts() {
            return remainingAttempts;
        }
    }

    public static final class ResendResult {
        private final ResendStatus status;
        private final int retryAfterSeconds;

        ResendResult(ResendStatus status, int retryAfterSeconds) {
            this.status = status;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public ResendStatus getStatus() {
            return status;
        }

        public int getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    public static final class ResetResult {
        private final ResetStatus status;
        private final PasswordValidationStatus validationStatus;

        ResetResult(ResetStatus status, PasswordValidationStatus validationStatus) {
            this.status = status;
            this.validationStatus = validationStatus;
        }

        public ResetStatus getStatus() {
            return status;
        }

        public PasswordValidationStatus getValidationStatus() {
            return validationStatus;
        }
    }

    private static final class Challenge {
        private final String id;
        private final int userId;
        private final String username;
        private final String email;
        private final boolean decoy;
        private final long createdAt;
        private final long challengeExpiresAt;

        private byte[] otpHash;
        private long otpExpiresAt;
        private int verifyAttempts;
        private int sendCount;
        private long lastSentAt;
        private boolean verified;
        private long verifiedUntil;
        private boolean sending;
        private boolean resetting;
        private boolean cancelled;

        private Challenge(String id, int userId, String username,
                          String email, boolean decoy, long createdAt) {
            this.id = id;
            this.userId = userId;
            this.username = username;
            this.email = email;
            this.decoy = decoy;
            this.createdAt = createdAt;
            this.challengeExpiresAt = createdAt + CHALLENGE_TTL_MS;
        }
    }

    private static final class RateBucket {
        private final Deque<Long> sends = new ArrayDeque<>();

        private synchronized RateDecision tryAcquire(long now) {
            removeExpired(now);
            if (sends.size() >= MAX_SENDS_PER_WINDOW) {
                long retry = sends.peekFirst() + RATE_WINDOW_MS - now;
                return new RateDecision(false, toSecondsCeiling(retry));
            }
            sends.addLast(now);
            return new RateDecision(true, 0);
        }

        private synchronized boolean isEmptyAfterCleanup(long now) {
            removeExpired(now);
            return sends.isEmpty();
        }

        private void removeExpired(long now) {
            while (!sends.isEmpty() && now - sends.peekFirst() >= RATE_WINDOW_MS) {
                sends.removeFirst();
            }
        }
    }

    private static final class RateDecision {
        private final boolean allowed;
        private final int retryAfterSeconds;

        private RateDecision(boolean allowed, int retryAfterSeconds) {
            this.allowed = allowed;
            this.retryAfterSeconds = retryAfterSeconds;
        }
    }
}
