package com.baldev.airline.common.feign;

import com.baldev.airline.common.exception.BusinessRuleException;
import com.baldev.airline.common.exception.ConflictException;
import com.baldev.airline.common.exception.DownstreamServiceException;
import com.baldev.airline.common.exception.ResourceNotFoundException;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Translates error responses from other airline services into the shared exception hierarchy, so that a
 * downstream 404/409/422 surfaces to the client with the same meaning instead of as a generic 500.
 *
 * <p>Only the {@code detail} and {@code code} fields of the downstream problem response are propagated (so a
 * client still sees e.g. {@code SEAT_UNAVAILABLE}); raw bodies are never exposed. Server errors and auth
 * failures between services become {@link DownstreamServiceException} (503).
 */
public class FeignErrorDecoder implements ErrorDecoder {

    private static final Logger log = LoggerFactory.getLogger(FeignErrorDecoder.class);
    private static final int MAX_BODY_BYTES = 8 * 1024;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** Stable error codes look like SEAT_UNAVAILABLE; anything else is ignored rather than forwarded. */
    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");

    private record Problem(String detail, String code) {
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        Problem problem = extractProblem(response);
        String detail = problem.detail();
        String code = problem.code();
        return switch (status) {
            case 404 -> new ResourceNotFoundException(detail != null ? detail : "Referenced resource not found");
            case 409 -> code != null
                    ? new ConflictException(code, detail != null ? detail : "Conflict in downstream service")
                    : new ConflictException(detail != null ? detail : "Conflict in downstream service");
            case 400, 422 -> code != null && !"VALIDATION_FAILED".equals(code)
                    ? new BusinessRuleException(code, detail != null ? detail : "Request rejected by downstream service")
                    : new BusinessRuleException(detail != null ? detail : "Request rejected by downstream service");
            default -> {
                log.error("Downstream call {} failed with status {}", methodKey, status);
                yield new DownstreamServiceException("A dependent service is unavailable, please retry later");
            }
        };
    }

    private static Problem extractProblem(Response response) {
        if (response.body() == null) {
            return new Problem(null, null);
        }
        try (InputStream in = response.body().asInputStream()) {
            byte[] bytes = in.readNBytes(MAX_BODY_BYTES);
            JsonNode node = JSON.readTree(new String(bytes, StandardCharsets.UTF_8));
            JsonNode detail = node.get("detail");
            JsonNode code = node.get("code");
            return new Problem(
                    detail != null && detail.isString() ? detail.asString() : null,
                    code != null && code.isString() && CODE.matcher(code.asString()).matches() ? code.asString() : null);
        } catch (Exception e) {
            return new Problem(null, null);
        }
    }
}
