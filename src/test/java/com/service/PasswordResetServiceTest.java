package com.service;

import com.dao.UserDAO;
import com.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordResetServiceTest {

    private MutableClock clock;
    private FakeAccounts accounts;
    private CapturingDelivery delivery;
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        accounts = new FakeAccounts();
        delivery = new CapturingDelivery();
        service = new PasswordResetService(
                accounts,
                delivery,
                clock,
                new SequenceRandom(123456, 654321, 111111, 222222)
        );
    }

    @Test
    void requestOtp_sendsExactlySixDigits() {
        PasswordResetService.RequestResult result =
                service.requestOtp("lan.nguyen", "lan.nguyen@gmail.com");

        assertEquals(PasswordResetService.RequestStatus.ACCEPTED, result.getStatus());
        assertTrue(delivery.lastOtp().matches("\\d{6}"));
        assertEquals("l***@gmail.com", result.getMaskedEmail());
    }

    @Test
    void requestOtp_unknownAccount_returnsGenericAcceptedWithoutSendingMail() {
        PasswordResetService.RequestResult result =
                service.requestOtp("unknown", "unknown@example.com");

        assertEquals(PasswordResetService.RequestStatus.ACCEPTED, result.getStatus());
        assertEquals(0, delivery.sentOtps.size());
        assertEquals(
                PasswordResetService.VerifyStatus.INVALID_CODE,
                service.verifyOtp(result.getChallengeId(), "123456").getStatus()
        );
    }

    @Test
    void verifyOtp_wrongThenCorrect() {
        PasswordResetService.RequestResult request = requestValidOtp();
        String otp = delivery.lastOtp();

        PasswordResetService.VerifyResult wrong =
                service.verifyOtp(request.getChallengeId(), differentCode(otp));
        PasswordResetService.VerifyResult correct =
                service.verifyOtp(request.getChallengeId(), otp);

        assertEquals(PasswordResetService.VerifyStatus.INVALID_CODE, wrong.getStatus());
        assertEquals(4, wrong.getRemainingAttempts());
        assertEquals(PasswordResetService.VerifyStatus.SUCCESS, correct.getStatus());
    }

    @Test
    void verifyOtp_expiredCodeIsRejected() {
        PasswordResetService.RequestResult request = requestValidOtp();
        clock.advance(PasswordResetService.OTP_TTL_MS + 1);

        assertEquals(
                PasswordResetService.VerifyStatus.EXPIRED,
                service.verifyOtp(request.getChallengeId(), delivery.lastOtp()).getStatus()
        );
    }

    @Test
    void verifyOtp_fifthWrongAttemptInvalidatesCurrentOtp() {
        PasswordResetService.RequestResult request = requestValidOtp();
        String wrong = differentCode(delivery.lastOtp());

        for (int i = 0; i < 4; i++) {
            assertEquals(
                    PasswordResetService.VerifyStatus.INVALID_CODE,
                    service.verifyOtp(request.getChallengeId(), wrong).getStatus()
            );
        }
        assertEquals(
                PasswordResetService.VerifyStatus.TOO_MANY_ATTEMPTS,
                service.verifyOtp(request.getChallengeId(), wrong).getStatus()
        );
    }

    @Test
    void resendOtp_invalidatesOldCodeAndAcceptsNewCode() {
        PasswordResetService.RequestResult request = requestValidOtp();
        String oldOtp = delivery.lastOtp();
        clock.advance(PasswordResetService.RESEND_COOLDOWN_MS);

        PasswordResetService.ResendResult resend =
                service.resendOtp(request.getChallengeId());
        String newOtp = delivery.lastOtp();

        assertEquals(PasswordResetService.ResendStatus.SUCCESS, resend.getStatus());
        assertNotEquals(oldOtp, newOtp);
        assertEquals(
                PasswordResetService.VerifyStatus.INVALID_CODE,
                service.verifyOtp(request.getChallengeId(), oldOtp).getStatus()
        );
        assertEquals(
                PasswordResetService.VerifyStatus.SUCCESS,
                service.verifyOtp(request.getChallengeId(), newOtp).getStatus()
        );
    }

    @Test
    void resetPassword_verifiedChallengeCanOnlyBeConsumedOnce() {
        PasswordResetService.RequestResult request = requestValidOtp();
        service.verifyOtp(request.getChallengeId(), delivery.lastOtp());
        char[] password = "NewPassword123".toCharArray();

        PasswordResetService.ResetResult first =
                service.resetPassword(request.getChallengeId(), password);
        PasswordResetService.ResetResult second =
                service.resetPassword(request.getChallengeId(), "AnotherPass123".toCharArray());

        assertEquals(PasswordResetService.ResetStatus.SUCCESS, first.getStatus());
        assertEquals(PasswordResetService.ResetStatus.NOT_FOUND, second.getStatus());
        assertArrayEquals(new char[password.length], password);
        assertEquals(1, accounts.resetCalls);
    }

    @Test
    void resetPassword_unverifiedChallengeIsRejected() {
        PasswordResetService.RequestResult request = requestValidOtp();

        assertEquals(
                PasswordResetService.ResetStatus.NOT_VERIFIED,
                service.resetPassword(
                        request.getChallengeId(), "NewPassword123".toCharArray()).getStatus()
        );
        assertEquals(0, accounts.resetCalls);
    }

    @Test
    void resetPassword_verifiedSessionExpires() {
        PasswordResetService.RequestResult request = requestValidOtp();
        service.verifyOtp(request.getChallengeId(), delivery.lastOtp());
        clock.advance(PasswordResetService.VERIFIED_TTL_MS + 1);

        assertEquals(
                PasswordResetService.ResetStatus.SESSION_EXPIRED,
                service.resetPassword(
                        request.getChallengeId(), "NewPassword123".toCharArray()).getStatus()
        );
    }

    @Test
    void resendOtp_enforcesCooldown() {
        PasswordResetService.RequestResult request = requestValidOtp();

        PasswordResetService.ResendResult result =
                service.resendOtp(request.getChallengeId());

        assertEquals(PasswordResetService.ResendStatus.COOLDOWN, result.getStatus());
        assertTrue(result.getRetryAfterSeconds() > 0);
        assertEquals(1, delivery.sentOtps.size());
    }

    @Test
    void resendOtp_enforcesThreeSendsPerFifteenMinutes() {
        PasswordResetService.RequestResult request = requestValidOtp();

        clock.advance(PasswordResetService.RESEND_COOLDOWN_MS);
        assertEquals(
                PasswordResetService.ResendStatus.SUCCESS,
                service.resendOtp(request.getChallengeId()).getStatus()
        );
        clock.advance(PasswordResetService.RESEND_COOLDOWN_MS);
        assertEquals(
                PasswordResetService.ResendStatus.SUCCESS,
                service.resendOtp(request.getChallengeId()).getStatus()
        );
        clock.advance(PasswordResetService.RESEND_COOLDOWN_MS);
        assertEquals(
                PasswordResetService.ResendStatus.RATE_LIMITED,
                service.resendOtp(request.getChallengeId()).getStatus()
        );
        assertEquals(3, delivery.sentOtps.size());
    }

    @Test
    void maskEmail_neverReturnsFullLocalPart() {
        assertEquals("t***@gmail.com", PasswordResetService.maskEmail("test@gmail.com"));
        assertEquals("a***@example.com", PasswordResetService.maskEmail("a@example.com"));
        assertEquals("***", PasswordResetService.maskEmail("invalid"));
    }

    @Test
    void validatePassword_enforcesRecoveryPolicy() {
        assertEquals(
                PasswordResetService.PasswordValidationStatus.LENGTH,
                PasswordResetService.validatePassword("Abc123".toCharArray())
        );
        assertEquals(
                PasswordResetService.PasswordValidationStatus.LETTER,
                PasswordResetService.validatePassword("12345678".toCharArray())
        );
        assertEquals(
                PasswordResetService.PasswordValidationStatus.DIGIT,
                PasswordResetService.validatePassword("Password".toCharArray())
        );
        assertEquals(
                PasswordResetService.PasswordValidationStatus.WHITESPACE,
                PasswordResetService.validatePassword(" Password123".toCharArray())
        );
        assertEquals(
                PasswordResetService.PasswordValidationStatus.VALID,
                PasswordResetService.validatePassword("Password123".toCharArray())
        );
    }

    @Test
    void resetPassword_sameAsOldPasswordCanBeRetried() {
        PasswordResetService.RequestResult request = requestValidOtp();
        service.verifyOtp(request.getChallengeId(), delivery.lastOtp());
        accounts.updateResult = UserDAO.PasswordResetUpdateResult.SAME_AS_OLD_PASSWORD;

        assertEquals(
                PasswordResetService.ResetStatus.SAME_AS_OLD_PASSWORD,
                service.resetPassword(
                        request.getChallengeId(), "SamePassword123".toCharArray()).getStatus()
        );

        accounts.updateResult = UserDAO.PasswordResetUpdateResult.SUCCESS;
        assertEquals(
                PasswordResetService.ResetStatus.SUCCESS,
                service.resetPassword(
                        request.getChallengeId(), "Different123".toCharArray()).getStatus()
        );
    }

    @Test
    void cleanup_removesExpiredChallenges() {
        requestValidOtp();
        assertEquals(1, service.activeChallengeCountForTest());

        clock.advance(PasswordResetService.CHALLENGE_TTL_MS + 1);
        service.cleanupExpiredForTest();

        assertEquals(0, service.activeChallengeCountForTest());
    }

    @Test
    void failedDeliveryDoesNotLeaveVerifiableChallenge() {
        delivery.failure = new Exception("SMTP unavailable");

        PasswordResetService.RequestResult result =
                service.requestOtp("lan.nguyen", "lan.nguyen@gmail.com");

        assertEquals(PasswordResetService.RequestStatus.MAIL_FAILED, result.getStatus());
        assertNull(result.getChallengeId());
        assertEquals(0, service.activeChallengeCountForTest());
    }

    private PasswordResetService.RequestResult requestValidOtp() {
        return service.requestOtp("lan.nguyen", "lan.nguyen@gmail.com");
    }

    private static String differentCode(String otp) {
        char replacement = otp.charAt(5) == '9' ? '0' : (char) (otp.charAt(5) + 1);
        return otp.substring(0, 5) + replacement;
    }

    private static final class MutableClock implements LongSupplier {
        private long now = 1_000_000L;

        @Override
        public long getAsLong() {
            return now;
        }

        private void advance(long millis) {
            now += millis;
        }
    }

    private static final class SequenceRandom extends SecureRandom {
        private final int[] values;
        private int index;

        private SequenceRandom(int... values) {
            this.values = values;
        }

        @Override
        public int nextInt(int bound) {
            int value = values[Math.min(index, values.length - 1)];
            index++;
            return Math.floorMod(value, bound);
        }
    }

    private static final class CapturingDelivery
            implements PasswordResetService.OtpDelivery {
        private final List<String> sentOtps = new ArrayList<>();
        private Exception failure;

        @Override
        public void send(String email, String otp) throws Exception {
            if (failure != null) {
                throw failure;
            }
            sentOtps.add(otp);
        }

        private String lastOtp() {
            return sentOtps.get(sentOtps.size() - 1);
        }
    }

    private static final class FakeAccounts
            implements PasswordResetService.AccountGateway {
        private UserDAO.PasswordResetUpdateResult updateResult =
                UserDAO.PasswordResetUpdateResult.SUCCESS;
        private int resetCalls;

        @Override
        public User findForPasswordReset(String username, String email) {
            if (!"lan.nguyen".equals(username)
                    || !"lan.nguyen@gmail.com".equals(email)) {
                return null;
            }
            User user = new User();
            user.setUserId(6);
            user.setUsername(username);
            user.setEmail(email);
            user.setStatus("ACTIVE");
            return user;
        }

        @Override
        public UserDAO.PasswordResetUpdateResult resetPassword(
                int userId, String newRawPassword) {
            resetCalls++;
            return updateResult;
        }
    }
}