package io.opentracing.contrib.spanmanager.concurrent;

import io.opentracing.Span;
import io.opentracing.contrib.spanmanager.GlobalSpanManager;
import io.opentracing.contrib.spanmanager.SpanManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

/**
 * Propagates the {@link SpanManager#currentSpan() currently active span} from the caller
 * into each call that is executed.
 * <p>
 * <em>Note:</em> The active span is merely propagated.
 * It is explicitly <b>not</b> finished when the calls end,
 * nor will new spans be automatically related to the propagated span.
 */
public class SpanPropagatingExecutorService implements ExecutorService {
    private final ExecutorService delegate;
    private final SpanManager spanManager;

    private SpanPropagatingExecutorService(ExecutorService delegate, SpanManager spanManager) {
        if (delegate == null) throw new NullPointerException("Delegate executor service is <null>.");
        if (spanManager == null) throw new NullPointerException("SpanManager is <null>.");
        this.delegate = delegate;
        this.spanManager = spanManager;
    }

    /**
     * Wraps the delegate ExecutorService to propagate the {@link GlobalSpanManager#currentSpan() currently active span}
     * of callers into the executed calls, using the {@link GlobalSpanManager}.
     *
     * @param delegate The executorservice to forward calls to.
     * @return An ExecutorService that propagates active spans from callers into executed calls.
     * @see #of(ExecutorService, SpanManager)
     * @see GlobalSpanManager
     */
    public static SpanPropagatingExecutorService of(final ExecutorService delegate) {
        return of(delegate, GlobalSpanManager.get());
    }

    /**
     * Wraps the delegate ExecutorService to propagate the {@link SpanManager#currentSpan() currently active span}
     * of callers into the executed calls, using the specified {@link SpanManager}.
     *
     * @param delegate    The executorservice to forward calls to.
     * @param spanManager The span manager to use.
     * @return An ExecutorService that propagates active spans from callers into executed calls.
     * @see #of(ExecutorService)
     */
    public static SpanPropagatingExecutorService of(ExecutorService delegate, SpanManager spanManager) {
        return new SpanPropagatingExecutorService(delegate, spanManager);
    }

    /**
     * Propagates the {@link GlobalSpanManager#currentSpan() custom current span} into the runnable
     * and performs cleanup afterwards.
     * <p>
     * <em>Note:</em> The <code>customCurrentSpan</code> is merely propagated.
     * The specified span is explicitly <b>not</b> finished by the runnable.
     *
     * @param runnable          The runnable to be executed.
     * @param customCurrentSpan The span to be propagated.
     * @return The wrapped runnable to execute with the custom span as current span.
     * @see GlobalSpanManager
     */
    private Runnable runnableWithCurrentSpan(Runnable runnable, Span customCurrentSpan) {
        return new RunnableWithManagedSpan(runnable, spanManager, customCurrentSpan);
    }

    /**
     * Propagates the {@link GlobalSpanManager#currentSpan() custom current span} into the callable
     * and performs cleanup afterwards.
     * <p>
     * <em>Note:</em> The <code>customCurrentSpan</code> is merely propagated.
     * The specified span is explicitly <b>not</b> finished by the callable.
     *
     * @param <T>               The callable result type.
     * @param callable          The callable to be executed.
     * @param customCurrentSpan The span to be propagated.
     * @return The wrapped callable to execute with the custom span as current span.
     */
    private <T> Callable<T> callableWithCurrentSpan(Callable<T> callable, Span customCurrentSpan) {
        return new CallableWithManagedSpan<T>(callable, spanManager, customCurrentSpan);
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(runnableWithCurrentSpan(command, spanManager.currentSpan()));
    }

    @Override
    public Future<?> submit(Runnable task) {
        return delegate.submit(runnableWithCurrentSpan(task, spanManager.currentSpan()));
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return delegate.submit(runnableWithCurrentSpan(task, spanManager.currentSpan()), result);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return delegate.submit(callableWithCurrentSpan(task, spanManager.currentSpan()));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return delegate.invokeAll(tasksWithCurrentSpan(tasks, spanManager.currentSpan()));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        return delegate.invokeAll(tasksWithCurrentSpan(tasks, spanManager.currentSpan()), timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return delegate.invokeAny(tasksWithCurrentSpan(tasks, spanManager.currentSpan()));
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.invokeAny(tasksWithCurrentSpan(tasks, spanManager.currentSpan()), timeout, unit);
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    private <T> Collection<? extends Callable<T>> tasksWithCurrentSpan(
            Collection<? extends Callable<T>> tasks, Span customCurrentSpan) {
        if (tasks == null) throw new NullPointerException("Collection of tasks is <null>.");
        Collection<Callable<T>> result = new ArrayList<Callable<T>>(tasks.size());
        for (Callable<T> task : tasks) result.add(callableWithCurrentSpan(task, customCurrentSpan));
        return result;
    }

}
