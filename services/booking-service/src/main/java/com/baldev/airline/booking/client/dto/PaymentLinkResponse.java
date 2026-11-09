package com.baldev.airline.booking.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Client-side copy of payment-service's API model; only this service's view of the contract. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentLinkResponse {

    private Long id;

    private String payment_link_url;
    private String payment_link_id;
}
