package org.shpytchuk.worker;

import org.junit.jupiter.api.Test;
import org.shpytchuk.worker.support.AbstractPostgresTests;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Starts the context on the test container rather than the developer's database: the scheduled
 * {@code ClaimFollowUpJob} runs as soon as the context is up and archives and deletes rows. With
 * {@code ddl-auto=validate} it also checks this module's copy of the entities against the changelog.
 */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class WorkerApplicationTests extends AbstractPostgresTests {

    @Test
    void contextLoads() {
    }

}
