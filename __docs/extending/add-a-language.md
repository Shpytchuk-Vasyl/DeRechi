# Add a language

A new UI language for the admin panel, and what the web team needs on their side. This is about **what the viewer reads**; the language a notice is written in is handled by full-text search configs, see [Add a search language](add-a-search-language.md).

## Before you start

Read [Localization](../features/localization.md) and the [i18n conventions](../conventions/i18n.md). Pick the ISO 639-1 code and a representative country for the flag icon (the layout uses `fi-<country>` classes from flag-icons).

The example below adds Czech (`cs_CZ`).

## Steps

1. **Register the locale.** `Admin-API/src/main/java/org/shpytchuk/adminapi/config/LocaleConfig.java`:

   ```java
   public static final List<Locale> SUPPORTED = List.of(
           Locale.of("uk", "UA"), Locale.of("en", "GB"), Locale.of("pl", "PL"),
           Locale.of("de", "DE"), Locale.of("fr", "FR"), Locale.of("cs", "CZ"));
   ```

   The first entry stays the default. The country part only drives the flag and the display name, not the country axis of notices.

2. **Create the bundle.** Copy `Admin-API/src/main/resources/messages.properties` to `messages_cs.properties` and translate every value. Keep every key and every `{0}` placeholder; do not add keys that the other bundles lack. `messages.properties` itself stays English and must not become `messages_en.properties`.

3. **Plural forms.** `Plurals` chooses between `one`/`few`/`many`/`other` for languages in its `THREE_FORM_LANGUAGES` set (`uk`, `pl`, `ru`, `be`) and `one`/`other` for the rest, with a special case for French. Czech has the Slavic three-form rule, so add `"cs"` to that set. For a language with yet another rule (Arabic, Welsh), extend `Plurals.category` rather than faking it with `other`. Keys to provide in the bundle either way: `list.records.one`, `.few`, `.many`, `.other` (and any other key that goes through `plural.format`).

4. **Formats need nothing.** Dates, money, country names and phone numbers come from the JDK and libphonenumber for whatever locale is current.

5. **Language menu needs nothing.** `GlobalModelAdvice` publishes `LocaleConfig.SUPPORTED` as `languages`, and `fragments/layout.html` iterates it. Check that flag-icons has a flag for the country code you chose.

6. **Run the parity test.** `MessagesTest` fails with the list of missing or extra keys and mismatched placeholders until the new bundle is complete:

   ```bash
   ./mvnw -pl Admin-API test -Dtest=MessagesTest
   ```

7. **Smoke test.** Start Admin-API, open `http://localhost:8083/admin?lang=cs`, walk through the list, the form (validation messages), a 404 (`/admin/lost-items/999999/edit`) and the Matches page with a Notify click so the notification subject and body are rendered in the new language. Check the cookie `DERECHI_LOCALE` is `cs_CZ`.

## Tests to add or update

- `MessagesTest` covers the bundle automatically.
- If you touched `Plurals.category`, add cases to the plural test (there is none yet; `Plurals` is exercised indirectly through controller tests, so add a small unit test next to `FormatsTest`).

## Migration needed?

No. Locale is a cookie, nothing is persisted.

## Web-Client impact

The web team adds `cs` to `locales` in `Web-Client/src/i18n/routing.ts`, creates `Web-Client/messages/cs.json` with every key, and runs `pnpm test` for the parity check. The cookie name and lifetime already match the admin panel. Until that is done, a browser that picked Czech in the admin panel falls back to the web client's default (`en`) because the web client validates the cookie against its own list.

## Checklist

- [ ] `LocaleConfig.SUPPORTED` has the new `Locale`
- [ ] `messages_<xx>.properties` exists with every key and placeholder, in `Admin-API` **and** in `Automatic-Search` (claim messages, see [claims](../features/claims.md); its `MessagesTest` checks parity too)
- [ ] `Plurals.THREE_FORM_LANGUAGES` or `Plurals.category` updated if the language needs it
- [ ] `MessagesTest` green
- [ ] Manual pass through list, form, error page, notification text
- [ ] Web-Client ticket filed with the locale code and the bundle to translate
