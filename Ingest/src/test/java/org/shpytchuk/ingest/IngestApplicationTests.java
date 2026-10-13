package org.shpytchuk.ingest;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.shpytchuk.ingest.publish.Publishers;
import org.shpytchuk.ingest.source.PostSource;
import org.shpytchuk.ingest.source.facebook.FacebookPostSource;
import org.shpytchuk.ingest.source.telegram.TelegramPostSource;
import org.shpytchuk.ingest.support.AbstractPostgresTests;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IngestApplicationTests {

    @Nested
    @SpringBootTest(properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            "derechi.telegram.api-id=0",
            "derechi.telegram.chats=zagubleno911=Львів"
    })
    class ByDefault extends AbstractPostgresTests {

        @Autowired
        Publishers publishers;

        @Autowired
        List<PostSource> sources;

        @Test
        void previewsInTheReviewChatAndReadsTelegramAndFacebook() {
            assertThat(publishers.names()).containsExactly("review");
            assertThat(sources).extracting(PostSource::name)
                    .containsExactlyInAnyOrder(TelegramPostSource.NAME, FacebookPostSource.NAME);
        }
    }

    @Nested
    @SpringBootTest(properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            "derechi.telegram.api-id=0",
            "derechi.ingest.publishers=site, channel, review"
    })
    class SiteChannelAndReview extends AbstractPostgresTests {

        @Autowired
        Publishers publishers;

        @Test
        void publishesEverywhere() {
            assertThat(publishers.names()).containsExactly("site", "channel", "review");
        }
    }
}
