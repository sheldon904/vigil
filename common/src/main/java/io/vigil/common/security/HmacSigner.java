package io.vigil.common.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signs and verifies request bodies with HMAC-SHA256.
 *
 * <p>Both the event simulator (client) and the ingest service (server) use this
 * class, which guarantees they compute the signature the same way: an HMAC over
 * the exact raw request bytes, rendered as lowercase hex.
 */
public final class HmacSigner {

    private static final String ALGORITHM = "HmacSHA256";

    private HmacSigner() {
        // static utility, never instantiated
    }

    /** Computes the HMAC-SHA256 of the body using the secret, as lowercase hex. */
    public static String sign(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // HmacSHA256 is mandated by the JCA spec, so this cannot happen with a valid key
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    /**
     * Verifies a signature against the body. Uses a constant time comparison so
     * an attacker cannot learn the correct signature byte by byte from timing.
     */
    public static boolean verify(String secret, byte[] body, String signatureHex) {
        if (signatureHex == null || signatureHex.isBlank()) {
            return false;
        }
        byte[] expected = sign(secret, body).getBytes(StandardCharsets.UTF_8);
        byte[] provided = signatureHex.toLowerCase().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, provided);
    }
}
