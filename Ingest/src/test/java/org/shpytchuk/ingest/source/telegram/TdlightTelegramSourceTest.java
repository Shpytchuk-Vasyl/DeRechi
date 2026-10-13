package org.shpytchuk.ingest.source.telegram;

import it.tdlight.jni.TdApi;
import org.junit.jupiter.api.Test;
import org.shpytchuk.ingest.config.TelegramProperties;
import org.shpytchuk.ingest.exception.StageUnavailableException;
import org.shpytchuk.ingest.source.IncomingPost;
import org.shpytchuk.ingest.source.PhotoRef;
import org.springframework.scheduling.TaskScheduler;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class TdlightTelegramSourceTest {

    private final TaskScheduler scheduler = mock(TaskScheduler.class);

    @Test
    void withoutApiCredentialsStartDoesNothingAndCallsAreUnavailable() {
        TdlightTelegramSource source = source(properties(0, ""));
        List<IncomingPost> received = new ArrayList<>();

        source.start(received::add);

        assertThat(source.authorized()).isFalse();
        assertThatThrownBy(() -> source.recentPosts(Duration.ofHours(1), 10)).isInstanceOf(StageUnavailableException.class);
        assertThatThrownBy(() -> source.userPhone(1)).isInstanceOf(StageUnavailableException.class);
        assertThatThrownBy(() -> source.userIdByUsername("someone")).isInstanceOf(StageUnavailableException.class);
        assertThatThrownBy(() -> source.download(new PhotoRef("1", 1, 1))).isInstanceOf(StageUnavailableException.class);
        source.close();
        assertThat(received).isEmpty();
        verifyNoInteractions(scheduler);
    }

    @Test
    void configuredButNotStartedIsUnavailable() {
        TdlightTelegramSource source = source(properties(12345, "hash"));

        assertThat(source.authorized()).isFalse();
        assertThatThrownBy(() -> source.userPhone(1)).isInstanceOf(StageUnavailableException.class);
    }

    @Test
    void findsTheFolderByNameIgnoringCaseAndSpaces() {
        TdApi.ChatFolderInfo[] folders = {folder(1, "Work"), folder(7, " derechi "), folder(9, "DeRechi 2")};

        assertThat(TdlightTelegramSource.findFolder(folders, "DeRechi")).hasValueSatisfying(f -> assertThat(f.id).isEqualTo(7));
        assertThat(TdlightTelegramSource.findFolder(folders, "Lost")).isEmpty();
        assertThat(TdlightTelegramSource.findFolder(new TdApi.ChatFolderInfo[0], "DeRechi")).isEmpty();
    }

    @Test
    void sessionAndDownloadsLiveUnderTheDataDir() {
        var settings = TdlightTelegramSource.tdlibSettings(properties(12345, "hash"));

        assertThat(settings.getDatabaseDirectoryPath()).endsWithRaw(Path.of("tdlib-test", "db"));
        assertThat(settings.getDownloadedFilesDirectoryPath()).endsWithRaw(Path.of("tdlib-test", "files"));
    }

    private TdlightTelegramSource source(TelegramProperties properties) {
        return new TdlightTelegramSource(properties, scheduler, Clock.systemUTC());
    }

    private static TdApi.ChatFolderInfo folder(int id, String name) {
        TdApi.ChatFolderInfo info = new TdApi.ChatFolderInfo();
        info.id = id;
        info.name = new TdApi.ChatFolderName(new TdApi.FormattedText(name, new TdApi.TextEntity[0]), false);
        return info;
    }

    private static TelegramProperties properties(int apiId, String apiHash) {
        return new TelegramProperties(apiId, apiHash, "", "tdlib-test", "DeRechi", "", "UA", "uk",
                Duration.ofSeconds(3), Duration.ofMinutes(10), Duration.ofHours(48), 200);
    }
}
