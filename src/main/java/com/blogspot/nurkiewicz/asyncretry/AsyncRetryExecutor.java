package com.blogspot.nurkiewicz.asyncretry;

import com.blogspot.nurkiewicz.asyncretry.backoff.Backoff;
import com.blogspot.nurkiewicz.asyncretry.backoff.ExponentialDelayBackoff;
import com.blogspot.nurkiewicz.asyncretry.backoff.FixedIntervalBackoff;
import com.blogspot.nurkiewicz.asyncretry.function.RetryCallable;
import com.blogspot.nurkiewicz.asyncretry.function.RetryRunnable;
import com.blogspot.nurkiewicz.asyncretry.policy.RetryPolicy;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Predicate;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * @author Tomasz Nurkiewicz
 * @since 7/15/13, 11:06 PM
 */
public class AsyncRetryExecutor implements RetryExecutor {

	private final ScheduledExecutorService scheduler;
	private final boolean fixedDelay;
	private final RetryPolicy retryPolicy;
	private final Backoff backoff;

	public AsyncRetryExecutor(ScheduledExecutorService scheduler) {
		this(scheduler, RetryPolicy.DEFAULT, Backoff.DEFAULT);
	}

	public AsyncRetryExecutor(ScheduledExecutorService scheduler, Backoff backoff) {
		this(scheduler, RetryPolicy.DEFAULT, backoff);
	}

	public AsyncRetryExecutor(ScheduledExecutorService scheduler, RetryPolicy retryPolicy) {
		this(scheduler, retryPolicy, Backoff.DEFAULT);
	}

	public AsyncRetryExecutor(ScheduledExecutorService scheduler, RetryPolicy retryPolicy, Backoff backoff) {
		this(scheduler, retryPolicy, backoff, false);
	}

	public AsyncRetryExecutor(ScheduledExecutorService scheduler, RetryPolicy retryPolicy, Backoff backoff, boolean fixedDelay) {
		this.scheduler = Objects.requireNonNull(scheduler);
		this.retryPolicy = Objects.requireNonNull(retryPolicy);
		this.backoff = Objects.requireNonNull(backoff);
		this.fixedDelay = fixedDelay;
	}

	@Override
	public CompletableFuture<Void> doWithRetry(RetryRunnable action) {
		return getWithRetry(context -> {
			action.run(context);
			return null;
		});
	}

	@Override
	public <V> CompletableFuture<V> getWithRetry(Callable<V> task) {
		return getWithRetry(ctx -> task.call());
	}

	@Override
	public <V> CompletableFuture<V> getWithRetry(RetryCallable<V> task) {
		return scheduleImmediately(task);
	}

	private <V> CompletableFuture<V> scheduleImmediately(RetryCallable<V> function) {
		final RetryJob<V> task = createTask(function);
		scheduler.schedule(task, 0, MILLISECONDS);
		return task.getFuture();
	}

	protected <V> RetryJob<V> createTask(RetryCallable<V> function) {
		return new RetryJob<>(function, this);
	}

	public ScheduledExecutorService getScheduler() {
		return scheduler;
	}

	public boolean isFixedDelay() {
		return fixedDelay;
	}

	public RetryPolicy getRetryPolicy() {
		return retryPolicy;
	}

	public Backoff getBackoff() {
		return backoff;
	}

	public AsyncRetryExecutor withScheduler(ScheduledExecutorService scheduler) {
		return new AsyncRetryExecutor(scheduler, retryPolicy, backoff, fixedDelay);
	}

	public AsyncRetryExecutor withRetryPolicy(RetryPolicy retryPolicy) {
		return new AsyncRetryExecutor(scheduler, retryPolicy, backoff, fixedDelay);
	}

	public AsyncRetryExecutor withExponentialBackoff(long initialDelayMillis, double multiplier) {
		final ExponentialDelayBackoff backoff = new ExponentialDelayBackoff(initialDelayMillis, multiplier);
		return new AsyncRetryExecutor(scheduler, retryPolicy, backoff, fixedDelay);
	}

	public AsyncRetryExecutor withBackoff(Backoff backoff) {
		return new AsyncRetryExecutor(scheduler, retryPolicy, backoff, fixedDelay);
	}

	public AsyncRetryExecutor withFixedDelay() {
		return new AsyncRetryExecutor(scheduler, retryPolicy, backoff, true);
	}

	public AsyncRetryExecutor withFixedDelay(boolean fixedDelay) {
		return new AsyncRetryExecutor(scheduler, retryPolicy, backoff, fixedDelay);
	}

	public AsyncRetryExecutor retryFor(Class<Throwable> retryForThrowable) {
		return this.withRetryPolicy(retryPolicy.retryFor(retryForThrowable));
	}

	public AsyncRetryExecutor abortFor(Class<Throwable> abortForThrowable) {
		return this.withRetryPolicy(retryPolicy.abortFor(abortForThrowable));
	}

	public AsyncRetryExecutor abortIf(Predicate<Throwable> abortPredicate) {
		return this.withRetryPolicy(retryPolicy.abortIf(abortPredicate));
	}

	public AsyncRetryExecutor withUniformJitter() {
		return this.withBackoff(this.backoff.withUniformJitter());
	}

	public AsyncRetryExecutor withUniformJitter(long range) {
		return this.withBackoff(this.backoff.withUniformJitter(range));
	}

	public AsyncRetryExecutor withProportionalJitter() {
		return this.withBackoff(this.backoff.withProportionalJitter());
	}

	public AsyncRetryExecutor withProportionalJitter(double multiplier) {
		return this.withBackoff(this.backoff.withProportionalJitter(multiplier));
	}

	public AsyncRetryExecutor withMinDelay(long minDelayMillis) {
		return this.withBackoff(this.backoff.withMinDelay(minDelayMillis));
	}

	public AsyncRetryExecutor withMaxDelay(long maxDelayMillis) {
		return this.withBackoff(this.backoff.withMaxDelay(maxDelayMillis));
	}

	public AsyncRetryExecutor withMaxRetries(int times) {
		return this.withRetryPolicy(this.retryPolicy.withMaxRetries(times));
	}

	public AsyncRetryExecutor dontRetry() {
		return this.withRetryPolicy(this.retryPolicy.dontRetry());
	}

	public AsyncRetryExecutor noDelay() {
		return this.withBackoff(new FixedIntervalBackoff(0));
	}

}
