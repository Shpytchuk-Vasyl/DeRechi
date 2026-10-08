# Messaging

RabbitMQ is the only way services talk to each other asynchronously. There are no
service-to-service HTTP calls today; the gateway forwards browser traffic to `Client-API`,
and everything else goes through a queue.

## Topology

The exchanges, queues and bindings are not declared by the applications. They come from
`docker/rabbitmq/definitions.json`, which the one-shot `rabbitmq-init` container imports over
the management API after every broker start; the services in compose wait for it to finish.
Changing the topology means editing that file and running `docker compose up -d` again.

| Exchange (topic) | Queue | Binding key | Dead-letter exchange | DLQ |
|---|---|---|---|---|
| `derechi.items` | `worker.items` | `item.*.created` | `derechi.items.dlx` | `worker.items.dlq` (bound with `item.*.created`) |
| `derechi.items` | `worker.claims` | `item.*.claimed`, `item.*.returned`, `item.*.paid` | `derechi.items.dlx` | `worker.claims.dlq` (bound with the same three patterns) |
| `derechi.items` | `worker.archive` | `item.*.archive` | `derechi.items.dlx` | `worker.archive.dlq` (bound with `item.*.archive`) |
| `derechi.notifications` | `notification.events` | `notification.#` | `derechi.notifications.dlx` | `notification.events.dlq` (bound with `#`) |

All eight queues are quorum queues (`x-queue-type: quorum`), durable, on the default vhost.
The user is `derechi` / `derechi` with full permissions; the management UI is on 15672 and
Prometheus metrics on 15692.

## Events

| Event | Type id | Producer | Exchange and routing key | Consumer |
|---|---|---|---|---|
| `ItemCreatedEvent` | `ITEM_CREATED` | `Client-API`, `ItemEventAspect` after `ItemService.create(...)` returns | `derechi.items`, `item.lost.created` or `item.found.created` | `Worker`, `ItemCreatedListener` |
| `NotificationRequestedEvent` | `NOTIFICATION` | `Admin-API`, `MatchNotificationService.notifyOwner(...)` | `derechi.notifications`, `notification.match.found` (from `derechi.notifications.routing-key`) | `Notification`, `NotificationRequestedListener` |
| `ClaimEvent` | `CLAIM` | `Client-API`, `ClaimEventAspect` after `ClaimService.claim(..)` / `confirm(..)` / `markPaid(..)` returns | `derechi.items`, `item.<kind>.claimed`, `item.<kind>.returned`, `item.<kind>.paid` | `Worker`, `ClaimListener` |
| `ArchiveRequestedEvent` | `ARCHIVE_REQUESTED` | `Admin-API`, `AdminItemService.archive(id, actor)` | `derechi.items`, `item.lost.archive`, `item.found.archive` (exchange from `derechi.archive.exchange`) | `Worker`, `ArchiveListener` |
| `NotificationRequestedEvent` | `NOTIFICATION` | `Worker`, `ClaimNotifier` | `derechi.notifications`, `notification.claim.created`, `notification.claim.reminder`, `notification.claim.unlocked` | `Notification`, `NotificationRequestedListener` |

`ItemCreatedEvent` carries only what matching needs: `id`, `date`, `category` (id), `lat`,
`lon`, `title`. The routing key says whether it was a lost or a found notice; the payload
does not.

`NotificationRequestedEvent` carries `subject`, `message` (already localized by the
producer), `phone`, `email`, `socialMedias` and a `deduplicationKey`. Contacts the chosen
channel does not need are sent as `null`; see
[../features/match-notifications.md](../features/match-notifications.md).

`ClaimEvent` carries only the claim `id` and `ArchiveRequestedEvent` the item `id` plus the
admin's login for the log; the routing key says the kind and the verb, and `Worker`
reads the rest from the shared database. That is why it is published after the commit, by
`ClaimEventAspect` with the same `@Order(0)` trick as `ItemEventAspect`; the advice skips a
`repeated` result, so a second claim from the same person or a second click on a confirm link
sends nothing. See [../features/claims.md](../features/claims.md).

The producer side of `Admin-API` is a plain `RabbitTemplate.convertAndSend(exchange, key,
event)`. In `Client-API` the publish is an AspectJ `@AfterReturning` advice on every
`ItemService.create(..)`, which picks the routing key by the concrete service class
(`LostItemService` or not). The reason it is an aspect rather than a line in the service is
that the event must not be published when the transaction fails, and the advice runs only on
a normal return.

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

Both consumers configure the listener the same way:

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

A failing message is retried in-process three times, then rejected without requeue, and the
queue's dead-letter exchange moves it to the matching `.dlq`. Nothing consumes the DLQs; they
are there to be inspected in the management UI. The `DeadLetters` alert fires when any
`.dlq` holds a ready message for a minute; how to inspect and replay is in
[../deployment/monitoring.md](../deployment/monitoring.md#dead-letter-queues).

Three things skip the retry on purpose by throwing `AmqpRejectAndDontRequeueException`:

- `ItemCreatedListener`, `ClaimListener` and `ArchiveListener` when no handler is registered for the received routing key;
- `NotificationRequestedListener` when the sender throws, since a retry at the NotifyHub
  level has already happened (`notify.retry.max-attempts: 3`).

Idempotency on the notification side comes from `deduplicationKey`: `NotificationSender`
hands NotifyHub `<event key>:<CHANNEL>`, NotifyHub keeps a one-hour window
(`notify.deduplication.ttl`) and drops a repeat, which the sender treats as success. That
window lives in NotifyHub's in-memory store: it is per `Notification` instance and is lost on
restart, so a redelivery after a restart or to a second instance is sent again. On the search side the insert into `similar_item` is keyed by
`(found_item_id, lost_item_id)`, so reprocessing the same event does not create duplicates.

## Local development

`docker compose up -d rabbitmq rabbitmq-init` is enough. The import runs on every `up` and is
additive: a new queue or binding appears on an existing volume, but a binding that was
*narrowed* (the items DLQ went from `#` to `item.*.created`) keeps its old pattern until it is
deleted in the UI or the volume is recreated, and a changed queue argument makes the import
fail. Services connect to `${RABBITMQ_HOST:localhost}:5672` from `application.yaml`, and the
container override sets `RABBITMQ_HOST=rabbitmq`. There is no RabbitMQ in the test
suite: the aspect and the sender are tested with a mocked template, see
[../conventions/testing.md](../conventions/testing.md).
