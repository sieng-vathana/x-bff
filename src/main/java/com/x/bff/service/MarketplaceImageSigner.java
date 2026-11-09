package com.x.bff.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Signs product and store image links for the public marketplace.
 * File content normally needs a signed-in user; a signed link lets shoppers load only the images
 * the marketplace itself handed out, never arbitrary files by id.
 */
@Component
public class MarketplaceImageSigner {

    private static final Pattern FILE_CONTENT_URL = Pattern.compile("^(.*)/api/v1/files/(\\d+)/content(?:\\?.*)?$");
    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] key;

    public MarketplaceImageSigner(@Value("${jwt.secret}") String jwtSecret) {
        // Derive a separate key so image signatures never reveal anything about the token key.
        this.key = hmac(jwtSecret.getBytes(StandardCharsets.UTF_8), "marketplace-image-v1");
    }

    /** Rewrites a file content link to its signed public marketplace link; other URLs are returned unchanged. */
    public String publicUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        Matcher matcher = FILE_CONTENT_URL.matcher(url.trim());
        if (!matcher.matches()) {
            return url;
        }
        long fileId = Long.parseLong(matcher.group(2));
        return matcher.group(1) + "/api/v1/marketplace/images/" + fileId + "?sig=" + sign(fileId);
    }

    public boolean verify(long fileId, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(sign(fileId).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(long fileId) {
        byte[] digest = hmac(key, Long.toString(fileId));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 22);
    }

    private static byte[] hmac(byte[] secret, String value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to sign marketplace image link", exception);
        }
    }
}
