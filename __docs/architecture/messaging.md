# Messaging

RabbitMQ is the only way services talk to each other asynchronously. There are no
service-to-service HTTP calls today; the gateway forwards browser traffic to `Client-API`,
and everything else goes through a queue.

## Topology

The exchanges, queues and bindings are not declared by the applications. They are loaded
from `docker/rabbitmq/definitions.json` when the broker starts, so the broker is the source
of truth and a service that starts before the broker has finished importing simply retries
its connection. Changing the topology means editing that file and recreating the container.

| Exchange (topic) | Queue | Binding key | Dead-letter exchange | DLQ |
|---|---|---|---|---|
| `derechi.items` | `automatic-search.items` | `item.*.created` | `derechi.items.dlx` | `automatic-search.items.dlq` (bound with `item.*.created`) |
| `derechi.items` | `automatic-search.claims` | `item.*.claimed`, `item.*.returned` | `derechi.items.dlx` | `automatic-search.claims.dlq` (bound with the same two patterns) |
| `derechi.notifications` | `notification.events` | `notification.#` | `derechi.notifications.dlx` | `notification.events.dlq` (bound with `#`) |

All six queues are quorum queues (`x-queue-type: quorum`), durable, on the default vhost.
The user is `derechi` / `derechi` with full permissions; the management UI is on 15672 and
Prometheus metrics on 15692.

## Events

| Event | Type id | Producer | Exchange and routing key | Consumer |
|---|---|---|---|---|
| `ItemCreatedEvent` | `ITEM_CREATED` | `Client-API`, `ItemEventAspect` after `ItemService.create(...)` returns | `derechi.items`, `item.lost.created` or `item.found.created` | `Automatic-Search`, `ItemCreatedListener` |
| `NotificationRequestedEvent` | `NOTIFICATION` | `Admin-API`, `MatchNotificationService.notifyOwner(...)` | `derechi.notifications`, `notification.match.found` (from `derechi.notifications.routing-key`) | `Notification`, `NotificationRequestedListener` |
| `ClaimEvent` | `CLAIM` | `Client-API`, `ClaimEventPublisher` after the claim transaction commits | `derechi.items`, `item.lost.claimed`, `item.found.claimed`, `item.lost.returned`, `item.found.returned` | `Automatic-Search`, `ClaimListener` |
| `NotificationRequestedEvent` | `NOTIFICATION` | `Automatic-Search`, `ClaimNotifier` | `derechi.notifications`, `notification.claim.created`, `notification.claim.reminder` | `Notification`, `NotificationRequestedListener` |

`ItemCreatedEvent` carries only what matching needs: `id`, `date`, `category` (id), `lat`,
`lon`, `title`. The routing key says whether it was a lost or a found notice; the payload
does not.

`NotificationRequestedEvent` carries `subject`, `message` (already localized by the
producer), `phone`, `email`, `socialMedias` and a `deduplicationKey`. Contacts the chosen
channel does not need are sent as `null`; see
[../features/match-notifications.md](../features/match-notifications.md).

`ClaimEvent` carries only the claim `id`; the routing key says the kind and the verb, and
`Automatic-Search` reads the rest from the shared database. That is why it is published after
the commit, from a `@TransactionalEventListener(AFTER_COMMIT)`, not from an aspect: see
[../features/claims.md](../features/claims.md).

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
          max-attempts: 3
```

A failing message is retried in-process three times, then rejected without requeue, and the
queue's dead-letter exchange moves it to the matching `.dlq`. Nothing consumes the DLQs; they
are there to be inspected in the management UI.

Three things skip the retry on purpose by throwing `AmqpRejectAndDontRequeueException`:

- `ItemCreatedListener` and `ClaimListener` when no handler is registered for the received routing key;
- `NotificationRequestedListener` when the sender throws, since a retry at the NotifyHub
  level has already happened (`notify.retry.max-attempts: 3`).

Idempotency on the notification side comes from `deduplicationKey`: `NotificationSender`
hands NotifyHub `<event key>:<CHANNEL>`, NotifyHub keeps a one-hour window
(`notify.deduplication.ttl`) and drops a repeat, which the sender treats as success. On the search side the insert into `similar_item` is keyed by
`(found_item_id, lost_item_id)`, so reprocessing the same event does not create duplicates.

## Local development

`docker compose up -d rabbitmq` is enough; the definitions file is mounted read-only into
the container. Services connect to `localhost:5672` from `application.yaml`, and the
container override sets `SPRING_RABBITMQ_HOST=rabbitmq`. There is no RabbitMQ in the test
suite: the aspect and the sender are tested with a mocked template, see
[../conventions/testing.md](../conventions/testing.md).
