package org.shpytchuk.ingest.telegram;

import it.tdlight.jni.TdApi;
import org.junit.jupiter.api.Test;
import org.shpytchuk.ingest.source.IncomingPost;
import org.shpytchuk.ingest.source.PhotoRef;
import org.shpytchuk.ingest.source.PostKey;
import org.shpytchuk.ingest.source.SourceSettings;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MessageMapperTest {

    private static final long CHAT_ID = -1001234567890L;
    private static final SourceSettings LVIV = new SourceSettings("UA", "Львів", "uk");
    private static final FolderChat CHAT = new FolderChat("Загублено Львів", "zagubleno911", LVIV);
    private static final int DATE = 1_791_000_000;

    @Test
    void mapsTextMessageOfAGroupMember() {
        TdApi.Message message = message(10L << 20, text("Загубив гаманець біля ратуші"));
        message.senderId = new TdApi.MessageSenderUser(777);

        IncomingPost post = map(message).orElseThrow();

        assertThat(post.key()).isEqualTo(new PostKey("telegram", Long.toString(CHAT_ID), Long.toString(10L << 20)));
        assertThat(details(post).chatId()).isEqualTo(CHAT_ID);
        assertThat(details(post).messageId()).isEqualTo(10L << 20);
        assertThat(post.sourceTitle()).isEqualTo("Загублено Львів");
        assertThat(details(post).chatUsername()).isEqualTo("zagubleno911");
        assertThat(post.postedAt()).isEqualTo(Instant.ofEpochSecond(DATE));
        assertThat(post.text()).isEqualTo("Загубив гаманець біля ратуші");
        assertThat(post.photos()).isEmpty();
        assertThat(details(post).authorUserId()).isEqualTo(777L);
        assertThat(post.settings()).isEqualTo(LVIV);
        assertThat(post.link()).isEqualTo("https://t.me/zagubleno911/10");
    }

    @Test
    void skipsOutgoingMessages() {
        TdApi.Message message = message(1, text("наше"));
        message.isOutgoing = true;

        assertThat(map(message)).isEmpty();
    }

    @Test
    void channelPostWithoutForwardHasNoAuthor() {
        TdApi.Message message = message(1, text("Знайдено ключі"));
        message.isChannelPost = true;
        message.senderId = new TdApi.MessageSenderChat(CHAT_ID);

        assertThat(details(map(message).orElseThrow()).authorUserId()).isNull();
    }

    @Test
    void channelPostForwardedFromAPersonHasThatPersonAsAuthor() {
        TdApi.Message message = message(1, text("Знайдено ключі"));
        message.isChannelPost = true;
        message.senderId = new TdApi.MessageSenderChat(CHAT_ID);
        message.forwardInfo = new TdApi.MessageForwardInfo(new TdApi.MessageOriginUser(555), DATE, null, null);

        assertThat(details(map(message).orElseThrow()).authorUserId()).isEqualTo(555L);
    }

    @Test
    void forwardFromAPersonWinsOverTheGroupMemberWhoForwardedIt() {
        TdApi.Message message = message(1, text("Знайдено ключі"));
        message.senderId = new TdApi.MessageSenderUser(777);
        message.forwardInfo = new TdApi.MessageForwardInfo(new TdApi.MessageOriginUser(555), DATE, null, null);

        assertThat(details(map(message).orElseThrow()).authorUserId()).isEqualTo(555L);
    }

    @Test
    void anonymousGroupAdminHasNoAuthor() {
        TdApi.Message message = message(1, text("Знайдено ключі"));
        message.senderId = new TdApi.MessageSenderChat(CHAT_ID);

        assertThat(details(map(message).orElseThrow()).authorUserId()).isNull();
    }

    @Test
    void extractsUsernameMentionsWithoutAtAndWithoutRepeats() {
        String body = "Загублено паспорт. Контакт: @finder_example / Автор: @Finder_Example, ще @other_one";
        TdApi.Message message = message(1, text(body,
                mention(body, "@finder_example"), mention(body, "@Finder_Example"), mention(body, "@other_one")));

        IncomingPost post = map(message).orElseThrow();

        assertThat(details(post).mentionedUsernames()).containsExactly("finder_example", "other_one");
        assertThat(post.text()).isEqualTo(body);
    }

    @Test
    void entityOffsetsCountUtf16UnitsLikeJavaStrings() {
        String body = "🔴 Загублено 🐕 собаку, пишіть @owner_name";
        TdApi.Message message = message(1, text(body, mention(body, "@owner_name")));

        assertThat(details(map(message).orElseThrow()).mentionedUsernames()).containsExactly("owner_name");
    }

    @Test
    void extractsUsersLinkedById() {
        String body = "Пишіть Андрію або Олені";
        TdApi.TextEntity andrii = new TdApi.TextEntity(body.indexOf("Андрію"), "Андрію".length(),
                new TdApi.TextEntityTypeMentionName(101));
        TdApi.TextEntity olena = new TdApi.TextEntity(body.indexOf("Олені"), "Олені".length(),
                new TdApi.TextEntityTypeMentionName(102));
        TdApi.TextEntity bold = new TdApi.TextEntity(0, 6, new TdApi.TextEntityTypeBold());

        IncomingPost post = map(message(1, text(body, andrii, olena, bold))).orElseThrow();

        assertThat(details(post).mentionedUserIds()).containsExactly(101L, 102L);
        assertThat(details(post).mentionedUsernames()).isEmpty();
    }

    @Test
    void photoTakesTheLargestSizeAndTheCaption() {
        TdApi.Message message = message(1, photo(caption("Знайшов телефон"), size(1, 90, 60), size(3, 1280, 853),
                size(2, 800, 533)));

        IncomingPost post = map(message).orElseThrow();

        assertThat(post.text()).isEqualTo("Знайшов телефон");
        assertThat(post.photos()).containsExactly(new PhotoRef("3", 1280, 853));
    }

    @Test
    void photoWithoutCaptionIsAPostWithEmptyText() {
        IncomingPost post = map(message(1, photo(caption(""), size(7, 640, 480)))).orElseThrow();

        assertThat(post.text()).isEmpty();
        assertThat(post.photos()).containsExactly(new PhotoRef("7", 640, 480));
    }

    @Test
    void skipsContentOtherThanTextAndPhoto() {
        assertThat(map(message(1, new TdApi.MessageSticker()))).isEmpty();
    }

    @Test
    void skipsAPhotoWithoutSizesAndWithoutCaption() {
        assertThat(map(message(1, photo(caption(""))))).isEmpty();
    }

    @Test
    void chatWithoutUsernameHasNoLink() {
        FolderChat privateGroup = new FolderChat("Група", null, LVIV);

        IncomingPost post = MessageMapper.map(message(1, text("текст")), privateGroup).orElseThrow();

        assertThat(details(post).chatUsername()).isNull();
        assertThat(post.link()).isNull();
    }

    @Test
    void mergesAnAlbumIntoOnePost() {
        IncomingPost second = post(12, 1_000, "", List.of(new PhotoRef("2", 800, 600)), null, List.of("b"), List.of());
        IncomingPost first = post(11, 1_001, "Знайдено рюкзак", List.of(new PhotoRef("1", 800, 600)), 777L,
                List.of("a"), List.of(5L));
        IncomingPost third = post(13, 1_002, "Знайдено рюкзак", List.of(new PhotoRef("3", 800, 600)), 888L,
                List.of("A", "c"), List.of(5L, 6L));

        IncomingPost album = MessageMapper.merge(List.of(second, third, first));

        assertThat(details(album).messageId()).isEqualTo(11);
        assertThat(album.key().id()).isEqualTo("11");
        assertThat(album.postedAt()).isEqualTo(Instant.ofEpochSecond(1_000));
        assertThat(album.text()).isEqualTo("Знайдено рюкзак");
        assertThat(album.photos()).extracting(PhotoRef::id).containsExactly("1", "2", "3");
        assertThat(details(album).authorUserId()).isEqualTo(777L);
        assertThat(details(album).mentionedUsernames()).containsExactly("a", "b", "c");
        assertThat(details(album).mentionedUserIds()).containsExactly(5L, 6L);
    }

    @Test
    void albumWithCaptionsOnSeveralPartsKeepsEachOnce() {
        IncomingPost first = post(1, 1_000, "Перший", List.of(new PhotoRef("1", 1, 1)), null, List.of(), List.of());
        IncomingPost second = post(2, 1_000, "Другий", List.of(new PhotoRef("2", 1, 1)), null, List.of(), List.of());

        assertThat(MessageMapper.merge(List.of(first, second)).text()).isEqualTo("Перший\n\nДругий");
    }

    @Test
    void mapAllMergesAlbumsAndDropsOutgoingAndOtherContent() {
        TdApi.Message text = message(30, text("третій"));
        TdApi.Message albumPart2 = message(21, photo(caption(""), size(2, 10, 10)));
        albumPart2.mediaAlbumId = 99;
        TdApi.Message albumPart1 = message(20, photo(caption("альбом"), size(1, 10, 10)));
        albumPart1.mediaAlbumId = 99;
        TdApi.Message outgoing = message(15, text("наше"));
        outgoing.isOutgoing = true;
        TdApi.Message sticker = message(12, new TdApi.MessageSticker());

        List<IncomingPost> posts = MessageMapper.mapAll(List.of(text, albumPart2, albumPart1, outgoing, sticker), CHAT);

        assertThat(posts).extracting(MessageMapper::messageId).containsExactlyInAnyOrder(20L, 30L);
        IncomingPost album = posts.stream().filter(post -> MessageMapper.messageId(post) == 20).findFirst().orElseThrow();
        assertThat(album.text()).isEqualTo("альбом");
        assertThat(album.photos()).extracting(PhotoRef::id).containsExactly("1", "2");
    }

    private static Optional<IncomingPost> map(TdApi.Message message) {
        return MessageMapper.map(message, CHAT);
    }

    private static TdApi.Message message(long id, TdApi.MessageContent content) {
        TdApi.Message message = new TdApi.Message();
        message.id = id;
        message.chatId = CHAT_ID;
        message.date = DATE;
        message.content = content;
        return message;
    }

    private static TdApi.MessageText text(String body, TdApi.TextEntity... entities) {
        TdApi.MessageText text = new TdApi.MessageText();
        text.text = new TdApi.FormattedText(body, entities);
        return text;
    }

    private static TdApi.FormattedText caption(String body) {
        return new TdApi.FormattedText(body, new TdApi.TextEntity[0]);
    }

    private static TdApi.MessagePhoto photo(TdApi.FormattedText caption, TdApi.PhotoSize... sizes) {
        TdApi.MessagePhoto photo = new TdApi.MessagePhoto();
        photo.photo = new TdApi.Photo(false, null, sizes);
        photo.caption = caption;
        return photo;
    }

    private static TdApi.PhotoSize size(int fileId, int width, int height) {
        TdApi.File file = new TdApi.File();
        file.id = fileId;
        return new TdApi.PhotoSize("x", file, width, height, new int[0]);
    }

    private static TdApi.TextEntity mention(String body, String mention) {
        return new TdApi.TextEntity(body.indexOf(mention), mention.length(), new TdApi.TextEntityTypeMention());
    }

    private static IncomingPost post(long messageId, long epochSecond, String text, List<PhotoRef> photos, Long author,
                                     List<String> usernames, List<Long> userIds) {
        return new IncomingPost(new PostKey("telegram", Long.toString(CHAT_ID), Long.toString(messageId)), "Канал",
                null, Instant.ofEpochSecond(epochSecond), text, photos, LVIV,
                new TelegramDetails(CHAT_ID, messageId, "chan", author, usernames, userIds));
    }

    private static TelegramDetails details(IncomingPost post) {
        return (TelegramDetails) post.details();
    }
}
