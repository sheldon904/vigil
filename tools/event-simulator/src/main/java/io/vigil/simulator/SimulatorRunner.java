package io.vigil.simulator;

import io.vigil.common.model.SecurityEvent;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Drives the simulation: a stream of benign noise with three attack patterns
 * injected partway through. Runs once and exits; this is a dev tool, not a
 * service.
 */
@Component
public class SimulatorRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SimulatorRunner.class);

    private final IngestClient client;
    private final SimulatorProperties properties;

    public SimulatorRunner(IngestClient client, SimulatorProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public void run(String... args) throws Exception {
        EventFactory factory = new EventFactory(System.nanoTime());
        int benign = properties.benignEvents();
        log.info("posting to {} ({} benign events plus 3 attack bursts)",
                properties.ingestUrl(), benign);

        int sent = 0;
        for (int i = 0; i < benign; i++) {
            client.send(List.of(factory.benign()));
            sent++;

            // a third of the way in: brute force burst from one source
            if (i == benign / 3) {
                List<SecurityEvent> burst = factory.bruteForceBurst("10.0.0.66", "admin", 8);
                client.send(burst);
                sent += burst.size();
                log.info("injected brute force burst: 8 AUTH_FAILURE from 10.0.0.66 against admin");
            }

            // two thirds in: port scan burst from another source
            if (i == 2 * benign / 3) {
                List<SecurityEvent> burst = factory.portScanBurst("172.16.0.99", 20);
                client.send(burst);
                sent += burst.size();
                log.info("injected port scan burst: 20 distinct ports from 172.16.0.99");
            }

            // near the end: impossible travel pair for one account
            if (i == benign - 5) {
                List<SecurityEvent> pair = factory.impossibleTravelPair("carol");
                client.send(pair);
                sent += pair.size();
                log.info("injected impossible travel: carol from 10.0.0.42 then 192.168.1.99");
            }

            Thread.sleep(100);
        }

        log.info("done: {} events sent", sent);
    }
}
