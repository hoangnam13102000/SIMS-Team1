package com.view.forgotpassword;

import com.service.PasswordResetService;

import javax.swing.SwingWorker;
import java.awt.Component;
import java.awt.Cursor;
import java.util.function.Consumer;

/**
 * Boc trang thai + goi PasswordResetService bat dong bo ra khoi cac step
 * panel. Panel chi viec goi request/verify/resend/resetPassword va nhan ket
 * qua qua callback, khong can biet SwingWorker hay quan ly challengeId.
 */
public class PasswordResetFlowController {

    private final PasswordResetService resetService = PasswordResetService.getInstance();
    private final Component displayabilityAnchor;

    private String challengeId;
    private boolean busy;
    private boolean completed;

    public PasswordResetFlowController(Component displayabilityAnchor) {
        this.displayabilityAnchor = displayabilityAnchor;
    }

    public boolean isBusy() {
        return busy;
    }

    public boolean hasActiveChallenge() {
        return challengeId != null;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void requestOtp(String username, String email,
                            Consumer<PasswordResetService.RequestResult> onSuccess,
                            Runnable onError) {
        cancelCurrentChallenge();
        setBusy(true);
        new SwingWorker<PasswordResetService.RequestResult, Void>() {
            @Override
            protected PasswordResetService.RequestResult doInBackground() {
                return resetService.requestOtp(username, email);
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    PasswordResetService.RequestResult result = get();
                    if (!displayabilityAnchor.isDisplayable()) {
                        resetService.cancelChallenge(result.getChallengeId());
                        return;
                    }
                    if (result.getStatus() == PasswordResetService.RequestStatus.ACCEPTED) {
                        challengeId = result.getChallengeId();
                    }
                    onSuccess.accept(result);
                } catch (Exception e) {
                    onError.run();
                }
            }
        }.execute();
    }

    public void verifyOtp(String code,
                           Consumer<PasswordResetService.VerifyResult> onSuccess,
                           Runnable onError) {
        if (challengeId == null) {
            return;
        }
        setBusy(true);
        new SwingWorker<PasswordResetService.VerifyResult, Void>() {
            @Override
            protected PasswordResetService.VerifyResult doInBackground() {
                return resetService.verifyOtp(challengeId, code);
            }

            @Override
            protected void done() {
                setBusy(false);
                if (!displayabilityAnchor.isDisplayable()) {
                    return;
                }
                try {
                    onSuccess.accept(get());
                } catch (Exception e) {
                    onError.run();
                }
            }
        }.execute();
    }

    public void resendOtp(Consumer<PasswordResetService.ResendResult> onSuccess, Runnable onError) {
        if (challengeId == null) {
            return;
        }
        setBusy(true);
        new SwingWorker<PasswordResetService.ResendResult, Void>() {
            @Override
            protected PasswordResetService.ResendResult doInBackground() {
                return resetService.resendOtp(challengeId);
            }

            @Override
            protected void done() {
                setBusy(false);
                if (!displayabilityAnchor.isDisplayable()) {
                    return;
                }
                try {
                    onSuccess.accept(get());
                } catch (Exception e) {
                    onError.run();
                }
            }
        }.execute();
    }

    public void resetPassword(char[] password,
                               Consumer<PasswordResetService.ResetResult> onSuccess,
                               Runnable onError) {
        if (challengeId == null) {
            return;
        }
        setBusy(true);
        new SwingWorker<PasswordResetService.ResetResult, Void>() {
            @Override
            protected PasswordResetService.ResetResult doInBackground() {
                return resetService.resetPassword(challengeId, password);
            }

            @Override
            protected void done() {
                setBusy(false);
                if (!displayabilityAnchor.isDisplayable()) {
                    return;
                }
                try {
                    PasswordResetService.ResetResult result = get();
                    if (result.getStatus() == PasswordResetService.ResetStatus.SUCCESS) {
                        completed = true;
                        challengeId = null;
                    }
                    onSuccess.accept(result);
                } catch (Exception e) {
                    onError.run();
                }
            }
        }.execute();
    }

    public void cancelCurrentChallenge() {
        if (challengeId != null) {
            resetService.cancelChallenge(challengeId);
            challengeId = null;
        }
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        displayabilityAnchor.setCursor(Cursor.getPredefinedCursor(
                busy ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR));
    }
}