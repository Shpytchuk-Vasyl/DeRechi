package org.shpytchuk.ingest.source.facebook;

import java.time.Instant;
import java.util.List;

final class ApifyFixtures {

    static final Instant FIRST = Instant.parse("2026-10-13T10:00:00Z");
    static final Instant SECOND = Instant.parse("2026-10-13T11:00:00Z");
    static final String GROUP_TITLE = "ЗАГУБИВ-ЗНАЙШОВ ТЕРНОПІЛЬ";

    static final String ITEMS = """
            [
              {
                "facebookUrl": "https://www.facebook.com/groups/zzter/",
                "url": "https://www.facebook.com/groups/zzter/permalink/9001/",
                "time": "2026-10-13T10:00:00.000Z",
                "user": {
                  "id": "pfbid02ZqqHS18k9RBsYemgDcvQ3rxm3yzFWfgvjvcJ97mMmGo",
                  "name": "Олена Коваль",
                  "profilePic": "https://scontent.example/olena.jpg"
                },
                "collaborators": [],
                "text": "Загубила ключі біля ринку",
                "topReactionsCount": 1,
                "id": "UzpfSTEwMDAxMjM0NTY3ODkwMTpWSzo5MDAx",
                "legacyId": "9001",
                "attachments": [
                  {
                    "thumbnail": "https://scontent.example/9001-thumb.jpg",
                    "__typename": "Photo",
                    "photo_image": {"uri": "https://scontent.example/9001.jpg", "height": 720, "width": 960},
                    "url": "https://www.facebook.com/photo/?fbid=1",
                    "ocrText": "May be an image of keys"
                  }
                ],
                "likesCount": 1,
                "facebookId": "153439147746716",
                "groupTitle": "ЗАГУБИВ-ЗНАЙШОВ ТЕРНОПІЛЬ",
                "inputUrl": "https://www.facebook.com/groups/zzter/"
              },
              {
                "facebookUrl": "https://www.facebook.com/groups/zzter/",
                "url": "https://www.facebook.com/groups/zzter/permalink/9002/",
                "time": "2026-10-13T11:00:00.000Z",
                "user": {"id": "pfbid0HBS8TpPESE95W6qzNWg5MAkf9EWNHNPEWLuCwkSVGoxu", "name": "Петро"},
                "text": "Знайдено собаку",
                "id": "UzpfSTEwMDA5ODc2NTQzMjEwMDpWSzo5MDAy",
                "legacyId": "9002",
                "groupTitle": "ЗАГУБИВ-ЗНАЙШОВ ТЕРНОПІЛЬ",
                "inputUrl": "https://www.facebook.com/groups/zzter/"
              }
            ]
            """;

    private ApifyFixtures() {
    }

    static List<ApifyPost> posts() {
        return List.of(
                new ApifyPost("UzpfSTEwMDAxMjM0NTY3ODkwMTpWSzo5MDAx", "9001", FIRST, "Загубила ключі біля ринку",
                        GROUP_TITLE, new ApifyPost.User("Олена Коваль"),
                        List.of(photo("https://scontent.example/9001.jpg")), null),
                new ApifyPost("UzpfSTEwMDA5ODc2NTQzMjEwMDpWSzo5MDAy", "9002", SECOND, "Знайдено собаку",
                        GROUP_TITLE, new ApifyPost.User("Петро"), null, null));
    }

    static ApifyPost.Attachment photo(String uri) {
        return new ApifyPost.Attachment("Photo", new ApifyPost.Image(uri, 960, 720));
    }
}
