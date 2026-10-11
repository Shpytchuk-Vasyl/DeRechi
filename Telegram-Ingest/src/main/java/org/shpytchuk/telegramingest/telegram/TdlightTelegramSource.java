package org.shpytchuk.telegramingest.telegram;

import it.tdlight.Init;
import it.tdlight.client.APIToken;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.client.SimpleTelegramClientBuilder;
import it.tdlight.client.SimpleTelegramClientFactory;
import it.tdlight.client.TDLibSettings;
import it.tdlight.client.TelegramError;
import it.tdlight.jni.TdApi;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.shpytchuk.telegramingest.config.TelegramProperties;
import org.shpytchuk.telegramingest.exception.IngestException;
import org.shpytchuk.telegramingest.exception.StageUnavailableException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.LongStream;

import static org.springframework.core.NestedExceptionUtils.getMostSpecificCause;

@Component
@Slf4j
public class TdlightTelegramSource implements TelegramSource {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(30);
    private static final int HISTORY_PAGE = 20;
    private static final int DOWNLOAD_PRIORITY = 32;
    private static final String DEVICE_MODEL = "DeRechi Telegram-Ingest";
    private static final String APPLICATION_VERSION = "1.0";
    private static final Comparator<IncomingPost> OLDEST_FIRST =
            Comparator.comparing(IncomingPost::postedAt).thenComparingLong(IncomingPost::messageId);

    private final TelegramProperties properties;
    private final TaskScheduler scheduler;
    private final Clock clock;
    private final AlbumBuffer albums;
    private final AtomicBoolean authorized = new AtomicBoolean();

    private volatile Consumer<IncomingPost> sink = post -> {
    };
    private volatile @Nullable SimpleTelegramClientFactory factory;
    private volatile @Nullable SimpleTelegramClient client;
    private volatile boolean closing;
    private volatile TdApi.ChatFolderInfo @Nullable [] folders;
    private volatile Map<Long, FolderChat> chats = Map.of();

    private boolean folderResolved;
    private boolean folderMissingLogged;

    public TdlightTelegramSource(TelegramProperties properties, TaskScheduler scheduler, Clock clock) {
        this.properties = properties;
        this.scheduler = scheduler;
        this.clock = clock;
        this.albums = new AlbumBuffer(properties.albumWait(), scheduler, post -> sink.accept(post));
    }

    @Override
    public void start(Consumer<IncomingPost> sink) {
        if (!properties.configured()) {
            log.warn("Telegram is not configured (TELEGRAM_API_ID, TELEGRAM_API_HASH), no posts will be read");
            return;
        }
        if (!loadNatives()) {
            return;
        }
        this.sink = sink;
        SimpleTelegramClientFactory clientFactory = new SimpleTelegramClientFactory();
        SimpleTelegramClientBuilder builder = clientFactory.builder(tdlibSettings(properties));
        builder.setClientInteraction((parameter, info) -> refuseLogin());
        builder.addUpdateHandler(TdApi.UpdateAuthorizationState.class, this::onAuthorizationState);
        builder.addUpdateHandler(TdApi.UpdateChatFolders.class, this::onChatFolders);
        builder.addUpdateHandler(TdApi.UpdateNewMessage.class, this::onNewMessage);
        builder.addUpdateExceptionHandler(e -> log.error("Telegram update handler failed", e));
        builder.addDefaultExceptionHandler(this::onClientException);
        SimpleTelegramClient started = builder.build(this::refuseLogin);
        factory = clientFactory;
        client = started;

        started.getMeAsync().thenAccept(me -> {
            log.info("Telegram session authorized as user {}", me.id);
            refreshFolderSoon();
        });
        scheduler.scheduleWithFixedDelay(this::refreshFolder, properties.folderRefresh());
        log.info("Telegram client started, reading folder {}", properties.folder());
    }

    @Override
    public List<IncomingPost> recentPosts(Duration maxAge, int maxMessages) {
        requireReady();
        Instant cutoff = clock.instant().minus(maxAge);
        List<IncomingPost> posts = new ArrayList<>();
        chats.forEach((chatId, chat) -> {
            try {
                posts.addAll(MessageMapper.mapAll(history(chatId, cutoff, maxMessages), chat));
            } catch (IngestException e) {
                log.warn("History of chat {} was not read: {}", chatId, e.getMessage());
            }
        });
        posts.sort(OLDEST_FIRST);
        return posts;
    }

    @Override
    public Optional<String> userPhone(long userId) {
        requireReady();
        String phone = call(new TdApi.GetUser(userId)).phoneNumber;
        return phone.isEmpty() ? Optional.empty() : Optional.of(phone);
    }

    @Override
    public Optional<Long> userIdByUsername(String username) {
        requireReady();
        TdApi.Chat chat;
        try {
            chat = call(new TdApi.SearchPublicChat(username));
        } catch (IngestException e) {
            if (e.getCause() instanceof TelegramError error && isUnknownUsername(error)) {
                return Optional.empty();
            }
            throw e;
        }
        if (!(chat.type instanceof TdApi.ChatTypePrivate personal)) {
            return Optional.empty();
        }
        TdApi.User user = call(new TdApi.GetUser(personal.userId));
        return user.type instanceof TdApi.UserTypeBot ? Optional.empty() : Optional.of(personal.userId);
    }

    @Override
    public byte[] download(PhotoRef photo) {
        SimpleTelegramClient ready = requireReady();
        TdApi.File file = call(new TdApi.DownloadFile(photo.fileId(), DOWNLOAD_PRIORITY, 0, 0, true));
        try {
            if (!file.local.isDownloadingCompleted) {
                throw new IngestException("Telegram file " + photo.fileId() + " was not downloaded");
            }
            return Files.readAllBytes(Path.of(file.local.path));
        } catch (IOException e) {
            throw new IngestException("Telegram file " + photo.fileId() + " could not be read", e);
        } finally {
            ready.send(new TdApi.DeleteFile(photo.fileId()));
        }
    }

    @Override
    public boolean authorized() {
        return authorized.get();
    }

    @PreDestroy
    public void close() {
        closing = true;
        authorized.set(false);
        SimpleTelegramClient current = client;
        SimpleTelegramClientFactory currentFactory = factory;
        if (current == null || currentFactory == null) {
            return;
        }
        try {
            current.close();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Telegram client did not close cleanly: {}", getMostSpecificCause(e).toString());
        }
        currentFactory.close();
    }

    static TDLibSettings tdlibSettings(TelegramProperties properties) {
        TDLibSettings settings = TDLibSettings.create(new APIToken(properties.apiId(), properties.apiHash()));
        Path dataDir = Path.of(properties.dataDir());
        settings.setDatabaseDirectoryPath(dataDir.resolve("db"));
        settings.setDownloadedFilesDirectoryPath(dataDir.resolve("files"));
        settings.setDeviceModel(DEVICE_MODEL);
        settings.setApplicationVersion(APPLICATION_VERSION);
        settings.setSystemLanguageCode(properties.defaultLocale());
        return settings;
    }

    static boolean loadNatives() {
        try {
            Init.init();
            return true;
        } catch (Exception | LinkageError e) {
            log.error("TDLib native library could not be loaded: {}", getMostSpecificCause(e).toString());
            return false;
        }
    }

    static Optional<TdApi.ChatFolderInfo> findFolder(TdApi.ChatFolderInfo[] folders, String name) {
        return Arrays.stream(folders)
                .filter(folder -> folder.name.text.text.trim().equalsIgnoreCase(name))
                .findFirst();
    }

    // --- updates, on the TDLib thread: never wait for a request here ---

    private void onAuthorizationState(TdApi.UpdateAuthorizationState update) {
        boolean ready = update.authorizationState instanceof TdApi.AuthorizationStateReady;
        if (authorized.getAndSet(ready) && !ready && !closing) {
            log.warn("Telegram session is no longer authorized: {}", update.authorizationState.getClass().getSimpleName());
        }
    }

    private void onChatFolders(TdApi.UpdateChatFolders update) {
        folders = update.chatFolders;
        refreshFolderSoon();
    }

    private void onNewMessage(TdApi.UpdateNewMessage update) {
        TdApi.Message message = update.message;
        FolderChat chat = chats.get(message.chatId);
        if (chat != null) {
            MessageMapper.map(message, chat).ifPresent(post -> albums.add(message.mediaAlbumId, post));
        }
    }

    private void onClientException(Throwable e) {
        if (!(e instanceof SessionNotAuthorizedException) && !closing) {
            log.warn("Telegram client error: {}", getMostSpecificCause(e).toString());
        }
    }

    private <T> CompletableFuture<T> refuseLogin() {
        log.error("Telegram session is not authorized, run the container with --login");
        return CompletableFuture.failedFuture(new SessionNotAuthorizedException());
    }

    // --- folder work, on the scheduler ---

    private void refreshFolderSoon() {
        scheduler.schedule(this::refreshFolder, clock.instant());
    }

    /** Re-reads the chats of the folder; the first success starts the catch-up. */
    private synchronized void refreshFolder() {
        TdApi.ChatFolderInfo[] known = folders;
        if (known == null || client == null || !authorized.get() || closing) {
            return;
        }
        Optional<TdApi.ChatFolderInfo> folder = findFolder(known, properties.folder());
        if (folder.isEmpty()) {
            if (!folderMissingLogged) {
                log.warn("Telegram folder {} not found among {} folders, no chats are read", properties.folder(),
                        known.length);
                folderMissingLogged = true;
            }
            chats = Map.of();
            return;
        }
        folderMissingLogged = false;

        TdApi.ChatFolder content;
        Map<Long, FolderChat> resolved;
        try {
            content = call(new TdApi.GetChatFolder(folder.get().id));
            resolved = resolveChats(content);
        } catch (IngestException e) {
            log.warn("Telegram folder {} was not refreshed: {}", properties.folder(), e.getMessage());
            return;
        }
        if (!folderResolved || !resolved.keySet().equals(chats.keySet())) {
            log.info("Telegram folder {} resolved: {} chats", properties.folder(), resolved.size());
            if (content.includeChannels || content.includeGroups || content.includeContacts
                    || content.includeNonContacts || content.includeBots) {
                log.warn("Telegram folder {} includes chats by type; only chats added to it one by one are read",
                        properties.folder());
            }
        }
        chats = resolved;
        if (!folderResolved) {
            folderResolved = true;
            Thread.ofVirtual().name("telegram-catch-up").start(this::catchUp);
        }
    }

    private Map<Long, FolderChat> resolveChats(TdApi.ChatFolder folder) {
        Map<Long, FolderChat> resolved = new HashMap<>();
        for (long chatId : LongStream.concat(Arrays.stream(folder.pinnedChatIds), Arrays.stream(folder.includedChatIds)).toArray()) {
            try {
                folderChat(chatId).ifPresent(chat -> resolved.put(chatId, chat));
            } catch (IngestException e) {
                log.warn("Telegram chat {} of folder {} skipped: {}", chatId, properties.folder(), e.getMessage());
            }
        }
        return Map.copyOf(resolved);
    }

    private Optional<FolderChat> folderChat(long chatId) {
        TdApi.Chat chat = call(new TdApi.GetChat(chatId));
        String username;
        switch (chat.type) {
            case TdApi.ChatTypeSupergroup supergroup -> {
                TdApi.Usernames usernames = call(new TdApi.GetSupergroup(supergroup.supergroupId)).usernames;
                username = usernames == null || usernames.activeUsernames.length == 0 ? null : usernames.activeUsernames[0];
            }
            case TdApi.ChatTypeBasicGroup _ -> username = null;
            default -> {
                return Optional.empty();
            }
        }
        return Optional.of(new FolderChat(chat.title, username, properties.settingsFor(username, chatId)));
    }

    private void catchUp() {
        try {
            List<IncomingPost> posts = recentPosts(properties.catchUpMaxAge(), properties.catchUpMaxMessages());
            posts.forEach(sink);
            log.info("Telegram catch-up replayed {} posts of {} chats", posts.size(), chats.size());
        } catch (RuntimeException e) {
            log.warn("Telegram catch-up failed: {}", getMostSpecificCause(e).toString());
        }
    }

    private List<TdApi.Message> history(long chatId, Instant cutoff, int maxMessages) {
        List<TdApi.Message> messages = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        long fromMessageId = 0;
        while (messages.size() < maxMessages) {
            int limit = Math.min(HISTORY_PAGE, maxMessages - messages.size());
            TdApi.Messages page = call(new TdApi.GetChatHistory(chatId, fromMessageId, 0, limit, false));
            List<TdApi.Message> fresh = Arrays.stream(page.messages).filter(message -> seen.add(message.id)).toList();
            if (fresh.isEmpty()) {
                break;
            }
            for (TdApi.Message message : fresh) {
                if (Instant.ofEpochSecond(message.date).isBefore(cutoff) || messages.size() == maxMessages) {
                    return messages;
                }
                messages.add(message);
            }
            fromMessageId = fresh.getLast().id;
        }
        return messages;
    }

    // --- plumbing ---

    private <R extends TdApi.Object> R call(TdApi.Function<R> function) {
        SimpleTelegramClient current = client;
        if (current == null) {
            throw new StageUnavailableException("Telegram client is not running");
        }
        String name = function.getClass().getSimpleName();
        try {
            return current.send(function).get(CALL_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IngestException("Telegram " + name + " was interrupted", e);
        } catch (TimeoutException e) {
            throw new IngestException("Telegram " + name + " timed out after " + CALL_TIMEOUT.toSeconds() + " s", e);
        } catch (ExecutionException e) {
            String reason = e.getCause() instanceof TelegramError error
                    ? error.getErrorCode() + " " + error.getErrorMessage()
                    : getMostSpecificCause(e).getClass().getSimpleName();
            throw new IngestException("Telegram " + name + " failed: " + reason, e.getCause());
        }
    }

    private SimpleTelegramClient requireReady() {
        SimpleTelegramClient current = client;
        if (current == null) {
            throw new StageUnavailableException("Telegram client is not running");
        }
        if (!authorized.get()) {
            throw new StageUnavailableException("Telegram session is not authorized");
        }
        return current;
    }

    private static boolean isUnknownUsername(TelegramError error) {
        String message = error.getErrorMessage();
        return error.getErrorCode() == 400
                && (message.contains("USERNAME_NOT_OCCUPIED") || message.contains("USERNAME_INVALID"));
    }

    private static final class SessionNotAuthorizedException extends RuntimeException {

        SessionNotAuthorizedException() {
            super("Telegram session is not authorized", null, false, false);
        }
    }
}
