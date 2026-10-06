# Notification

A background worker that turns `NotificationRequestedEvent`s into emails, SMS and, in the
future, messenger messages. Delivery is done by NotifyHub, and this module is the glue between
the queue and that library. The one channel it owns is SMS (`SmsFlyChannel`, see below).

| | |
|---|---|
| Port | 8084 (serves nothing public); actuator on the management port 9084 |
| Consumes | queue `notification.events`, bound to `derechi.notifications` with `notification.#` |
| Needs | RabbitMQ, an SMTP server (Mailpit in development), an SMS-fly key for SMS (optional). No database. |
| Stack | Spring AMQP, `notify-spring-boot-starter` + `notify-email` (NotifyHub 1.1.0) |

## NotifyHub

`io.github.gabrielbbaldez:notify-spring-boot-starter` gives one fluent API
(`notify.to(...).via(channel).subject(...).content(...).send()`) over email, SMS, Telegram,
WhatsApp, Slack, Teams and others, each as a separate artifact. The version is managed in
the root pom; `notify-email` is the only channel artifact on this module's classpath today
(`notify-telegram` is version-managed but not added).

The library targets Spring Boot 3, and it runs on 4.1.1 anyway: its actuator beans are
behind `@ConditionalOnClass` on `HealthIndicator`, which moved packages in Boot 4, so those
beans never load and nothing else in it cares about the Boot version. If a future NotifyHub
release ships a Boot 4 build, prefer it.

## SMS: SMS-fly

NotifyHub's own SMS channel is Twilio-only, so SMS goes through `channel/SmsFlyChannel`, a
`NotificationChannel` bean named `sms`. NotifyHub collects every `NotificationChannel` bean, so
`via(Channel.SMS)` in the sender lands there unchanged, and the recipient is the phone from
`toPhone(...)`.

- API: SMS-fly REST v2.4, one JSON `POST` with `action: SENDMESSAGE`, `channels: ["sms"]`, the key
  in `auth.key`. The number goes without `+` (`380501234567`); the channel strips everything but
  digits. The message text is the event's `message`, the subject is not used. The answer is read
  as a string and parsed by hand: its content type is not documented, and JSON served as
  `text/html` would otherwise fail every send.
- Configuration: `derechi.sms-fly.*` (`SmsFlyProperties`): `api-key`, `api-url`, `source` (the
  approved alpha-name), `ttl` (1m to 24h, sent in minutes), timeouts. `SmsFlyConfig` registers the
  channel only for a **non-blank** key, so unlike NotifyHub's channels an empty `SMS_FLY_API_KEY`
  simply switches SMS off and the sender skips it with a warning.
- Failures: `success: 0` (`INVSOURCE`, `INVRECIPIENT`, ...), an SMS status other than `ACCEPTD`
  (`INSUFFICIENTFUNDS`, ...), an HTTP error or a timeout throw `NotificationSendException`, so the
  usual retry and dead-letter path applies. A timed-out request may still have been sent, and the
  retry then sends a second, paid SMS.
- `ACCEPTD` means accepted, not delivered. Delivery status (`GETMESSAGESTATUS`) is not polled; the
  message id and cost are only logged.
- Cyrillic SMS are UCS-2, 70 characters per segment (67 when split), and every segment is billed.
  The claim texts from `Worker` are 250 to 350 characters, so one SMS costs 4 to 6 segments.

A channel is enabled by the **presence** of its key under `notify.channels.*`: `email` by
`host`, `telegram` by `bot-token`, and so on. An empty value still counts as present and
fails startup, which is why an unused channel is commented out rather than left blank.

```yaml
notify:
  deduplication: { enabled: true, ttl: 1h }
  retry: { strategy: exponential, max-attempts: 3 }
  tracking: { enabled: true }
  channels:
    email:
      host: ${NOTIFY_EMAIL_HOST:localhost}
      port: ${NOTIFY_EMAIL_PORT:1025}
      username: ${NOTIFY_EMAIL_USERNAME:}
      password: ${NOTIFY_EMAIL_PASSWORD:}
      from: ${NOTIFY_EMAIL_FROM:no-reply@derechi.local}
      from-name: DeRechi
      tls: false
```

Two NotifyHub details that shaped the code:

- The recipient is a builder field, not a channel argument. `to(...)` fills
  `recipientEmail`, which email, Telegram, Slack and Teams read; `toPhone(...)` fills
  `recipientPhone` for SMS and WhatsApp. One `send()` therefore addresses one channel, and
  the sender loops.
- `getRegisteredChannels()` returns lower-case, hyphenated names (`google-chat`), not
  `Channel.name()`. Compare against those when checking what is configured.

## The sender

`NotificationRequestedListener` reads the event off the queue and calls
`NotificationSender.send(event)`. Any exception becomes an
`AmqpRejectAndDontRequeueException`: NotifyHub has already retried internally, so Spring
AMQP's own retry would only triple the attempts; the message goes to
`notification.events.dlq`.

`NotificationSender`:

1. if `event.email()` is not null, send via `Channel.EMAIL` to the email;
2. if `event.phone()` is not null, send via `Channel.SMS` to the phone;
3. a channel that is not registered in NotifyHub (`getRegisteredChannels()`, lower-case
   hyphenated names such as `sms`, `google-chat`) is skipped with a warning, so an event with a
   phone does not dead-letter while `SMS_FLY_API_KEY` is empty;
4. a NotifyHub duplicate is treated as sent.

Each send carries `deduplicationKey = "<event.deduplicationKey>:<CHANNEL>"`
(`match:<lostId>:<foundId>` from `Admin-API`, `claim:<kind>:<id>` from `Worker`), one
per channel so a dropped SMS cannot also drop the email. An event without a key falls back to
`<recipient>:<CHANNEL>`, which only stops exact replays. The keys are kept by NotifyHub's
`InMemoryDeduplicationStore` for an hour: per instance and lost on restart, so a message
redelivered after a restart, or to a second `Notification` instance, is sent again.

The `socialMedias` array in the event is **ignored**. `NotificationRequestedEvent.SocialMediaEnum`
maps `TELEGRAM` to `Channel.TELEGRAM`, `WHATSAPP` to `Channel.WHATSAPP` and `VIBER` to
`Channel.SMS` (no Viber channel exists), and a `targets(...)` method that fanned out to the
configured ones is commented out in the sender. It was disabled because Telegram needs a
chat id, which `ContactInfo` does not have, and the event only carries a phone number.
Until that is solved, choosing a messenger in the admin panel results in an SMS, since
`Admin-API` still sends the phone for messenger channels. See
[../../features/match-notifications.md](../../features/match-notifications.md) and
[../../extending/add-a-notification-channel.md](../../extending/add-a-notification-channel.md).

## Event contract

`NotificationRequestedEvent` is a record `(subject, message, phone, email, socialMedias,
deduplicationKey)` with `@EventType("NOTIFICATION")`. The producers' copies in `Admin-API`
and `Worker` have the same shape; the type id is what ties them together, see
[../messaging.md](../messaging.md). Subject and body arrive already localized, so this
module has no message bundles.

## In Docker

`NOTIFY_EMAIL_HOST=mailpit`; the container waits for `rabbitmq` and `mailpit` to be
healthy. Sent mail shows up at `http://localhost:8025`. SMS has no local sandbox: with
`SMS_FLY_API_KEY` set, messages go out for real and are billed.

## Tests

`NotificationSenderTest` uses NotifyHub's `TestNotifyHub` (an in-memory hub from
`io.notifyhub.core.testing`) to assert that an event with both contacts produces one email
and one SMS with the message as content, that unconfigured channels are skipped and that the
dedup key is per event. `NotificationRequestedListenerTest` checks that a failed delivery is
rejected without requeue, `RabbitConfigTest` that a message labelled `NOTIFICATION` from another
module is read into this module's record. `SmsFlyChannelTest` checks the SMS-fly request and its
error handling against `MockRestServiceServer`, `SmsFlyConfigTest` that the channel registers only
with a non-blank key, `NotificationSenderSmsFlyTest` that the real NotifyHub core routes
`Channel.SMS` to it and lets its failure through, `SmsFlyRegistrationTests` that the Spring context
registers it as `sms`. `NotificationApplicationTests` loads the context.

```bash
docker compose up -d rabbitmq mailpit
./mvnw -pl Notification spring-boot:run
```
