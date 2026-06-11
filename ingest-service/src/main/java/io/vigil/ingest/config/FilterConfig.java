package io.vigil.ingest.config;

import io.vigil.ingest.security.HmacVerificationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers the HMAC filter explicitly instead of annotating it with
 * Component, so we control its URL pattern and its place in the filter chain.
 */
@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<HmacVerificationFilter> hmacFilter(IngestProperties properties) {
        var registration = new FilterRegistrationBean<>(
                new HmacVerificationFilter(properties.hmacSecret()));
        registration.addUrlPatterns("/events", "/events/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
