# Places

A place is where a thing was lost or found. It is a Google Places result frozen into our database: an id, a display name, a coordinate and a country. Places are shared between notices and never edited.

## The entity

`place` table, entity `Place` (copied into `DB-Postgres`, `Client-API`, `Admin-API`, `Worker`):

| Column | Type | Notes |
|---|---|---|
| `google_place_id` | `VARCHAR`, primary key | the Google Places id; saving the same id again overwrites the row |
| `name` | `VARCHAR(100) NOT NULL` | what the user sees |
| `coordinate` | `geography(Point, 4326) NOT NULL` | `@JdbcTypeCode(SqlTypes.GEOGRAPHY)`, JTS `Point` with `(lon, lat)` order |
| `country_code` | `VARCHAR(2) NOT NULL` | ISO 3166-1 alpha-2, see [Countries and currencies](countries-and-currencies.md) |

PostGIS is enabled by migration `001`. The `geography` type means distances are metres on the sphere, which is what both the public `near` filter and the matching radius rely on. Liquibase diffs mangle this column type; see [Database migrations](../conventions/database-migrations.md) before regenerating.

`GeoPoints.point(lat, lon)` in `Client-API` builds the JTS point with SRID 4326 and the correct axis order.

## Admin form: Google Places autocomplete

The create/edit form has a search box (`#place-search`) wired by `static/js/place-autocomplete.js`:

- `GoogleMaps` (a view helper built from `MapsProperties`) renders the Maps JS script URL with `libraries=places`, the admin's UI language and `region` (`derechi.maps.region`, default `UA`). The widget is only rendered when `derechi.maps.api-key` (`GOOGLE_MAPS_API_KEY`) is set.
- The input carries `data-region` and `data-countries`. `data-countries` is the lower-cased list of supported countries from `CountriesProperties` and becomes `componentRestrictions.country`, which narrows suggestions. Google accepts at most five countries there. `data-region` is only used as a fallback restriction when `data-countries` is empty; its main job is the `region` bias of the script URL.
- On `place_changed` the script fills the hidden fields `placeId`, `placeName` (cut to 100 characters), `lat`, `lon`, and selects `countryCode` from the `country` entry of `address_components`. If Google returns a country that is not in the select, the previous selection stays, and `ItemFormValidator` will complain if that is wrong.
- If the Maps script fails to authenticate (`gm_authFailure`), the search box is hidden and the manual fields are opened so the form stays usable without Google.

`ItemFormPlaceSearchTests` checks that the form mounts the widget and emits the Maps script URL with the current language. Country validation is covered by `CountriesPropertiesTest` and the validator.

## Public API

- `PlaceInput` on `createLostItem` / `createFoundItem` carries the same five fields; the Web-Client gets them from its own Google Maps integration (`@vis.gl/react-google-maps`).
- `places(name, first, after)` lists stored places sorted by name, with `name` as a case-insensitive substring (`ReferenceService.places`, max 100 characters). The Web-Client uses it to offer "near" suggestions from places that already have notices.
- `near` in `ItemFilterInput` filters notices by distance from a point; see [Search and filtering](search-and-filtering.md).

## Known gaps

- Places are never cleaned up; a place with no notices stays.
- The name is whatever Google returned at creation time in the creator's language; it is not localized afterwards.
- No reverse geocoding: a notice created by coordinates alone (manual fields) still needs a `placeId`, so the admin form expects a Google id even in manual mode.

## Where to look

- `Client-API/src/main/java/org/shpytchuk/clientapi/entity/detail/Place.java`, `util/GeoPoints.java`, `service/ReferenceService.java`, `controller/ReferenceController.java`
- `Admin-API/src/main/java/org/shpytchuk/adminapi/view/GoogleMaps.java`, `config/property/MapsProperties.java`
- `Admin-API/src/main/resources/static/js/place-autocomplete.js`
- `Admin-API/src/main/resources/templates/items/form.html` (the place section)
- Tests: `ReferenceControllerTests` (the `places` query), `PlaceRepositoryTests`, `ItemFormPlaceSearchTests`
