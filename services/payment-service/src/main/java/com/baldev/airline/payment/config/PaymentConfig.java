package com.baldev.airline.payment.config;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(PaymentProperties.class)
public class PaymentConfig {

    /** One thread-safe client for the application, instead of a new client (and connection pool) per call. */
    @Bean
    @Profile("!payment-stub")
    public RazorpayClient razorpayClient(PaymentProperties properties) throws RazorpayException {
        return new RazorpayClient(properties.razorpay().keyId(), properties.razorpay().keySecret());
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
