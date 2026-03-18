package com.chainsettle.service;

import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class OrgIdentityService {
    private static final Map<String, String> NORMALIZED = Map.of(
        "bankalpha", "BankAlpha",
        "bankbeta", "BankBeta",
        "clearinghouse", "ClearingHouse"
    );

    public String requireOrg(final String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            throw new IllegalArgumentException("X-ChainSettle-Org header is required for mutating requests");
        }
        return normalize(headerValue);
    }

    public String resolveReadOrg(final String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return "BankAlpha";
        }
        return normalize(headerValue);
    }

    public String normalize(final String rawValue) {
        final String normalized = NORMALIZED.get(rawValue.replace("-", "").toLowerCase(Locale.ROOT));
        if (normalized == null) {
            throw new IllegalArgumentException("Unknown organization: " + rawValue);
        }
        return normalized;
    }
}

