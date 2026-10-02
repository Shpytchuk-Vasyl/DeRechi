# Add a social network

A new messenger that a person can list in their contact details and that an admin can pick in the Notify menu. Sending through it is a separate job, see [Add a notification channel](add-a-notification-channel.md).

## Before you start

Read [Notices](../features/notices.md) (contact input) and [Match notifications](../features/match-notifications.md) (the Notify menu). The example adds Signal.

## The one rule that matters

`contact_info.social_medias` is `INT2[]` and the entity maps it with `@Enumerated(EnumType.ORDINAL)`. The database stores **positions**, not names. Therefore:

- **append** the new constant at the end of the enum;
- **never reorder or remove** existing constants; doing so silently relabels every stored contact.

Migration `002-init-schema` defines the column without a CHECK constraint, so the database accepts any ordinal and nothing stops a mistake except this rule.

## Steps

1. **Entity copies.** The enum is nested in `ContactInfo` and the class is copied per module. Change all four identically:

   - `DB-Postgres/src/main/java/org/shpytchuk/dbpostgres/detail/ContactInfo.java`
   - `Client-API/src/main/java/org/shpytchuk/clientapi/entity/detail/ContactInfo.java`
   - `Admin-API/src/main/java/org/shpytchuk/adminapi/entity/detail/ContactInfo.java`
   - `Worker/src/main/java/org/shpytchuk/worker/entity/detail/ContactInfo.java`

   ```java
   public enum SocialMediaEnum {
       TELEGRAM,
       VIBER,
       WHATSAPP,
       SIGNAL
   }
   ```

2. **GraphQL schema.** `Client-API/src/main/resources/graphql/schema.graphqls` has `enum SocialMedia`. Spring for GraphQL maps it by name onto `ContactInfo.SocialMediaEnum`, so the names must match. Note that the schema **already** lists `SIGNAL` and `MESSENGER` while the entity does not; a client sending either today gets a mapping error at `ItemService.toArray`. Adding the constant to the entity closes that gap for `SIGNAL`. Keep the schema order identical to the enum to avoid confusion, even though GraphQL does not care about order.

3. **Admin Notify menu.** `Admin-API/src/main/java/org/shpytchuk/adminapi/form/NotifyChannel.java`: add `SIGNAL`. `social()` converts any non-`ALL`/`EMAIL`/`PHONE` constant with `SocialMediaEnum.valueOf(name())`, so the name must equal the entity constant. `isNeedPhone()` already returns `true` for everything except `EMAIL`, which is right for a phone-addressed messenger. `NotifyChannelTest.everySocialMediaHasItsOwnChannel` fails until the constant exists.

4. **Icon.** `Admin-API/src/main/java/org/shpytchuk/adminapi/view/detail/SocialMediaIcons.java` maps constants to Font Awesome classes (`fa-brands fa-signal-messenger`); anything missing falls back to a generic comment icon, so this is optional but looks sloppy without it.

5. **Labels.** The form checkboxes (`items/form.html`, iterating `allSocialMedias`) and the Notify dropdown (`fragments/candidates.html`) render `#{social.__${messenger}__}`. Add `social.SIGNAL=Signal` to all five bundles or `MessagesTest` fails and the screen shows the key.

6. **Notification module.** `Notification/src/main/java/org/shpytchuk/notification/event/NotificationRequestedEvent.java` has its own `SocialMediaEnum` with a NotifyHub `Channel` per constant. The JSON carries the enum **name**, so add `SIGNAL` there too or deserialisation of an event that mentions it fails and the message dead-letters. NotifyHub has no Signal channel; map it to the closest thing (`Channel.SMS`, like Viber) until the fan-out is implemented.

7. **Admin form.** Nothing else: `ItemModel` puts `SocialMediaEnum.values()` on the model, `ItemForm.socialMedias` binds the list, `ItemMapper` copies it to the entity.

## Tests to add or update

- `NotifyChannelTest` (both cases pass once the constants line up).
- `MessagesTest` for the label.
- `ItemServiceTests` or `LostItemControllerTests` in `Client-API`: one mutation with `socialMedias: [SIGNAL]` round-tripping through the database (`ContactInfoRepositoryTests` shows how the array is asserted).
- `MatchControllerTests`: the Notify dropdown shows a Signal entry when the owner listed it.

## Migration needed?

No. The column is an array of small integers and the new ordinal fits. Do not run `liquibase:diff` expecting a change; it would report none.

## Web-Client impact

The web team runs `pnpm codegen` to pick up the enum (the schema already contains `SIGNAL`, so the generated type may not even change), and adds the label and icon for the checkbox in the notice form and in the notice view.

## Checklist

- [ ] Constant appended (not inserted) in all four `ContactInfo` copies
- [ ] `schema.graphqls` enum matches the entity
- [ ] `NotifyChannel`, `SocialMediaIcons`, `social.*` keys in five bundles
- [ ] `Notification`'s `SocialMediaEnum` extended with a channel mapping
- [ ] `NotifyChannelTest`, `MessagesTest`, a GraphQL round-trip test green
- [ ] Web-Client team informed
