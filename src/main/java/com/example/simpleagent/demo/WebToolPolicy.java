package com.example.simpleagent.demo;

import java.io.IOException;
import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;

public class WebToolPolicy {
    public static final int DEFAULT_MAX_CHARS = 4_000;
    public static final int MAX_EXTRACTED_CHARS = 20_000;
    public static final int MAX_RESPONSE_BYTES = 2_000_000;
    public static final int MAX_REDIRECTS = 3;
    public static final int DEFAULT_MAX_LINKS = 20;
    public static final int MAX_LINKS = 50;
    public static final int DEFAULT_SEARCH_RESULTS = 5;
    public static final int MAX_SEARCH_RESULTS = 10;

    public URI requirePublicHttpUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("URL is required.");
        }

        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid URL: " + e.getMessage(), e);
        }

        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new IllegalArgumentException("URL must include http:// or https://.");
        }

        String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
        if (!normalizedScheme.equals("http") && !normalizedScheme.equals("https")) {
            throw new IllegalArgumentException("Only http and https URLs are allowed.");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL must include a public hostname.");
        }

        String asciiHost = IDN.toASCII(host).toLowerCase(Locale.ROOT);
        if (asciiHost.equals("localhost") || asciiHost.endsWith(".localhost") || asciiHost.endsWith(".local")) {
            throw new IllegalArgumentException("Local/private hostnames are not allowed.");
        }

        try {
            InetAddress[] addresses = InetAddress.getAllByName(asciiHost);
            if (addresses.length == 0) {
                throw new IllegalArgumentException("Hostname did not resolve to an address.");
            }

            for (InetAddress address : addresses) {
                if (isBlockedAddress(address)) {
                    throw new IllegalArgumentException("URL resolves to a local/private network address and is blocked.");
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not resolve hostname: " + asciiHost, e);
        }

        return uri;
    }

    public int boundedInt(Object value, int defaultValue, int min, int max) {
        int parsed = defaultValue;
        if (value instanceof Number number) {
            parsed = number.intValue();
        } else if (value != null) {
            try {
                parsed = Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                parsed = defaultValue;
            }
        }
        return Math.max(min, Math.min(max, parsed));
    }

    private boolean isBlockedAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address && bytes.length == 4) {
            int b0 = bytes[0] & 0xff;
            int b1 = bytes[1] & 0xff;
            return b0 == 0 || b0 == 10 || b0 == 127 || (b0 == 100 && b1 >= 64 && b1 <= 127)
                    || (b0 == 169 && b1 == 254) || (b0 == 172 && b1 >= 16 && b1 <= 31)
                    || (b0 == 192 && b1 == 168);
        }

        if (address instanceof Inet6Address && bytes.length == 16) {
            int first = bytes[0] & 0xff;
            return (first & 0xfe) == 0xfc;
        }

        return false;
    }
}
