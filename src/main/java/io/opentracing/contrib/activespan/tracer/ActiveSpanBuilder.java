package io.opentracing.contrib.activespan.tracer;

import io.opentracing.NoopSpanBuilder;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.contrib.activespan.ActiveSpanManager;
import io.opentracing.contrib.activespan.SpanDeactivator;

import java.util.Map;

/**
 * {@link SpanBuilder} that forwards all methods to a delegate.<br>
 * Only the {@link #start()} method is overridden, {@link ActiveSpanManager#activate(Span) activating}
 * the started {@link Span} and wrapping it in an {@link ActiveSpan} object.<br>
 * The {@link ActiveSpan} object {@link SpanDeactivator deactivates} the span automatically
 * when it is {@link ActiveSpan#finish() finished} or {@link ActiveSpan#close() closed}.
 *
 * @see ActiveSpanManager#activate(Span)
 * @see ActiveSpan#finish()
 */
final class ActiveSpanBuilder implements SpanBuilder {

    protected SpanBuilder delegate;

    private ActiveSpanBuilder(SpanBuilder delegate) {
        if (delegate == null) throw new NullPointerException("Delegate SpanBuilder was <null>.");
        this.delegate = delegate;
    }

    static SpanBuilder of(SpanBuilder spanBuilder) {
        return spanBuilder instanceof NoopSpanBuilder ? spanBuilder : new ActiveSpanBuilder(spanBuilder);
    }

    /**
     * Replaces the {@link #delegate} SpanBuilder by a delegated-method result.
     * <p>
     * For <code>null</code> or {@link NoopSpanBuilder} the active span builder short-circuits to the noop SpanBuilder,
     * similar to the <code>AbstractSpanBuilder</code> implementation.
     *
     * @param spanBuilder The builder returned from the delegate (normally '== delegate').
     * @return Either this re-wrapped ActiveSpanBuilder or the NoopSpanBuilder.
     */
    SpanBuilder rewrap(SpanBuilder spanBuilder) {
        if (spanBuilder == null || spanBuilder instanceof NoopSpanBuilder) return NoopSpanBuilder.INSTANCE;
        this.delegate = spanBuilder;
        return this;
    }

    /**
     * Starts the built Span and {@link ActiveSpanManager#activate(Span) activates} it.
     *
     * @return a new 'active' Span that deactivates itself upon <em>finish</em> or <em>close</em> calls.
     * @see ActiveSpan#finish()
     * @see ActiveSpanManager#activate(Span)
     */
    @Override
    public Span start() {
        Span newSpan = delegate.start();
        return new ActiveSpan(newSpan, ActiveSpanManager.get().activate(newSpan));
    }

    // All other methods are forwarded to the delegate SpanBuilder.

    public SpanBuilder asChildOf(SpanContext parent) {
        if (parent instanceof ActiveSpanBuilder) parent = ((ActiveSpanBuilder) parent).delegate;
        return rewrap(delegate.asChildOf(parent));
    }

    public SpanBuilder asChildOf(Span parent) {
        if (parent instanceof ActiveSpan) parent = ((ActiveSpan) parent).delegate;
        return rewrap(delegate.asChildOf(parent));
    }

    public SpanBuilder addReference(String referenceType, SpanContext context) {
        if (context instanceof ActiveSpanBuilder) context = ((ActiveSpanBuilder) context).delegate;
        return rewrap(delegate.addReference(referenceType, context));
    }

    public SpanBuilder withTag(String key, String value) {
        return rewrap(delegate.withTag(key, value));
    }

    public SpanBuilder withTag(String key, boolean value) {
        return rewrap(delegate.withTag(key, value));
    }

    public SpanBuilder withTag(String key, Number value) {
        return rewrap(delegate.withTag(key, value));
    }

    public SpanBuilder withStartTimestamp(long microseconds) {
        return rewrap(delegate.withStartTimestamp(microseconds));
    }

    public Iterable<Map.Entry<String, String>> baggageItems() {
        return delegate.baggageItems();
    }

}
