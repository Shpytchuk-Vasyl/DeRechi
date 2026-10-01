# Add a notification channel

Turning on another delivery channel in the `Notification` module. The module itself owns no transport; everything goes through NotifyHub (`io.github.gabrielbbaldez:notify-spring-boot-starter`, version in the root `pom.xml`). Today only email is live.

## Before you start

Read [Match notifications](../features/match-notifications.md), in particular the known gaps: the consumer sends email when `email` is present and SMS when `phone` is present, regardless of which `NotifyChannel` the admin picked, and the SMS channel is not actually on the classpath. Any new channel inherits that until the event carries a channel flag.

## How NotifyHub enables a channel

Each channel has an auto-configuration guarded by two conditions:

1. `@ConditionalOnClass` on the channel implementation, so the channel's Maven artifact must be a dependency (`notify-email` is the only one today);
2. `@ConditionalOnProperty` on one key under `notify.channels.<name>`. The key only has to **exist**; an empty value counts as present, and an incomplete configuration then fails at startup. This is why unused channels are commented out in `application.yaml` rather than left blank.

The keys that switch channels on (from the starter's `Notify*AutoConfiguration` classes, version 1.1.0):

| Channel | Switch property | Other properties |
|---|---|---|
| email | `notify.channels.email.host` | `port`, `username`, `password`, `from`, `from-name`, `tls` |
| telegram | `notify.channels.telegram.bot-token` | `chat-id` (default), `recipients` (map name to chat id) |
| sms (Twilio) | `notify.channels.sms.account-sid` | `auth-token`, `from-number`; also needs `com.twilio.Twilio` on the classpath |
| whatsapp (Twilio) | `notify.channels.whatsapp.account-sid` | `auth-token`, `from-number` |

Channel artifacts share the starter's groupId and are named `notify-<channel>`; `notify-email` and `notify-telegram` are already in our Maven cache, check Maven Central for the exact artifact of any other channel. Slack, Teams, Discord, push, webhooks, Google Chat, SendGrid, Mailgun and more follow the same pattern; open `NotifyProperties.Channels` in the starter's sources jar for the full list (`find ~/.m2 -name "notify-spring-boot-starter-*-sources.jar"`).

`NotifyHub.getRegisteredChannels()` reports names in lower case with dashes (`google-chat`), not `Channel.name()`. Compare against those if you check registration at runtime.

## Steps (example: Telegram)

1. **Dependency.** `Notification/pom.xml`:

   ```xml
   <dependency>
       <groupId>io.github.gabrielbbaldez</groupId>
       <artifactId>notify-telegram</artifactId>
   </dependency>
   ```

   If the root `<dependencyManagement>` does not pin this artifact yet, add it there next to `notify-email`; module POMs carry no versions.

2. **Configuration.** `Notification/src/main/resources/application.yaml`:

   ```yaml
   notify:
     channels:
       email:
         host: ${NOTIFY_EMAIL_HOST:localhost}
         # ...
       telegram:
         bot-token: ${NOTIFY_TELEGRAM_BOT_TOKEN}
   ```

   No default value for the token: with `${NOTIFY_TELEGRAM_BOT_TOKEN:}` the key is present and empty, the channel registers, and the first send fails. Either the variable is set or the whole block stays commented out.

3. **Environment.** `docker-compose.services.yml`, service `notification`, next to `NOTIFY_EMAIL_HOST`: add `NOTIFY_TELEGRAM_BOT_TOKEN: ${NOTIFY_TELEGRAM_BOT_TOKEN}` so the secret comes from the host environment, not from git. Document it in [Environment variables](../deployment/environment-variables.md).

4. **Sender.** `Notification/src/main/java/org/shpytchuk/notification/service/NotificationSender.java` has one `sendVia(Channel, event)` call per channel. The recipient is a builder field, not a channel argument: `to(...)` sets `recipientEmail`, which email, telegram, slack and teams read; `toPhone(...)` sets `recipientPhone` for sms and whatsapp. `NotificationRequestedEvent.getRecipient(channel)` already returns the email for `EMAIL` and the phone for everything else, but `sendVia` passes the result to `to(...)` for every channel, so for SMS the phone currently lands in `recipientEmail` and the SMS channel would see no recipient. Branch on the channel: `to(email)` for email-like channels, `toPhone(phone)` for sms and whatsapp, and the chat id for telegram.

   For Telegram the recipient is a **chat id**, which `ContactInfo` does not store. Shipping Telegram for real means: a `telegram_chat_id` column (migration in `DB-Postgres`, entity copies in all modules), a field in `ContactInfoInput` and `ItemForm`, a way for the user to obtain it (a bot `/start` flow), and the id in `NotificationRequestedEvent`. Without that, the channel can only reach the configured default `chat-id`, which is good for an ops alert and useless for owners.

5. **Honour the admin's choice.** The commented-out `targets(event)` in `NotificationSender` is the sketch: iterate `event.socialMedias()`, map each to a `Channel` through `SocialMediaEnum`, skip channels that are not in `getRegisteredChannels()`, and send to the rest. Pair it with a rule for when `phone` or `email` are present so that choosing Telegram no longer triggers SMS. This is the change that makes `NotifyChannel` on the admin side meaningful.

6. **Dedup key.** `deduplicationKey(event, channel)` is `<recipient>:<channel>`; a new channel gets its own key automatically. The TTL is `notify.deduplication.ttl` (1 h).

7. **Failure path.** Keep the behaviour: a channel without configuration or without a recipient is skipped; if every attempted channel fails, the exception propagates, Spring AMQP retries 3 times and the message goes to `notification.events.dlq`.

## Tests to add or update

`NotificationSenderTest` drives `NotificationSender` with a recording `NotifyHub` test double (`hub.sent("email")`). Add cases: the new channel receives the message with the right recipient, an event without that recipient does not touch the channel, and the chosen-channel rule from step 5. For the real transport, test manually against a sandbox bot or Twilio test credentials; do not add network tests to the build.

## Migration needed?

Only if the channel needs a recipient we do not store (Telegram chat id). Twilio SMS and WhatsApp address by phone, which we have.

## Web-Client impact

None for the channel itself. If a new contact field appears (chat id), the web team adds it to the notice form after `pnpm codegen`.

## Checklist

- [ ] Channel artifact in `Notification/pom.xml`, version managed in the root
- [ ] `notify.channels.<name>.<switch-key>` without an empty default
- [ ] Secret wired through `docker-compose.services.yml` and documented
- [ ] `NotificationSender.sendVia` uses the right recipient method for the channel
- [ ] Admin's channel choice respected, SMS no longer implied by a phone
- [ ] `NotificationSenderTest` extended
- [ ] Manual send verified (Mailpit for email, sandbox for the rest)
