package org.shpytchuk.telegramingest;

import org.junit.jupiter.api.Test;
import org.shpytchuk.telegramingest.support.AbstractPostgresTests;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "derechi.telegram.api-id=0",
        "derechi.telegram.chats=zagubleno911=Львів"
})
class TelegramIngestApplicationTests extends AbstractPostgresTests {

    @Test
    void contextLoads() {
    }

}
