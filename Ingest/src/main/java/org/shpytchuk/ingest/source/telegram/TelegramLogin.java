package org.shpytchuk.ingest.source.telegram;

import it.tdlight.client.AuthenticationSupplier;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.client.SimpleTelegramClientBuilder;
import it.tdlight.client.SimpleTelegramClientFactory;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.shpytchuk.ingest.config.telegram.TelegramProperties;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.springframework.core.NestedExceptionUtils.getMostSpecificCause;

/**
 * The one-off interactive login: {@code docker compose run --rm -it ingest --login}.
 * Asks for the code from Telegram and the 2FA password on the console (TDLight's default
 * interaction), saves the session to {@code TDLIB_DATA_DIR} and exits. With a saved session it
 * only prints whose session it is.
 *
 * <p>Starts a minimal Spring context that binds {@link TelegramProperties} from the same
 * {@code application.yaml} and environment as the service, without auto-configuration: no
 * database, no web server, no TDLib listener.
 */
@Slf4j
public final class TelegramLogin {

    public static final String LOGIN_ARG = "--login";

    private static final long LOGIN_TIMEOUT_MINUTES = 10;

    private TelegramLogin() {
    }

    public static void run(String[] args) {
        int status = 1;
        try {
            login(properties(args));
            status = 0;
        } catch (LoginException e) {
            log.error("Telegram login failed: {}", e.getMessage());
        } catch (TimeoutException e) {
            log.error("Telegram login failed: no login within {} minutes", LOGIN_TIMEOUT_MINUTES);
        } catch (Exception e) {
            log.error("Telegram login failed: {}", getMostSpecificCause(e).toString());
        }
        System.exit(status);
    }

    private static TelegramProperties properties(String[] args) {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(LoginConfig.class)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF)
                .logStartupInfo(false)
                .run(args)) {
            return context.getBean(TelegramProperties.class);
        }
    }

    private static void login(TelegramProperties properties) throws Exception {
        if (!properties.configured()) {
            throw new LoginException("TELEGRAM_API_ID and TELEGRAM_API_HASH must be set (my.telegram.org)");
        }
        if (properties.phone().isEmpty()) {
            throw new LoginException("TELEGRAM_PHONE must be set to the phone number of the ingest account");
        }
        if (!TdlightTelegramSource.loadNatives()) {
            throw new LoginException("TDLib native library is not available on this platform");
        }

        try (SimpleTelegramClientFactory factory = new SimpleTelegramClientFactory()) {
            SimpleTelegramClientBuilder builder = factory.builder(TdlightTelegramSource.tdlibSettings(properties));
            builder.addDefaultExceptionHandler(e ->
                    log.warn("Telegram answered with an error: {}", getMostSpecificCause(e).toString()));
            SimpleTelegramClient client = builder.build(AuthenticationSupplier.user(properties.phone()));
            try {
                CompletableFuture<TdApi.User> me = client.getMeAsync();
                client.waitForExitAsync().thenRun(() ->
                        me.completeExceptionally(new LoginException("Telegram closed the session")));
                TdApi.User user = me.get(LOGIN_TIMEOUT_MINUTES, TimeUnit.MINUTES);
                log.info("Telegram session of user {} saved in {}", user.id, properties.dataDir());
            } finally {
                client.closeAndWait();
            }
        }
    }

    @EnableConfigurationProperties(TelegramProperties.class)
    static class LoginConfig {
    }

    private static final class LoginException extends RuntimeException {

        LoginException(String message) {
            super(message);
        }
    }
}
