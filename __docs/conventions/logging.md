# Logging

Logs are read by a person, usually through `docker compose logs` on a production machine, often while something is broken. Every line has to say what happened, to which record, and how it ended, without a stack trace to dig through and without personal data. The rules below serve that.

## Rule 1: one line per event, with the ids

Declare the logger the same way everywhere, and log with placeholders:

```java
@Slf4j
public abstract class ClaimService<T extends Thing, C extends Claim<T>> {
    ...
    log.info("Claim {} created on {} item {}", claim.getId(), kind, item.getId());
```

- The logger is `@Slf4j` where the module has Lombok, otherwise `LoggerFactory.getLogger(X.class)` in a field named `log` (see [Java code style](java-code-style.md#logging)). String concatenation in log calls is not used.
- **English only.** One language keeps `grep` working across all services.
- The message names the event and the records it touched: `Claim 42 created on found item 17`, `Fourthwall order 9f3c matched claim 42`, `Archived lost item 17 as history 80: 3 matches deleted, 1 claim moved`. Past tense for something that happened, present for a decision (`Skipping ...`, `Retrying ...`).
- Log ids, kinds, counts and durations, not objects. An object goes into a message only if its `toString` is ours and prints ids (records do; a class without `toString` prints `ItemCreatedEvent@1a2b`).

## Rule 2: levels

| Level | When | Examples |
|---|---|---|
| `ERROR` | Something failed and needs a person: a message or data may be lost. | An unexpected exception reached the top handler; an event dropped from the full buffer; retries exhausted, the message went to the DLQ. |
| `WARN` | A failure the system handled or will retry, or bad input from outside. | RabbitMQ is down, the event is buffered; Fourthwall refused the image; a webhook with a wrong signature; a notification channel is not configured. |
| `INFO` | A business event or a state change, one line per action. | Item created; claim created or repeated; checkout reserved; order matched; notification sent; an admin deleted an item; a cron run summary. |
| `DEBUG` | Details for development. Off in production. | Branch decisions, payload ids, timings of inner steps. |

- **Expected outcomes are not errors.** A missing entity, a validation failure or the unlock limit is answered to the caller; log it at `INFO` or `DEBUG` if at all, never `WARN`/`ERROR`.
- **Reads are not logged one by one.** Queries, list pages and edit forms are covered by the request line of the module (Rule 5).
- **Log where you handle, not where you rethrow.** A method that rethrows (wrapped or not) leaves the logging to whoever handles the exception; otherwise one failure prints twice.

## Rule 3: the cause in the message, no stack traces

A failure is logged as one line that carries the root cause, not as a stack trace:

```java
import static org.springframework.core.NestedExceptionUtils.getMostSpecificCause;

} catch (RestClientException e) {
    log.warn("Fourthwall product {} stays without an image: {}", productId, getMostSpecificCause(e).toString());
}
```

`getMostSpecificCause(e).toString()` prints `ClassName: message` of the deepest cause, which is what you need almost every time (`HttpClientErrorException$Unauthorized: 401 Unauthorized`, `ConnectException: Connection refused`). Do not pass the exception as the last argument in this case.

The exception object is passed only where an **unexpected** exception ends up: the one top-level handler of each module (`GlobalExceptionHandler`, the GraphQL request logger, the HTTP request logger, `RabbitMessageLogger`). Even there the output is short: every module sets

```yaml
logging:
  exception-conversion-word: "%wEx{10}"
```

so a stack trace, ours or a library's (`Retries exhausted for message ...` from Spring AMQP), prints the exception and at most 10 frames per cause, enough to see where it came from.

## Rule 4: no personal data, no secrets

Never log:

- phone numbers, emails, names, addresses or coordinates of people;
- claim tokens, passwords, API keys, HMAC signatures and secrets;
- message bodies that contain any of the above: GraphQL variables, notification texts, whole RabbitMQ payloads of notification events.

Log ids instead. When a recipient really has to be visible (which channel a notification went to), mask it: `+38067*****67`, `f*****r@example.com` (`ContactMasker`; a module that needs it keeps its own copy, like entities). The admin username is not personal data of a user of the site and is logged on purpose, as audit (Rule 6).

Third-party loggers that print recipients are silenced in `application.yaml`. NotifyHub prints the address at `INFO` (`Notification sent via ... to ...`) and in its `WARN` retry lines (`Failed to send email to '...'`), so `io.notifyhub.core.NotificationExecutor` and `io.notifyhub.core.pipeline.RetrySendHandler` are set to `ERROR`; our listener line reports the outcome instead. An exception rethrown to Spring AMQP carries a redacted cause, because its recoverer and error handler print the cause chain.

## Rule 5: one request line per entry point

Each module logs every incoming unit of work once, at its edge, with the outcome and the duration:

| Module | Line | From |
|---|---|---|
| Getaway | method, path, status, duration | Reactor Netty access log |
| Client-API | GraphQL operation, `OK` or error types, duration (no variables); other HTTP requests (the Fourthwall webhook) | `GraphQlRequestLogger`, `HttpRequestLogger` |
| Admin-API | write actions with the actor (Rule 6); errors in `GlobalExceptionHandler` | controllers, `GlobalExceptionHandler` |
| Worker | queue, routing key, event type and ids, outcome, duration | `RabbitMessageLogger` |
| Notification | routing key, event, channels used, outcome | the listener |

Business classes below the edge log only business events (Rule 2), not "entering method X".

## Rule 6: audit of admin actions

Every write action in Admin-API logs one `INFO` line with the actor (`authentication.getName()`, the Keycloak `preferred_username`), the action, the kind and the id: `admin@derechi.site deleted lost item 17`. Logins and failed logins are logged too. These lines are the only record of who changed what; there is no audit table.
