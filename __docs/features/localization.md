# Localization

The admin panel and the web client speak five languages. Language is a property of the **viewer**, stored in a cookie; it has nothing to do with the country of a notice (see [Countries and currencies](countries-and-currencies.md)) or with the language a notice is written in (see [Automatic matching](automatic-matching.md)).

## Supported locales

`Admin-API/src/main/java/org/shpytchuk/adminapi/config/LocaleConfig.java`:

| Locale | Bundle | Role |
|---|---|---|
| `uk_UA` | `messages_uk.properties` | default when there is no cookie |
| `en_GB` | `messages.properties` | the base bundle, also the fallback |
| `pl_PL` | `messages_pl.properties` | |
| `de_DE` | `messages_de.properties` | |
| `fr_FR` | `messages_fr.properties` | |

There is deliberately no `messages_en.properties`: the base bundle **is** English, and a second copy would drift. `MessagesTest` enforces that every locale in `SUPPORTED` has a bundle, that all bundles declare the same keys, and that every translation keeps the same `{0}`-style placeholders. A missing key would otherwise only show up as the raw key on screen.

## Choosing a language

- `CookieLocaleResolver` reads `DERECHI_LOCALE` (path `/`, 365 days).
- `LocaleChangeInterceptor` accepts `?lang=xx` on any URL and rewrites the cookie; invalid values are ignored.
- The header menu lists `LocaleConfig.SUPPORTED` with flag icons and native names (`Locale.getDisplayLanguage(locale)`), so adding a locale needs no template change.
- `LocaleConfig.current()` normalises whatever the request resolved to one of the supported locales, falling back to the first (Ukrainian).
- The Web-Client reads and writes the same cookie and additionally puts the locale in the URL (`/uk/lost`). Its copy lives in `Web-Client/messages/{en,uk,pl,de,fr}.json` with its own parity test (`pnpm test`).

`spring.messages.fallback-to-system-locale` is off, so an unknown language falls to the base bundle, never to the JVM default.

## What is localized

**All UI text.** Templates contain no literal copy; everything is `#{key}`. Keys are grouped by prefix: `nav.*`, `page.*`, `entity.*`, `action.*`, `field.*`, `category.*`, `social.*`, `list.*`, `matches.*`, `validation.*`, `error.*`, `upload.*`, `notification.*`.

**Plurals.** `MessageFormat` cannot express Slavic plural rules, so `Plurals` (exposed as `${plural}`) picks a suffix: `one`/`few`/`many` for `uk`, `pl`, `ru`, `be`; `one`/`other` elsewhere, with French treating 0 as singular. Keys look like `list.records.one`, `list.records.few`, `list.records.many`, `list.records.other`; the `other` form is the fallback.

**Dates, money, countries, phones.** `Formats` (`${fmt}`) wraps the JDK: `date` is `FormatStyle.MEDIUM` in the current locale, `money(amount, currency)` is `NumberFormat.getCurrencyInstance` with the currency overridden and no fraction digits, `country(code)` is `Locale.getDisplayCountry`, `phone` is libphonenumber's international format. None of these need bundle keys.

**Validation.** `LocaleConfig` registers a `LocalValidatorFactoryBean` with the message source, so `message = "{validation.phone}"` on a form field resolves through the bundles. `ItemFormValidator` uses `validation.country` and `validation.currency`.

**Exceptions.** `NotFoundException` carries an entity **key** (`entity.LOST_ITEM`) rather than text; `GlobalExceptionHandler` builds the sentence in the current locale. The 408 and 500 pages use `error.timeout` and `error.unexpected`.

**Notifications.** The subject and body of a match notification (`notification.match.subject`, `notification.match.body`) are rendered in the admin's current locale at the moment of clicking Notify. The owner's own language is unknown.

## Known gaps

- The notification goes out in the admin's language, not the recipient's.
- `Client-API` is not localized at all: GraphQL error messages are English, and category names are returned as keys for the client to translate.

## Where to look

- `Admin-API/src/main/java/org/shpytchuk/adminapi/config/LocaleConfig.java`
- `Admin-API/src/main/java/org/shpytchuk/adminapi/view/Plurals.java`, `Formats.java`
- `Admin-API/src/main/resources/messages*.properties`
- `Admin-API/src/test/java/org/shpytchuk/adminapi/i18n/MessagesTest.java`
- `Admin-API/src/main/resources/templates/fragments/layout.html` (language menu)

Conventions: [i18n](../conventions/i18n.md). Extending: [Add a language](../extending/add-a-language.md).
