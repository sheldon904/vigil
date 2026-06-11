package io.vigil.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class HmacSignerTest {

    private static final String SECRET = "test-secret";

    @Test
    void signThenVerifyRoundTrips() {
        byte[] body = "{\"source\":\"10.0.0.5\"}".getBytes(StandardCharsets.UTF_8);

        String signature = HmacSigner.sign(SECRET, body);

        assertThat(HmacSigner.verify(SECRET, body, signature)).isTrue();
    }

    @Test
    void verifyIsCaseInsensitiveOnTheHexSignature() {
        byte[] body = "payload".getBytes(StandardCharsets.UTF_8);

        String signature = HmacSigner.sign(SECRET, body).toUpperCase();

        assertThat(HmacSigner.verify(SECRET, body, signature)).isTrue();
    }

    @Test
    void tamperedBodyFailsVerification() {
        byte[] original = "{\"source\":\"10.0.0.5\"}".getBytes(StandardCharsets.UTF_8);
        byte[] tampered = "{\"source\":\"10.0.0.6\"}".getBytes(StandardCharsets.UTF_8);

        String signature = HmacSigner.sign(SECRET, original);

        assertThat(HmacSigner.verify(SECRET, tampered, signature)).isFalse();
    }

    @Test
    void wrongSecretFailsVerification() {
        byte[] body = "payload".getBytes(StandardCharsets.UTF_8);

        String signature = HmacSigner.sign("other-secret", body);

        assertThat(HmacSigner.verify(SECRET, body, signature)).isFalse();
    }

    @Test
    void missingOrGarbageSignatureFailsVerification() {
        byte[] body = "payload".getBytes(StandardCharsets.UTF_8);

        assertThat(HmacSigner.verify(SECRET, body, null)).isFalse();
        assertThat(HmacSigner.verify(SECRET, body, "")).isFalse();
        assertThat(HmacSigner.verify(SECRET, body, "not-a-hex-signature")).isFalse();
    }

    @Test
    void signatureIsDeterministicForSameInput() {
        byte[] body = "payload".getBytes(StandardCharsets.UTF_8);

        assertThat(HmacSigner.sign(SECRET, body)).isEqualTo(HmacSigner.sign(SECRET, body));
    }
}
