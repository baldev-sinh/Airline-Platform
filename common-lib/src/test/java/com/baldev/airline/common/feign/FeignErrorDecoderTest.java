package com.baldev.airline.common.feign;

import com.baldev.airline.common.exception.BusinessRuleException;
import com.baldev.airline.common.exception.ConflictException;
import com.baldev.airline.common.exception.DownstreamServiceException;
import com.baldev.airline.common.exception.ResourceNotFoundException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FeignErrorDecoderTest {

    private final FeignErrorDecoder decoder = new FeignErrorDecoder();

    @Test
    void notFoundKeepsDownstreamDetail() {
        Exception e = decoder.decode("SeatClient#hold", response(404, "{\"detail\":\"Seat 12 does not exist\"}"));
        assertThat(e).isInstanceOf(ResourceNotFoundException.class).hasMessage("Seat 12 does not exist");
    }

    @Test
    void conflictMapsToConflict() {
        Exception e = decoder.decode("SeatClient#hold", response(409, "{\"detail\":\"Seat 12A is no longer available\"}"));
        assertThat(e).isInstanceOf(ConflictException.class).hasMessageContaining("12A");
    }

    @Test
    void validationErrorsMapToBusinessRule() {
        assertThat(decoder.decode("m", response(422, "{}"))).isInstanceOf(BusinessRuleException.class);
        assertThat(decoder.decode("m", response(400, "not json"))).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void serverErrorsBecomeRetryable503WithoutLeakingTheBody() {
        Exception e = decoder.decode("m", response(500, "{\"detail\":\"java.sql.SQLException: secret internals\"}"));
        assertThat(e).isInstanceOf(DownstreamServiceException.class);
        assertThat(e.getMessage()).doesNotContain("SQLException");
    }

    private static Response response(int status, String body) {
        Request request = Request.create(Request.HttpMethod.GET, "http://svc/x", Map.of(), null, StandardCharsets.UTF_8, null);
        return Response.builder()
                .status(status)
                .request(request)
                .headers(Map.of())
                .body(body, StandardCharsets.UTF_8)
                .build();
    }
}
