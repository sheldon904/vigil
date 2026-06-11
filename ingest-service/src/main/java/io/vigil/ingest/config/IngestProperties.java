package io.vigil.ingest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type safe view of the vigil.ingest.* keys in application.yml. Spring binds
 * the YAML values onto this record at startup, so a missing or malformed value
 * fails fast instead of surfacing as a null deep inside a request.
 *
 * @param hmacSecret shared secret used to verify the X-Signature request header
 */
@ConfigurationProperties(prefix = "vigil.ingest")
public record IngestProperties(String hmacSecret) {
}
