# Messaging

RabbitMQ is the only way services talk to each other asynchronously. The only
service-to-service HTTP call is `Ingest` creating notices through the public GraphQL on the
API entry (Caddy for now), exactly like a browser; the entry forwards browser traffic to
`Client-API`, and everything else goes through a queue.

## Topology

The exchanges, queues and bindings are not declared by the applications. They come from
`docker/rabbitmq/definitions.json`, which the one-shot `rabbitmq-init` container imports over
the management API after every broker start; the services in compose wait for it to finish.
Changing the topology means editing that file and running `docker compose up -d` again.

**One queue per consuming service.** Exchanges are split by what the message is about, and a
service's queue binds every key it wants from any of them:

| Exchange | Routing keys | Producer | Bound queues |
|---|---|---|---|
| `notice.status.exchange` (topic) | `notice.<kind>.created`, `notice.<kind>.updated`, `notice.<kind>.returned`, `notice.<kind>.archived`, `notice.<kind>.deleted` | `Client-API` (`created`, `returned`), `Admin-API` (`created`, `updated`, `deleted`), `Worker` (`archived`) | `worker.queue` (`created`, `updated`, `returned`), `ingest.queue` (`created`, `updated`, `archived`, `deleted`) |
| `claims.exchange` (topic) | `claim.<kind>.claimed`, `claim.<kind>.paid` | `Client-API` | `worker.queue` |
| `notice.action.exchange` (topic) | `notice.<kind>.archive` | `Admin-API` | `worker.queue` |
| `notification.exchange` (topic) | `notification.match.found`, `notification.claim.*` | `Admin-API`, `Worker` | `notification.queue` (`notification.#`) |
| `dead-letter.exchange` (direct) | the name of the queue that rejected the message | RabbitMQ | `worker.dlq`, `notification.dlq`, `ingest.dlq` |

`<kind>` is `lost` or `found`. A key never repeats across exchanges (`notice.` and `claim.`
prefixes), so a consumer tells events apart by the routing key alone. `notice.status.exchange`
carries facts about a notice, `notice.action.exchange` carries requests to act on one: a
subscriber to notice statuses never receives an order to archive.

Every queue dead-letters into `dead-letter.exchange` with `x-dead-letter-routing-key` set to
its own name, and each DLQ is bound by that name, so a rejection lands in exactly its own DLQ
however many queues share the original routing key.

A second consumer of an event is a binding of its service's queue to that key; the producer
does not change. `ingest.queue` takes `created`, `updated`, `archived` and `deleted` but not `returned`:
`Worker` archives every returned notice right away, and the `archived` that follows names the
notice, while `returned` carries a claim id.

All six queues are quorum queues (`x-queue-type: quorum`), durable, on the default vhost.
The user is `derechi` / `derechi` with full permissions; the management UI is on 15672 and
Prometheus metrics on 15692.

## Events

| Event | Type id | Producer | Exchange and routing key | Consumer |
|---|---|---|---|---|
| `ItemCreatedEvent` | `ITEM_CREATED` | `Client-API`, `ItemEventAspect` after `ItemService.create(...)` returns; `Admin-API`, `ArchivableAdminItemService.create(form)` after the commit | `notice.status.exchange`, `notice.lost.created` or `notice.found.created` | `Worker`, `WorkerQueueListener.onItemCreated`; `Ingest`, `NoticeEventListener.onCreated` |
| `ItemUpdatedEvent` | `ITEM_UPDATED` | `Admin-API`, `ArchivableAdminItemService.update(id, form)` after the commit | `notice.status.exchange`, `notice.lost.updated` or `notice.found.updated` | `Worker`, `WorkerQueueListener.onItemUpdated` (matching again); `Ingest`, `NoticeEventListener.onUpdated` (the channel post) |
| `ItemArchivedEvent` | `ITEM_ARCHIVED` | `Worker`, `ItemArchiver.archive(kind, id)` after the commit | `notice.status.exchange` (from `derechi.archive.exchange`), `notice.lost.archived`, `notice.found.archived` | `Ingest`, `NoticeEventListener.onArchived` |
| `ItemDeletedEvent` | `ITEM_DELETED` | `Admin-API`, `ArchivableAdminItemService.delete(id)` after the commit | `notice.status.exchange` (from `derechi.notice-status.exchange`), `notice.lost.deleted`, `notice.found.deleted` | `Ingest`, `NoticeEventListener.onDeleted` |
| `ClaimEvent` | `CLAIM` | `Client-API`, `ClaimEventAspect` after `ClaimService.claim(..)` / `confirm(..)` / `markPaid(..)` returns | `claims.exchange`, `claim.<kind>.claimed`, `claim.<kind>.paid`; `notice.status.exchange`, `notice.<kind>.returned` | `Worker`, `WorkerQueueListener.onClaim` |
| `ArchiveRequestedEvent` | `ARCHIVE_REQUESTED` | `Admin-API`, `AdminItemService.archive(id, actor)` | `notice.action.exchange` (from `derechi.archive.exchange`), `notice.lost.archive`, `notice.found.archive` | `Worker`, `WorkerQueueListener.onArchiveRequested` |
| `NotificationRequestedEvent` | `NOTIFICATION` | `Admin-API`, `MatchNotificationService.notifyOwner(...)` | `notification.exchange`, `notification.match.found` (from `derechi.notifications.routing-key`) | `Notification`, `NotificationRequestedListener` |
| `NotificationRequestedEvent` | `NOTIFICATION` | `Worker`, `ClaimNotifier` | `notification.exchange`, `notification.claim.created`, `notification.claim.reminder`, `notification.claim.unlocked` | `Notification`, `NotificationRequestedListener` |

`ItemCreatedEvent` carries what matching needs (`id`, `date`, `category` (id), `lat`, `lon`,
`title`, `description`) and what a channel post needs, so `Ingest` does not read the notice tables:
`categoryKey`, `placeName`, `compensation`, `currency` and `image` (the storage key). `Worker`
ignores the fields only `Ingest` uses; Jackson 3 skips unknown properties. The routing key
says whether it was a lost or a found notice; the payload does not.

`ItemUpdatedEvent` has the same shape as `ItemCreatedEvent`: `Worker` drops the notice's candidates
nobody was notified about (`similar_item.notified_at IS NULL`) and searches again, keeping the notified
ones; `Ingest` edits the channel post in place (`editMessageMedia` with the photo, `editMessageText`
without), and when Telegram refuses the edit with a 400 other than "message is not modified" (a photo
added to or removed from the notice, a message too old to edit) it deletes the post and makes a new
one. Only the admin panel edits notices, so only `Admin-API` sends it.

`ItemArchivedEvent` and `ItemDeletedEvent` carry only the `id` of the live notice: for an archived
one the id it had before `Worker` moved it into history, not the id of the history copy.

`NotificationRequestedEvent` carries `subject`, `message` and `smsMessage` (both already
localized by the producer; `smsMessage` is the short text for SMS, and `Notification` falls back to
`message` when it is missing; neither it nor `subject` starts with "DeRechi:", since the SMS
alpha-name and the email `from-name` already say who writes), `phone`, `email`, `socialMedias` and a `deduplicationKey`. Contacts the chosen
channel does not need are sent as `null`; see
[../features/match-notifications.md](../features/match-notifications.md).

`ClaimEvent` carries only the claim `id`, also on `notice.<kind>.returned`: that status event
names the claim that confirmed the return, not the notice. `ArchiveRequestedEvent` carries the
item `id` plus the admin's login for the log. The routing key says the kind and the verb, and
`Worker` reads the rest from the shared database. That is why it is published after the
commit, by `ClaimEventAspect` with the same `@Order(0)` trick as `ItemEventAspect`; the advice
skips a `repeated` result, so a second claim from the same person or a second click on a
confirm link sends nothing. See [../features/claims.md](../features/claims.md).

In `Admin-API` the archive request and the match notification are a plain
`RabbitTemplate.convertAndSend(exchange, key, event)`. `created`, `updated` and `deleted` in `Admin-API`
and `archived` in `Worker` go through `AfterCommitPublisher`: the service hands the message to
the transaction, and a `@TransactionalEventListener` sends it after the commit, so `Ingest` never
posts or deletes a notice whose transaction rolled back. A broker failure at that point is a
`WARN` and the event is lost; the caller does not see it. In `Client-API` the publish is an AspectJ `@AfterReturning` advice on every
`ItemService.create(..)`, which takes the routing key from the advised service's kind
(`service.kind().noticeKey("created")`, bound with `target(service)`; `ItemKind` in `entity/`)
and the exchange from `event/Exchanges`. The reason it is an aspect rather than a line in the
service is that the event must not be published when the transaction fails, and the advice
runs only on a normal return.

## One queue, several events

`Worker` reads everything from `worker.queue` through one class, `WorkerQueueListener`:
`@RabbitListener(queues = "${derechi.worker.queue}")` on the class and a `@RabbitHandler`
method per payload class. With several handler methods the converter cannot infer the type
from a parameter, so it takes the class from `__TypeId__` (see below) and Spring calls the
method whose parameter accepts it. Within one payload class `RoutingKeyDispatcher` picks the
handler by the received routing key: `ClaimEvent` arrives as `claimed`, `returned` and `paid`
for both kinds. A payload class without a method lands in the `@RabbitHandler(isDefault = true)`
method, which rejects it.

`Ingest` does the same on `ingest.queue` with `NoticeEventListener`: `onCreated` for
`ItemCreatedEvent`, `onUpdated` for `ItemUpdatedEvent`, `onArchived` for `ItemArchivedEvent`, `onDeleted` for
`ItemDeletedEvent`. It reads
the kind from the routing key (`notice.<kind>.<verb>`) and rejects a key whose verb does not match the
event; the work is in
[`ChannelMirror`](../features/telegram-ingest.md#notices-from-the-site).

Never put a second `@RabbitListener` on the same queue: two listeners on one queue are
competing consumers, and each message goes to one of them at random.

## Serialization

Messages are JSON. Each module that touches RabbitMQ has an identical `RabbitConfig` that
builds a `JacksonJsonMessageConverter` and a `DefaultJacksonJavaTypeMapper` whose
`idClassMapping` is produced by `EventTypeScanner`. The scanner walks the module's own
`event` package for classes annotated with `@EventType("...")` and maps the string id to the
class. Producers write the id into the `__TypeId__` header; consumers resolve it back to their
own copy of the class.

Consequences:

- the id in `@EventType` must be the same string on both sides (`ITEM_CREATED`,
  `NOTIFICATION`); the Java class names and packages do not have to match;
- two classes with the same id in one module fail at startup with a clear message;
- a consumer that receives an id it does not know fails to convert the message, which goes
  through the retry path below.

The scanner, the annotation and the config are copied per module like the entities. Adding
an event is a copy-and-adapt job described in
[../extending/add-an-event.md](../extending/add-an-event.md).

## Failure handling

All three consumers configure the listener the same way:

```yaml
spring:
  rabbitmq:
    listener:
      simple:
        default-requeue-rejected: false
        retry:
          enabled: true
          max-retries: 2
```

A failing message is retried in-process three times, then rejected without requeue, and
`dead-letter.exchange` moves it to the service's `.dlq`. Nothing consumes the DLQs; they
are there to be inspected in the management UI. The `DeadLetters` alert fires when any
`.dlq` holds a ready message for a minute; how to inspect and replay is in
[../deployment/monitoring.md](../deployment/monitoring.md#dead-letter-queues).

Three things skip the retry on purpose by throwing `AmqpRejectAndDontRequeueException`:

- `WorkerQueueListener` and `NoticeEventListener` when no handler is registered for the
  received routing key;
- `WorkerQueueListener` and `NoticeEventListener` when no `@RabbitHandler` method takes the
  payload class;
- `NotificationRequestedListener` when the sender throws, since a retry at the NotifyHub
  level has already happened (`notify.retry.max-attempts: 3`).

`Worker` and `Ingest` dispatch by the received routing key, and a replay changes it: "Move
messages" and "Publish message" in the management UI publish through the default exchange, so
the message comes back with the key `worker.queue` (or `ingest.queue`) and is rejected again.
Replay into those two queues has to go through the original exchange with the original key; see
[../deployment/monitoring.md](../deployment/monitoring.md#dead-letter-queues).

Idempotency on the notification side comes from `deduplicationKey`: `NotificationSender`
hands NotifyHub `<event key>:<CHANNEL>`, NotifyHub keeps a one-hour window
(`notify.deduplication.ttl`) and drops a repeat, which the sender treats as success. That
window lives in NotifyHub's in-memory store: it is per `Notification` instance and is lost on
restart, so a redelivery after a restart or to a second instance is sent again. On the search side the insert into `similar_item` is keyed by
`(found_item_id, lost_item_id)`, so reprocessing the same event does not create duplicates.
`Ingest` writes an `internal` row in `ingest_post` once it has posted a notice, so a redelivered
`created` finds the row and posts nothing (a crash between the post and the row posts it twice); a
redelivered `archived` or `deleted` finds no `message_id` left and does nothing, and a redelivered `updated`
edits the post to the same text, which Telegram answers with "message is not modified" and `Ingest` takes
as done.

## Local development

`docker compose up -d rabbitmq rabbitmq-init` is enough. The import runs on every `up` and is
additive: a new queue or binding appears on an existing volume, but nothing is ever removed.
The exchanges and queues of the previous topology (`derechi.items`, `derechi.notifications`,
`worker.items`, `worker.claims`, `worker.archive`, `notification.events` and their `.dlx`/`.dlq`)
stay on an old volume until they are deleted in the UI or the volume is recreated, and a
changed queue argument makes the import fail. Services connect to
`${RABBITMQ_HOST:localhost}:5672` from `application.yaml`, and the container override sets
`RABBITMQ_HOST=rabbitmq`. There is no RabbitMQ in the test suite: the aspect and the sender
are tested with a mocked template, see [../conventions/testing.md](../conventions/testing.md).
