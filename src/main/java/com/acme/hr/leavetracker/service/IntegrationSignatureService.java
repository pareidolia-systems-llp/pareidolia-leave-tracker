package com.acme.hr.leavetracker.service;

import com.acme.hr.leavetracker.config.AppProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class IntegrationSignatureService {
    private static final String HMAC_SHA_256 = "HmacSHA256";
    private final AppProperties properties;

    public IntegrationSignatureService(AppProperties properties) {
        this.properties = properties;
    }

    public String signDecision(String requestId, String action, String token, String managerComment) {
        return hmac(canonicalDecision(requestId, action, token, managerComment));
    }

    public boolean verifyDecision(String signature, String requestId, String action, String token, String managerComment) {
        if (signature == null || !isConfigured()) {
            return false;
        }
        return MessageDigest.isEqual(signature.getBytes(StandardCharsets.US_ASCII),
                signDecision(requestId, action, token, managerComment).getBytes(StandardCharsets.US_ASCII));
    }

    public boolean isConfigured() {
        return properties.googleScript() != null && properties.googleScript().sharedSecret() != null
                && !properties.googleScript().sharedSecret().isBlank();
    }

    private String hmac(String content) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(properties.googleScript().sharedSecret().getBytes(StandardCharsets.UTF_8), HMAC_SHA_256));
            return HexFormat.of().formatHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not calculate integration signature", ex);
        }
    }

    public static String canonicalDecision(String requestId, String action, String token, String managerComment) {
        return requestId + "\n" + action + "\n" + token + "\n" + (managerComment == null ? "" : managerComment);
    }
}
