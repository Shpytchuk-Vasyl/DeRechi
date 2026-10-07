# Add a country

A new country in which places can be, together with the reward currency that follows from it. Nothing about this is a language change; see [Add a language](add-a-language.md) for that.

## Before you start

Read [Countries and currencies](../features/countries-and-currencies.md). Decide on the ISO 3166-1 alpha-2 code and confirm that the JDK knows its currency:

```java
Currency.getInstance(Locale.of("", "CZ")).getCurrencyCode()   // CZK
```

If this throws or returns `null`, the country cannot be added as is (no currency, or an outdated JDK currency table), and `CountriesProperties` would refuse to start anyway.

The example adds the Czech Republic (`CZ`, currency `CZK`).

## Steps

1. **Configuration, in both modules.** The list is duplicated on purpose; keep the two in sync.

   ```yaml
   # Client-API/src/main/resources/application.yaml
   # Admin-API/src/main/resources/application.yaml
   derechi:
     countries:
       supported: [UA, PL, DE, FR, CZ]
       fallback: UA
   ```

   Order matters only for display: `currencies()` and the GraphQL `countries` list follow it. `fallback` must stay inside `supported`; it is the default pre-selection of the admin form's country select. There is no environment override for this list in `docker-compose.services.yml`, so the YAML is the only place.

2. **Start both services.** `CountriesProperties` validates the code and resolves the currency at startup. A typo fails fast with a message naming the offending code.

3. **Admin panel needs nothing.** The country select is built from `CountriesProperties.supported`, the label from `fmt.country(code)` (JDK display names in every supported UI language), and the currency select from `currencies()`. The Google Places widget restricts suggestions to `data-countries`, which is the same list.

   One hard limit: **Google Places accepts at most five countries** in `componentRestrictions.country`. With a sixth country the widget throws at initialisation and no suggestions appear. Either keep the list at five, or change `place-autocomplete.js` to drop the restriction (and rely on validation) once we cross that line.

4. **GraphQL needs nothing.** `countries { code currency }` reflects the new entry, and `PlaceInput.countryCode` and `MoneyInput.currency` accept it.

5. **Existing data is unaffected.** Rows keep their `country_code` and `currency`; a notice in the new country only appears once somebody creates one.

6. **Removing a country** is the reverse but not symmetric: rows with that `country_code` or `currency` still exist and the admin form will show them with a validation error on save. Migrate or archive those rows first.

## Tests to add or update

- `CountriesPropertiesTest` in `Admin-API` and `Client-API` test the record's behaviour with inline lists; they do not need to change for a new entry, but add the new code to the assertions if you want the real configuration pinned.
- `ReferenceControllerTests` and `ReferenceControllerSliceTests` assert the `countries` query against the configured list (`returnsTheConfiguredCountriesWithTheirCurrencies`, `returnsTheConfiguredCountriesInOrderWithTheirCurrencies`); extend the expected lists there, and in `ReferenceControllerTests.returnsTheCategoriesSeededByTheMigrations` nothing changes.

## Migration needed?

No. The columns already accept any two- and three-letter codes.

## Web-Client impact

The web client reads `countries` at run time (cached an hour), so the new country shows up in
its currency select and Places restriction automatically; country names
come from `Intl.DisplayNames`, so no message key is needed there either.

One thing it does need: the legal pages are assembled per jurisdiction. Add
`Web-Client/src/content/legal/jurisdictions/<CC>.json` with the six country-dependent
sentences (`findersLaw`, `governingLaw`, `dataLaw`, `rightsBasis`, `complaintRight`,
`withdrawalLaw`) in all five locales and the document's `updated` date, and register it in
`Web-Client/src/content/legal/index.ts`. Without it `/terms`, `/privacy` and the FAQ answer
about rewards fall back to the first jurisdiction (UA) and the server log warns. The Places
restriction is capped at five countries on the web side too.

## Checklist

- [ ] JDK resolves a currency for the code
- [ ] `derechi.countries.supported` updated in `Client-API` and `Admin-API`
- [ ] Both services start cleanly
- [ ] At most five countries, or the Places widget adjusted
- [ ] Created a notice in the new country from the admin form and via GraphQL; reward shows in the right currency
- [ ] Web-Client: jurisdiction JSON added and registered
