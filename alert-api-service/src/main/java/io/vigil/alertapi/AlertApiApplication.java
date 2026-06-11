package io.vigil.alertapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;

@SpringBootApplication
// serialize Page responses as a stable DTO shape instead of PageImpl internals
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
public class AlertApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlertApiApplication.class, args);
    }
}
