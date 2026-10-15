# Add an event

A new asynchronous message between services over RabbitMQ. The pattern is already in place several times (`ITEM_CREATED` from `Client-API` and `Admin-API` to `Worker` and `Ingest`, `NOTICE` from `Worker` and `Admin-API` to `Ingest`, `NOTIFICATION` from `Admin-API` and `Worker` to `Notification`); copy it rather than inventing another way.

## Before you start

Read [Messaging](../architecture/messaging.md). The example adds `ITEM_UPDATED`, published by `Admin-API` when a notice is edited, consumed by `Ingest` to edit the notice's channel post (today an edit does not reach the channel).

## How events are typed

Every module that touches RabbitMQ has a `RabbitConfig` that installs a `JacksonJsonMessageConverter` with a `DefaultJacksonJavaTypeMapper` whose id-to-class mapping comes from `EventTypeScanner.scan()`. The scanner looks for classes annotated `@EventType("ID")` **in its own package** (`org.shpytchuk.<module>.event`) and fails startup on a duplicate id. The converter writes the id into the `__TypeId__` header on publish and resolves it on consume, so the producer's and the consumer's classes may differ in name and package as long as:

- the `@EventType` value is identical;
- the JSON shape is compatible (same property names; the consumer may ignore extras).

Each module has its own copy of `EventType` and `EventTypeScanner`; they are deliberately not shared.

## Steps

1. **Event class, producer side.** `Admin-API/src/main/java/org/shpytchuk/adminapi/event/ItemUpdatedEvent.java`:

   ```java
   @EventType("ITEM_UPDATED")
   public record ItemUpdatedEvent(Long id, String title, String description) { }
   ```

   Records work for publishing (Jackson reads the components). Keep payloads flat and small: ids and the few values the consumer needs, no entities.

2. **Event class, consumer side.** `Ingest/src/main/java/org/shpytchuk/ingest/event/ItemUpdatedEvent.java` with the same id and the same property names. The consumer-side events in `Worker` are mutable classes with Lombok (`@Getter @Setter @NoArgsConstructor`), in `Ingest` and `Notification` records; either works as long as the property names match.

3. **Broker topology.** `docker/rabbitmq/definitions.json` is imported into the broker by `rabbitmq-init` on every `docker compose up`. Decide whether the event fits an existing exchange (`notice.status.exchange` for facts about a notice, `claims.exchange` for claims, `notice.action.exchange` for requests to act on a notice, `notification.exchange` for anything that ends in a message to a person) or needs a new one. Every consuming service has exactly one queue, so a consumer that already exists only gets a binding from the exchange to its queue. For a new consuming service, add:

   - a quorum queue `<service>.queue` with `x-dead-letter-exchange: dead-letter.exchange` and `x-dead-letter-routing-key: <service>.queue`;
   - its `<service>.dlq` bound to `dead-letter.exchange` with the key `<service>.queue`;
   - bindings from the topic exchanges with routing-key patterns.

   Routing keys follow `<entity>.<kind>.<verb>` and never repeat across exchanges: `notice.lost.created`, `claim.found.paid`, `notification.match.found`. For the example: `notice.lost.updated` and `notice.found.updated` on `notice.status.exchange`, and `ingest.queue` bound with a further pattern `notice.*.updated`.

   `rabbitmq-init` imports this file on every `docker compose up -d`, so a new exchange, queue or binding appears without touching the volume. The import only adds: a removed binding or a changed queue argument needs the object deleted in the management UI at `http://localhost:15672` (or `docker compose down -v rabbitmq`) first. On the hosted broker the same file goes in with `docker/rabbitmq/import.py`.

4. **Publish.** In the service, after the state change:

   ```java
   publisher.publish(noticeStatus.exchange(), kind().noticeKey("updated"), new ItemUpdatedEvent(id, title, description));
   ```

   `Admin-API` keeps exchange and routing key in `derechi.notifications.*` (`NotificationProperties`), `derechi.archive.exchange` and `derechi.notice-status.exchange` (`NoticeStatusProperties`) and builds the notice keys with `ItemKind.noticeKey`; `Worker` keeps the exchange of `archived` in `derechi.archive.exchange`; `Client-API` keeps the exchanges in `event/Exchanges` and builds the keys with `ItemKind.noticeKey` / `claimKey`. Prefer a `@ConfigurationProperties` record for anything new. A plain `rabbitTemplate.convertAndSend` inside a `@Transactional` method sends before commit; if that matters, use `AfterCommitPublisher` in `Admin-API` and `Worker` (a `@TransactionalEventListener` sends after the commit; a broker failure there is only a `WARN`), or an `@AfterReturning` aspect with `@Order(0)` as `Client-API` does in `ItemEventAspect` and `ClaimEventAspect`, making sure the advised method owns the outermost transaction.

5. **Consume.** A service reads its one queue through one listener class. In `Worker` that is `WorkerQueueListener`: `@RabbitListener` on the class and a `@RabbitHandler` method per event class (the converter picks the class from `__TypeId__`, Spring the method by its parameter). Each method resolves its handler with `RoutingKeyDispatcher<H>`: built from the handler beans and a `routingKey()` function, `resolve(message)` reads the received routing key and throws `AmqpRejectAndDontRequeueException` for a key nobody handles (`onItemCreated` and `onClaim` dispatch to handler beans, `onArchiveRequested` to an `ItemKind`). A new handler for an event the service already reads is one more bean with its own `routingKey()`; a new event class is one more `@RabbitHandler` method. `Ingest` reads `ingest.queue` through `NoticeEventListener` the same way, but parses the kind and the verb out of `notice.<kind>.<verb>` itself; the example is one more `@RabbitHandler` method there that hands `ItemUpdatedEvent` to `ChannelMirror`. Never add a second `@RabbitListener` on the same queue: two listeners on one queue compete, and each message reaches only one of them.

   Queue names come from properties (`derechi.worker.queue`, `derechi.notification.queue`, `derechi.events.queue` in `Ingest`); a new consuming service adds `derechi.<service>.queue` to its `application.yaml` and references it as `@RabbitListener(queues = "${...}")`.

6. **Failure policy.** Consumers already set `spring.rabbitmq.listener.simple.retry.max-retries: 2` (Boot 4's name; the old `max-attempts` is silently ignored) and `default-requeue-rejected: false`, so a failing message is attempted three times in-process and then dead-letters. Throw for transient failures; throw `AmqpRejectAndDontRequeueException` for messages that can never succeed so they go to the DLQ immediately.

7. **Idempotency.** RabbitMQ delivers at least once. Design the handler so a replay is harmless (the example edits a post to the same text, which is naturally idempotent; inserts should use a natural key, as `similar_item` does with its composite primary key).

## Tests to add or update

- Producer: `ItemEventAspectTest` in `Client-API` is the model: real Spring context, `RabbitTemplate` replaced with a `@MockitoBean`, assert exchange, routing key and the flattened payload.
- Consumer: unit-test the handler with the event object directly, and the listener's dispatch with a mocked handler map. `EventTypeScannerTest` shows how to assert that the scanner finds the new class (`picksUpAnnotatedEventsSoRabbitCanResolveThemByTypeId`); add the id to its expectations in each module where the event lives.
- Context tests (`*ApplicationTests`) catch a duplicate `@EventType` id at startup.

## Migration needed?

No, unless the handler needs new columns.

## Web-Client impact

None. Events are internal.

## Checklist

- [ ] Same `@EventType` id and compatible JSON on both sides, classes in the `event` package of each module
- [ ] Exchange, queue, DLQ and binding in `definitions.json`, routing key follows the naming scheme
- [ ] Local broker volume reset or objects created by hand
- [ ] Publish wired with exchange and key from properties
- [ ] Consumer dispatches by routing key, rejects unknown keys, is idempotent
- [ ] Producer and consumer tests, scanner expectations updated
- [ ] [Messaging](../architecture/messaging.md) page updated with the new row
