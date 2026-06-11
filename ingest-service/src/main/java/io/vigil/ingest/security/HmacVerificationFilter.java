package io.vigil.ingest.security;

import io.vigil.common.security.HmacSigner;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects any request to the events endpoint whose X-Signature header is not a
 * valid HMAC-SHA256 of the raw request body. Runs before the controller, so
 * unauthenticated payloads never reach business code or Kafka.
 *
 * <p>OncePerRequestFilter is a Spring base class that guarantees the filter
 * executes exactly once per request, even if the request is dispatched
 * internally more than once.
 */
public class HmacVerificationFilter extends OncePerRequestFilter {

    public static final String SIGNATURE_HEADER = "X-Signature";

    private final String secret;

    public HmacVerificationFilter(String secret) {
        this.secret = secret;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        // Read the body once here; downstream consumers get the cached copy.
        byte[] body = request.getInputStream().readAllBytes();
        String signature = request.getHeader(SIGNATURE_HEADER);

        if (!HmacSigner.verify(secret, body, signature)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"missing or invalid " + SIGNATURE_HEADER + "\"}");
            return;
        }

        filterChain.doFilter(new CachedBodyRequestWrapper(request, body), response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only the ingestion endpoint is signed; health and actuator stay open.
        return !request.getRequestURI().startsWith("/events");
    }

    /** Convenience for clients and tests: computes the header value for a body. */
    public static String signatureFor(String secret, String body) {
        return HmacSigner.sign(secret, body.getBytes(StandardCharsets.UTF_8));
    }
}
