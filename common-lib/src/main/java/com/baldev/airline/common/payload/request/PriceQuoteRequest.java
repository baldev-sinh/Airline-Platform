package com.baldev.airline.common.payload.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Internal request to price a set of per-flight items (ancillaries, meals). The pricing service validates that
 * every item exists, belongs to {@code flightId} (and {@code cabinClassId} when given) and is available.
 * Repeated ids mean quantity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceQuoteRequest {

    @NotNull
    private Long flightId;

    private Long cabinClassId;

    @NotNull
    @Builder.Default
    private List<Long> itemIds = new ArrayList<>();
}
