package com.baldev.airline.gateway.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * The address of the client that sent a request, as far as it can be trusted.
 *
 * <p>{@code X-Forwarded-For} is a list a client can start with anything it likes; only the entries appended by
 * our own proxies are reliable. So the list is read from the right, skipping trusted proxies, and the first
 * address that is not one of them is the client. If the direct peer is not a trusted proxy, the header is ignored
 * entirely (someone is talking to the gateway directly).
 */
public class ClientIpResolver {

    private record Cidr(BigInteger network, BigInteger mask, int bytes) {
        boolean contains(InetAddress address) {
            byte[] raw = address.getAddress();
            return raw.length == bytes && new BigInteger(1, raw).and(mask).equals(network);
        }
    }

    private final List<Cidr> trusted = new ArrayList<>();

    public ClientIpResolver(List<String> trustedProxies) {
        for (String cidr : trustedProxies) {
            trusted.add(parse(cidr.trim()));
        }
    }

    public String resolve(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        if (!isTrusted(peer)) {
            return peer;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return peer;
        }
        String[] hops = forwarded.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = hops[i].trim();
            if (!hop.isEmpty() && !isTrusted(hop)) {
                return hop;
            }
        }
        // Every hop is one of our proxies: the leftmost is the closest we get to the client.
        return hops[0].trim().isEmpty() ? peer : hops[0].trim();
    }

    boolean isTrusted(String address) {
        InetAddress parsed = literal(address);
        if (parsed == null) {
            return false;
        }
        for (Cidr cidr : trusted) {
            if (cidr.contains(parsed)) {
                return true;
            }
        }
        return false;
    }

    /** Parses an IP literal without DNS lookups; anything else (hostnames, garbage) is not an address. */
    private static InetAddress literal(String value) {
        // Only IPv4 dotted quads and IPv6 literals: InetAddress would resolve anything else through DNS
        // (e.g. "deadbeef"), letting a client trigger a lookup per request.
        boolean ipv4 = value != null && value.matches("\\d{1,3}(\\.\\d{1,3}){3}");
        boolean ipv6 = value != null && value.contains(":") && value.matches("[0-9a-fA-F:.]+");
        if (!ipv4 && !ipv6) {
            return null;
        }
        try {
            return InetAddress.getByName(value);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private static Cidr parse(String cidr) {
        String[] parts = cidr.split("/");
        InetAddress address = literal(parts[0]);
        if (address == null) {
            throw new IllegalArgumentException("Invalid trusted proxy CIDR: " + cidr);
        }
        int bytes = address.getAddress().length;
        int prefix = parts.length > 1 ? Integer.parseInt(parts[1]) : bytes * 8;
        BigInteger all = BigInteger.ONE.shiftLeft(bytes * 8).subtract(BigInteger.ONE);
        BigInteger mask = all.shiftRight(prefix).xor(all);
        return new Cidr(new BigInteger(1, address.getAddress()).and(mask), mask, bytes);
    }
}
