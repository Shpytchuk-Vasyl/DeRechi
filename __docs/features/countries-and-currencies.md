# Countries and currencies

A notice belongs to a country through its **place**, and the reward is expressed in a currency that defaults to that country's. Country and UI language are two separate axes: a German-speaking admin can edit a Polish notice, and a Polish notice keeps its złoty regardless of who looks at it.

## Supported countries

Configured per module, same values in both:

```yaml
# Client-API/src/main/resources/application.yaml and Admin-API/src/main/resources/application.yaml
derechi:
  countries:
    supported: [UA, PL, DE, FR]
    fallback: UA
```

`CountriesProperties` (a record bound with constructor binding, so `-parameters` matters) normalises the codes to upper case, de-duplicates them, and validates at startup:

- the list must not be empty;
- `fallback` must be in the list (empty `fallback` means the first entry);
- every code must have a currency in the JDK: `Currency.getInstance(Locale.of("", code))`. An unknown code or a country without a currency (Antarctica) fails startup, not the first form submission.

Nothing maps country to currency by hand; the JDK knows that UA is UAH, PL is PLN, DE and FR are EUR. `currencies()` returns the distinct currencies of the supported countries in order, which is what the admin currency select and the GraphQL `countries` query show.

Country names in the admin panel come from the JDK too (`fmt.country(code)` uses `Locale.getDisplayCountry`), so a new country needs no message keys.

## The place carries the country

`place.country_code VARCHAR(2) NOT NULL`, ISO 3166-1 alpha-2 upper case. It is set when the place is created:

- **GraphQL**: `PlaceInput.countryCode!` is required; `ItemService.toPlace` rejects anything not in `supported` with `IllegalArgumentException("Unsupported country: XX")`, which the resolver turns into `BAD_REQUEST`.
- **Admin form**: the Google Places widget reads `address_components` of the chosen place and selects the matching option in the `countryCode` select; the select stays editable. `data-countries` on the input narrows Google's suggestions to the supported countries (Google allows at most five). `ItemFormValidator` rejects an unsupported code with the field error `validation.country`.

Places are keyed by Google place id, so a place is created once and reused; its country does not change afterwards.

## Rewards

Two columns on every item table (`lost_item`, `found_item` and both history tables):

| Column | Type | Meaning |
|---|---|---|
| `compensation` | `INTEGER`, nullable | whole units, no cents |
| `currency` | `VARCHAR(3) NOT NULL` | ISO 4217, present even when there is no amount |

Resolution order, identical in `Client-API` (`ItemService.currencyFor`) and `Admin-API`:

1. an explicit currency from `MoneyInput.currency` or the form select wins;
2. otherwise the currency of the place's country;
3. either way the result must be one of `currencies()`, else `Unsupported currency: XXX` (`BAD_REQUEST`) or the field error `validation.currency`.

There is **no conversion**. An amount is shown in the currency it was declared in; `Formats.money` only localises digits and the symbol.

GraphQL surface:

```graphql
type Money { amount: Int!, currency: String! }
input MoneyInput { amount: Int!, currency: String }
type Place { ..., countryCode: String! }
input PlaceInput { ..., countryCode: String! }
type Country { code: String!, currency: String! }
query { countries { code currency } }
```

`countries` is the single source of the list for the Web-Client; it does not hard-code countries.

## Why VARCHAR and not CHAR

Hibernate's schema validation (`ddl-auto: validate` in `DB-Postgres` and in tests) does not recognise PostgreSQL `bpchar` as `char(n)`, so fixed-width columns would fail validation. Migration `005-country-and-currency.postgresql.sql` adds both columns as `VARCHAR`, backfilling `UA` and `UAH` for rows that predate it.

## Modules not involved

`Automatic-Search` and `Notification` read neither the amount nor the country. Matching is by category, date and distance, and the notification body does not mention the reward.

## Where to look

- `Admin-API/src/main/java/org/shpytchuk/adminapi/config/property/CountriesProperties.java`, `Client-API/src/main/java/org/shpytchuk/clientapi/config/CountriesProperties.java` (identical apart from the package)
- `Client-API/src/main/java/org/shpytchuk/clientapi/service/ItemService.java` (`toPlace`, `currencyFor`)
- `Admin-API/src/main/java/org/shpytchuk/adminapi/form/ItemFormValidator.java`
- `Admin-API/src/main/resources/static/js/place-autocomplete.js`
- `DB-Postgres/changelog/changes/005-country-and-currency.postgresql.sql`
- Tests: `CountriesPropertiesTest` in both modules, `ItemServiceTests`, `ItemFormPlaceSearchTests`

Extending: [Add a country](../extending/add-a-country.md).
