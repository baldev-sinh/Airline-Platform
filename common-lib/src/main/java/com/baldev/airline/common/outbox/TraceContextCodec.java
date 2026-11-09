package com.baldev.airline.common.outbox;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Carries the trace of the request that wrote an outbox event over to the relay, so the Kafka message (and its
 * consumers) stay in the same distributed trace even though they are sent later from a background thread.
 */
public interface TraceContextCodec {

    TraceContextCodec NONE = new TraceContextCodec() {
        @Override
        public Map<String, String> capture() {
            return Map.of();
        }

        @Override
        public <T> T callWithin(Map<String, String> headers, String name, Supplier<T> action) {
            return action.get();
        }
    };

    /** Propagation headers (W3C {@code traceparent}, ...) of the current span; empty if none. */
    Map<String, String> capture();

    /** Runs {@code action} in a child span of the captured context, so instrumentation inside joins that trace. */
    <T> T callWithin(Map<String, String> headers, String name, Supplier<T> action);
}
