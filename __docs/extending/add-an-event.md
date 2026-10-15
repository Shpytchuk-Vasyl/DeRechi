# Add an event

A new asynchronous message between services over RabbitMQ. The pattern is already in place twice (`ITEM_CREATED` from `Client-API` to `Worker`, `NOTIFICATION` from `Admin-API` to `Notification`); copy it rather than inventing a third way.

## Before you start

Read [Messaging](../architecture/messaging.md). The example adds `ITEM_ARCHIVED`, published by `Admin-API` when a notice is archived, consumed by `Worker` to drop stale candidates.

## How events are typed

Every module that touches RabbitMQ has a `RabbitConfig` that installs a `JacksonJsonMessageConverter` with a `DefaultJacksonJavaTypeMapper` whose id-to-class mapping comes from `EventTypeScanner.scan()`. The scanner looks for classes annotated `@EventType("ID")` **in its own package** (`org.shpytchuk.<module>.event`) and fails startup on a duplicate id. The converter writes the id into the `__TypeId__` header on publish and resolves it on consume, so the producer's and the consumer's classes may differ in name and package as long as:

- the `@EventType` value is identical;
- the JSON shape is compatible (same property names; the consumer may ignore extras).

Each module has its own copy of `EventType` and `EventTypeScanner`; they are deliberately not shared.

## Steps

1. **Event class, producer side.** `Admin-API/src/main/java/org/shpytchuk/adminapi/event/ItemArchivedEvent.java`:

   ```java
   @EventType("ITEM_ARCHIVED")
   public record ItemArchivedEvent(Long id, String kind) { }
   ```

   Records work for publishing (Jackson reads the components). Keep payloads flat and small: ids and the few values the consumer needs, no entities.

2. **Event class, consumer side.** `Worker/src/main/java/org/shpytchuk/worker/event/ItemArchivedEvent.java` with the same id and the same property names. The existing consumer-side events are mutable classes with Lombok (`@Getter @Setter @NoArgsConstructor`); a record works too as long as the property names match.

3. **Broker topology.** `docker/rabbitmq/definitions.json` is imported into the broker by `rabbitmq-init` on every `docker compose up`. Decide whether the event fits an existing exchange (`notice.status.exchange` for facts about a notice, `claims.exchange` for claims, `notice.action.exchange` for requests to act on a notice, `notification.exchange` for anything that ends in a message to a person) or needs a new one. Every consuming service has exactly one queue, so a consumer that already exists only gets a binding from the exchange to its queue. For a new consuming service, add:

   - a quorum queue `<service>.queue` with `x-dead-letter-exchange: dead-letter.exchange` and `x-dead-letter-routing-key: <service>.queue`;
   - its `<service>.dlq` bound to `dead-letter.exchange` with the key `<service>.queue`;
   - bindings from the topic exchanges with routing-key patterns.

   Routing keys follow `<entity>.<kind>.<verb>` and never repeat across exchanges: `notice.lost.created`, `claim.found.paid`, `notification.match.found`. For the example: `notice.lost.archived` and `notice.found.archived` on `notice.status.exchange` (already bound to `ingest.queue`), and `worker.queue` bound with a further pattern `notice.*.archived`.

   The broker only reads this file when its data volume is empty. On a dev box that means `docker compose down -v rabbitmq` (or `down -v` for everything) and `up -d`, or add the objects by hand in the management UI at `http://localhost:15672` and mirror them into the file.

4. **Publish.** In the service, after the state change:

   ```java
   rabbitTemplate.convertAndSend(properties.exchange(), "notice.lost.archived", new ItemArchivedEvent(id, "LOST"));
   ```

   `Admin-API` keeps exchange and routing key in `derechi.notifications.*` (`NotificationProperties`) and `derechi.archive.exchange`; `Client-API` keeps the exchanges in `event/Exchanges` and builds the keys with `ItemKind.noticeKey` / `claimKey`. Prefer a `@ConfigurationProperties` record for anything new. Publishing inside a `@Transactional` method sends before commit; if that matters, publish from an `@AfterReturning` aspect with `@Order(0)` as `Client-API` does in `ItemEventAspect` and `ClaimEventAspect`, and make sure the advised method owns the outermost transaction.

5. **Consume.** A service reads its one queue through one listener class. In `Worker` that is `WorkerQueueListener`: `@RabbitListener` on the class and a `@RabbitHandler` method per event class (the converter picks the class from `__TypeId__`, Spring the method by its parameter). Each method resolves its handler with `RoutingKeyDispatcher<H>`: built from the handler beans and a `routingKey()` function, `resolve(message)` reads the received routing key and throws `AmqpRejectAndDontRequeueException` for a key nobody handles (`onItemCreated` and `onClaim` dispatch to handler beans, `onArchiveRequested` to an `ItemKind`). A new handler for an event the service already reads is one more bean with its own `routingKey()`; a new event class is one more `@RabbitHandler` method. Never add a second `@RabbitListener` on the same queue: two listeners on one queue compete, and each message reaches only one of them.

   Queue names come from properties (`derechi.worker.queue`, `derechi.notification.queue`); a new consuming service adds `derechi.<service>.queue` to its `application.yaml` and references it as `@RabbitListener(queues = "${...}")`.

6. **Failure policy.** Consumers already set `spring.rabbitmq.listener.simple.retry.max-retries: 2` (Boot 4's name; the old `max-attempts` is silently ignored) and `default-requeue-rejected: false`, so a failing message is attempted three times in-process and then dead-letters. Throw for transient failures; throw `AmqpRejectAndDontRequeueException` for messages that can never succeed so they go to the DLQ immediately.

7. **Idempotency.** RabbitMQ delivers at least once. Design the handler so a replay is harmless (the example deletes rows, which is naturally idempotent; inserts should use a natural key, as `similar_item` does with its composite primary key).

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
