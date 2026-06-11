package io.vigil.simulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EventSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventSimulatorApplication.class, args);
    }
}
