package com.baldev.airline.common.outbox;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** {@link TraceContextCodec} on Micrometer Tracing (OpenTelemetry bridge in these services). */
class MicrometerTraceContextCodec implements TraceContextCodec {

    private final Tracer tracer;
    private final Propagator propagator;

    MicrometerTraceContextCodec(Tracer tracer, Propagator propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    @Override
    public Map<String, String> capture() {
        Span current = tracer.currentSpan();
        if (current == null) {
            return Map.of();
        }
        TraceContext context = current.context();
        Map<String, String> headers = new HashMap<>();
        propagator.inject(context, headers, Map::put);
        return headers;
    }

    @Override
    public <T> T callWithin(Map<String, String> headers, String name, Supplier<T> action) {
        if (headers == null || headers.isEmpty()) {
            return action.get();
        }
        Span span = propagator.extract(headers, Map::get).name(name).kind(Span.Kind.PRODUCER).start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            return action.get();
        } catch (RuntimeException e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }
}
