package com.baldev.airline.gateway.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private final ClientIpResolver resolver = new ClientIpResolver(List.of("10.0.0.0/8", "127.0.0.0/8", "::1/128"));

    @Test
    void directClientCannotChooseItsKeyWithXForwardedFor() {
        MockHttpServletRequest request = request("203.0.113.9", "1.2.3.4");
        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void behindTrustedProxiesTheRightmostUntrustedHopIsTheClient() {
        // client spoofs "1.2.3.4", Caddy appends the real client, nginx (10.0.0.5) appends Caddy (10.0.0.4).
        MockHttpServletRequest request = request("10.0.0.6", "1.2.3.4, 198.51.100.7, 10.0.0.4");
        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.7");
    }

    @Test
    void hostnamesAndGarbageAreNeverTrustedOrResolved() {
        assertThat(resolver.isTrusted("deadbeef")).isFalse();
        assertThat(resolver.isTrusted("localhost")).isFalse();
        assertThat(resolver.isTrusted("10.1.2.3")).isTrue();
        assertThat(resolver.isTrusted("::1")).isTrue();
    }

    private static MockHttpServletRequest request(String peer, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setRemoteAddr(peer);
        request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }
}
