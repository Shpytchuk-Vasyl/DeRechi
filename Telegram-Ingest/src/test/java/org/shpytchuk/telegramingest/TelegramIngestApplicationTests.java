package org.shpytchuk.telegramingest;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.shpytchuk.telegramingest.pipeline.NoticeOutput;
import org.shpytchuk.telegramingest.pipeline.PreviewOutput;
import org.shpytchuk.telegramingest.pipeline.PublishingOutput;
import org.shpytchuk.telegramingest.support.AbstractPostgresTests;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramIngestApplicationTests {

    @Nested
    @SpringBootTest(properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            "derechi.telegram.api-id=0",
            "derechi.telegram.chats=zagubleno911=Львів"
    })
    class DryRun extends AbstractPostgresTests {

        @Autowired
        NoticeOutput output;

        @Test
        void previewsByDefault() {
            assertThat(output).isInstanceOf(PreviewOutput.class);
        }
    }

    @Nested
    @SpringBootTest(properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            "derechi.telegram.api-id=0",
            "derechi.ingest.mode=AUTO"
    })
    class Auto extends AbstractPostgresTests {

        @Autowired
        NoticeOutput output;

        @Test
        void publishes() {
            assertThat(output).isInstanceOf(PublishingOutput.class);
        }
    }
}
