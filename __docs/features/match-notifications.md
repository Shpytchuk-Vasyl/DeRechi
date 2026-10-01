# Match notifications

Staff review the candidate pairs produced by [Automatic matching](automatic-matching.md) on the Matches page of the admin panel and, when a candidate looks right, notify the owner of the lost item. The notification is an event on RabbitMQ consumed by the `Notification` module, which sends the actual email or message through NotifyHub.

## The Matches page

`GET /admin/matches` (`MatchController`, permission `MATCH:VIEW`) lists lost items with the same filters as the other list pages. Each row shows up to `MatchService.PREVIEW_SIZE` (3) candidate found items ordered by `match_order` descending, plus the total count. A "show all" button loads the rest with `hx-get /admin/matches/{lostItemId}/candidates`, which returns the `fragments/candidates :: cell` fragment and replaces the whole cell. The reasons for this swap strategy are in [Admin UI conventions](../conventions/admin-ui.md).

Each candidate row is its own fragment, `fragments/candidates :: row(lost, candidate)`, with id `candidate-{lostId}-{foundId}`. The row carries the "Notify" split button:

- the main button submits `channel=ALL`;
- the dropdown offers `EMAIL`, `PHONE` and one entry per social network the owner listed (`lost.socialMedias`), each submitting its own `channel` value.

All of them are `hx-post` submits of one form to `POST /admin/matches/notify` (permission `MATCH:NOTIFY`), so htmx sends the form's hidden fields (`lostItemId`, `foundItemId`, `_csrf`) plus the clicked button's `name`/`value`. The response is the same `row` fragment re-rendered: the button turns light, and the row shows "Notified <time> (<admin>)".

## Building the event

`MatchNotificationService.notifyOwner(lostItemId, foundItemId, actor, channel)`:

1. Loads the `SimilarItem` pair or throws `NotFoundException("entity.match")`.
2. Builds a `NotificationRequestedEvent` with
   - `subject` and `message` from the message keys `notification.match.subject` and `notification.match.body`, in the admin's current locale, with the lost title and date, the found title, place and date, and the finder's phone and email;
   - `phone` and `email` of the **owner of the lost item**, each set only if the chosen `NotifyChannel` needs it (`isNeedPhone`, `isNeedEmail`), otherwise `null`;
   - `socialMedias`: the single social network when a messenger channel was chosen, otherwise empty;
   - `deduplicationKey = match:<lostId>:<foundId>`.
3. Publishes it to the exchange `derechi.notifications` with routing key `notification.match.found` (`derechi.notifications.*` in `application.yaml`).
4. Stamps `notified_at` and `notified_by` on the pair and returns a `NotifiedMatch` built from the already-loaded entities, so the row can be re-rendered without another query.

If RabbitMQ is unreachable the `AmqpConnectException` propagates, the row is not stamped, and htmx shows the `data-error` text on the button.

`NotifyChannel` values: `ALL`, `EMAIL`, `PHONE`, `TELEGRAM`, `VIBER`, `WHATSAPP`. The phone is included for messenger channels too, because messengers are addressed by number.

## Delivering it (`Notification`)

The queue `notification.events` is bound to `derechi.notifications` with `notification.#`, so one listener handles every notification reason; the routing key says why it was sent. `NotificationRequestedListener` hands the event to `NotificationSender`, which:

- sends via `Channel.EMAIL` when `email` is present;
- sends via `Channel.SMS` when `phone` is present;
- swallows `DuplicateNotificationException` (NotifyHub dedup, key `<recipient>:<channel>`, TTL 1 h).

Any other exception is logged and rethrown as `AmqpRejectAndDontRequeueException`; Spring AMQP retries 3 times and then the message lands in `notification.events.dlq`.

Channels are enabled by configuration presence under `notify.channels.*`. Today only email is configured (SMTP host from `NOTIFY_EMAIL_HOST`, Mailpit in dev at `http://localhost:8025`). Details and the rules for turning on more channels are in [Add a notification channel](../extending/add-a-notification-channel.md).

## Known gaps

- **The consumer ignores the channel choice.** The event has no channel flag, so when an admin picks Telegram the consumer still sees a phone and attempts SMS. Choosing EMAIL works as expected because the phone is `null` in that case.
- **SMS is not actually wired.** `Notification` has only the `notify-email` channel on the classpath, and NotifyHub's SMS channel also needs Twilio and `notify.channels.sms.account-sid`. A send with a phone therefore fails on the SMS step, which after retries dead-letters the message even though the email part may have gone out (dedup prevents a duplicate email on retry).
- Messenger fan-out is commented out in `NotificationSender`. Telegram needs a chat id that `ContactInfo` does not store; Viber is mapped to SMS in `NotificationRequestedEvent.SocialMediaEnum` as a placeholder.
- The body is rendered in the admin's locale, not the owner's; the owner's language is unknown.
- One dedup key per pair means "Notify again" is a no-op within the TTL.

## Where to look

- `Admin-API/src/main/java/org/shpytchuk/adminapi/controller/MatchController.java`
- `Admin-API/src/main/java/org/shpytchuk/adminapi/service/MatchService.java`, `MatchNotificationService.java`
- `Admin-API/src/main/java/org/shpytchuk/adminapi/form/NotifyChannel.java`
- `Admin-API/src/main/resources/templates/matches.html`, `fragments/candidates.html`
- `Notification/src/main/java/org/shpytchuk/notification/service/NotificationSender.java`
- `docker/rabbitmq/definitions.json`
- Tests: `MatchControllerTests`, `MatchNotificationServiceTest`, `NotifyChannelTest`, `NotificationSenderTest`
