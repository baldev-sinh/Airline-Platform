package com.baldev.airline.common.payload.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sent by the client after the gateway redirects back. The id is only a lookup key: the server re-fetches the
 * payment from the gateway with its own credentials and verifies status, amount and ownership.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentVerifyRequest {

    @NotBlank(message = "razorpayPaymentId is required")
    @Pattern(regexp = "^pay_[A-Za-z0-9]{6,40}$", message = "razorpayPaymentId is malformed")
    private String razorpayPaymentId;

    /**
     * Optional: the payment link id Razorpay appends to the callback URL ({@code razorpay_payment_link_id}).
     * Used to find our payment when the provider's payment carries no {@code payment_id} note; the server then
     * confirms with the provider that the payment really belongs to this link before trusting it.
     */
    @Pattern(regexp = "^plink_[A-Za-z0-9]{6,40}$", message = "razorpayPaymentLinkId is malformed")
    private String razorpayPaymentLinkId;
}
