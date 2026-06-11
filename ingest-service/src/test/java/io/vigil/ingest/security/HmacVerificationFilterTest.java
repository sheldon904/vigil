package io.vigil.ingest.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.vigil.common.security.HmacSigner;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class HmacVerificationFilterTest {

    private static final String SECRET = "unit-test-secret";
    private static final String BODY = "{\"source\":\"10.0.0.5\",\"eventType\":\"AUTH_FAILURE\"}";

    private HmacVerificationFilter filter;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new HmacVerificationFilter(SECRET);
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    private MockHttpServletRequest postToEvents(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/events");
        request.setRequestURI("/events");
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    @Test
    void validSignaturePassesThroughAndBodyIsStillReadable() throws Exception {
        MockHttpServletRequest request = postToEvents(BODY);
        request.addHeader(HmacVerificationFilter.SIGNATURE_HEADER,
                HmacSigner.sign(SECRET, BODY.getBytes(StandardCharsets.UTF_8)));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        // the chain received a wrapped request whose body can be read again
        HttpServletRequest forwarded = (HttpServletRequest) chain.getRequest();
        assertThat(forwarded).isNotNull();
        assertThat(new String(forwarded.getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo(BODY);
    }

    @Test
    void missingSignatureIsRejectedWith401() throws Exception {
        MockHttpServletRequest request = postToEvents(BODY);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getContentAsString()).contains("X-Signature");
    }

    @Test
    void tamperedBodyIsRejectedWith401() throws Exception {
        String signatureOfOtherBody = HmacSigner.sign(SECRET,
                "{\"source\":\"1.2.3.4\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletRequest request = postToEvents(BODY);
        request.addHeader(HmacVerificationFilter.SIGNATURE_HEADER, signatureOfOtherBody);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void signatureWithWrongSecretIsRejectedWith401() throws Exception {
        MockHttpServletRequest request = postToEvents(BODY);
        request.addHeader(HmacVerificationFilter.SIGNATURE_HEADER,
                HmacSigner.sign("some-other-secret", BODY.getBytes(StandardCharsets.UTF_8)));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void nonEventPathsAreNotFiltered() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        request.setRequestURI("/actuator/health");

        filter.doFilter(request, response, chain);

        // no signature, yet the request reaches the chain untouched
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }
}
