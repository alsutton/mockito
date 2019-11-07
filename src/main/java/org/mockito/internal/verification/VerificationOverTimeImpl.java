/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.internal.verification;

import java.time.Duration;

import org.mockito.exceptions.base.MockitoAssertionError;
import org.mockito.internal.util.Timer;
import org.mockito.internal.verification.api.VerificationData;
import org.mockito.verification.VerificationMode;

/**
 * Verifies that another verification mode (the delegate) is satisfied within a certain timeframe
 * (before timeout has passed, measured from the call to verify()), and either returns immediately
 * once it does, or waits until it is definitely satisfied once the full time has passed.
 */
public class VerificationOverTimeImpl implements VerificationMode {

    private final Duration pollingPeriod;
    private final VerificationMode delegate;
    private final boolean returnOnSuccess;
    private final Timer timer;

    /**
     * Create this verification mode, to be used to verify invocation ongoing data later.
     *
     * @param pollingPeriod The frequency to poll delegate.verify(), to check whether the delegate has been satisfied
     * @param duration The max time to wait for the delegate verification mode to be satisfied
     * @param delegate The verification mode to delegate overall success or failure to
     * @param returnOnSuccess Whether to immediately return successfully once the delegate is satisfied (as in
     *                        {@link org.mockito.verification.VerificationWithTimeout}, or to only return once
     *                        the delegate is satisfied and the full duration has passed (as in
     *                        {@link org.mockito.verification.VerificationAfterDelay}).
     */
    public VerificationOverTimeImpl(Duration pollingPeriod, Duration duration, VerificationMode delegate, boolean returnOnSuccess) {
        this(pollingPeriod, delegate, returnOnSuccess, new Timer(duration));
    }

    /**
     * Create this verification mode, to be used to verify invocation ongoing data later.
     *
     * @param pollingPeriod The frequency to poll delegate.verify(), to check whether the delegate has been satisfied
     * @param delegate The verification mode to delegate overall success or failure to
     * @param returnOnSuccess Whether to immediately return successfully once the delegate is satisfied (as in
     *                        {@link org.mockito.verification.VerificationWithTimeout}, or to only return once
     *                        the delegate is satisfied and the full duration has passed (as in
     *                        {@link org.mockito.verification.VerificationAfterDelay}).
     * @param timer Checker of whether the duration of the verification is still acceptable
     */
    public VerificationOverTimeImpl(Duration pollingPeriod, VerificationMode delegate, boolean returnOnSuccess, Timer timer) {
        this.pollingPeriod = pollingPeriod;
        this.delegate = delegate;
        this.returnOnSuccess = returnOnSuccess;
        this.timer = timer;
    }

    /**
     * Verify the given ongoing verification data, and confirm that it satisfies the delegate verification mode
     * before the full duration has passed.
     *
     * In practice, this polls the delegate verification mode until it is satisfied. If it is not satisfied once
     * the full duration has passed, the last error returned by the delegate verification mode will be thrown
     * here in turn. This may be thrown early if the delegate is unsatisfied and the verification mode is known
     * to never recover from this situation (e.g. {@link AtMost}).
     *
     * If it is satisfied before the full duration has passed, behaviour is dependent on the returnOnSuccess parameter
     * given in the constructor. If true, this verification mode is immediately satisfied once the delegate is. If
     * false, this verification mode is not satisfied until the delegate is satisfied and the full time has passed.
     *
     * @throws MockitoAssertionError if the delegate verification mode does not succeed before the timeout
     */
    public void verify(VerificationData data) {
        AssertionError error = null;

        timer.start();
        while (timer.isCounting()) {
            try {
                delegate.verify(data);

                if (returnOnSuccess) {
                    return;
                } else {
                    error = null;
                }
            } catch (MockitoAssertionError e) {
                error = handleVerifyException(e);
            }
            catch (AssertionError e) {
                error = handleVerifyException(e);
            }
        }

        if (error != null) {
            throw error;
        }
    }

    private AssertionError handleVerifyException(AssertionError e) {
        if (canRecoverFromFailure(delegate)) {
            sleep(pollingPeriod);
            return e;
        } else {
            throw e;
        }
    }

    protected boolean canRecoverFromFailure(VerificationMode verificationMode) {
        return !(verificationMode instanceof AtMost || verificationMode instanceof NoMoreInteractions);
    }

    public VerificationOverTimeImpl copyWithVerificationMode(VerificationMode verificationMode) {
        return new VerificationOverTimeImpl(pollingPeriod, timer.duration(), verificationMode, returnOnSuccess);
    }

    private void sleep(Duration sleep) {
        try {
            Thread.sleep(sleep.toMillis());
        } catch (InterruptedException ie) {
            throw new RuntimeException("Thread sleep has been interrupted", ie);
        }
    }

    public boolean isReturnOnSuccess() {
        return returnOnSuccess;
    }

    public Duration getPollingPeriod() {
        return pollingPeriod;
    }

    public Timer getTimer() {
        return timer;
    }

    public VerificationMode getDelegate() {
        return delegate;
    }
}
