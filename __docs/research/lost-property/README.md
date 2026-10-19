# Lost-property contacts: research

Raw research for the "where else to ask" block on the city pages ("Бюро знахідок у <місто>"):
for each city in `Web-Client/src/lib/cities.ts`, the places where someone who lost a thing can ask,
or where a finder can hand it in. Collected by research agents on 2026-10-19 from web sources.

**This is research, not publishable data.** Nothing here goes to the site until a person has
checked the entry against its source. An official source proves a contact exists, not that the
contact deals with lost items, and several official pages turned out stale or hijacked (see below).

## Files

- `<slug>.json`: one city. `places` lists what was found, `notFound` what was searched for and not
  found, or not checked at all (the entry says which).
- `_national.json`: what is the same in every city: Ukrzaliznytsia, police, documents, bank cards,
  Civil Code articles 337–339, a national registry.

A place:

| Field | Meaning |
|---|---|
| `type` | `metro`, `city_transport`, `railway`, `bus_station`, `airport`, `police`, `cnap`, `taxi`, `other` |
| `name`, `what`, `address`, `phone`, `hours` | as written on the source; `null` when the source has none |
| `url` | the best official page for the service |
| `sources` | every page a fact came from |
| `confidence` | `verified_official` (on an official site) or `secondary_only` (news, aggregators, forums) |
| `handlesLostItems` | added in the follow-up run: `confirmed` (a source says it deals with lost items), `rules_only` (rules say whom to tell, no office), `unknown` |
| `notes` | doubts, conflicts between sources, likely outdated facts |

## Groups

How the cities sort, and what the site shows for each (agreed 2026-10-19):

| Group | Meaning | Shown on the site |
|---|---|---|
| A | a real lost-property service | yes, after a manual check |
| B | rules say whom to tell (driver, dispatcher, station staff), no office | yes, after a manual check |
| C | only an operator's contacts; nothing says it takes lost items | no |
| D | nothing local found | no; the national block only |

After the follow-up run (2026-10-19, `recheckedAt`):

- **A** Kyiv (metro office), Lviv (city bureau), Chernivtsi (trolleybus form and channel), Sumy (depot
  that holds items; its Telegram channel is not yet confirmed as the operator's own).
- **B** Dnipro and Kharkiv (metro: tell station staff), Chernihiv (driver or dispatcher at the end
  stop), Lutsk, Khmelnytskyi, Odesa (the lost-property office rests on one news piece; the official
  page lists the address only as an information centre), Vinnytsia (role confirmed only by a news
  report).
- **C** Cherkasy, Kropyvnytskyi, Ternopil, Ivano-Frankivsk, Uzhhorod, Zhytomyr, Zaporizhzhia,
  Mykolaiv.
- **D** Kherson, Poltava, Rivne.

`handlesLostItems: "confirmed"` records that some source says so, not that the source is
official: for Vinnytsia, Sumy, Khmelnytskyi, Lutsk's ЦНАП and Dnipro's electrotransport it is a news
piece or an unconfirmed channel. Kyiv's Boryspil entry is "confirmed" by a pre-war page. The manual
check decides.

Every city page carries the national block regardless: Ukrzaliznytsia's form
(services.uz.gov.ua/lost-and-found) and hotline 0 800 503 111 cover railway lost property
nationally; no station has its own office.

## Known traps

- **Sumy**: the operator site the city council links to (troleybus.sumy.ua) now shows unrelated
  content. Never link it.
- **Vinnytsia**: depo.vn.ua looks official and is not.
- **Kyiv**: the Boryspil lost-property page and the 15-51 lost-and-found service are only described
  on pre-war pages.
- **Blocked council sites** (Kyiv, Lviv, Lutsk, Rivne, Ivano-Frankivsk) refuse automated access, so
  some facts rest on search-result snippets of those pages. Check them in a browser.
- **Railway storage period**: the rules (order No. 1196, section 17) set none; "30 days" comes from
  a 2019 news story.
- **Documents**: a police report is needed only for a stolen document, not a lost one.

## What is on the site

The city pages render `Web-Client/src/content/lost-property/places.ts` (texts in five locales next
to it), not these files. Every city has at least one entry. An entry marked `needsCheck` is shown
with "(потребує уточнення)" after its text: what it says comes from news, an unconfirmed channel or
no source at all beyond the operator's name.

Confirmed on an official page on 2026-10-19 (no mark): Kyiv metro office, Lviv city bureau,
Chernivtsi trolleybus form, the Dnipro and Kharkiv metro rules, the Chernihiv trolleybus rules, and
the city rules of Uzhhorod (buses, clause 9.1.20) and Kropyvnytskyi (buses, clause 6.7). The last two
were quoted by the research agent from official PDFs that could not be reopened by hand here; check
them against the PDFs listed in `_city-rules.json`.

The national tram and trolleybus rules were repealed in 2017 (Мінрегіон order No. 277), so the
"hand it to the driver or the dispatcher at the last stop" wording is each city's own and had to be
found per city; `_city-rules.json` records the search. For buses the national rule stands: clause 82
of the Rules approved by КМУ resolution No. 176 (the driver writes a report and hands the item to the
carrier). It is in the national block on every city page, with Ukrzaliznytsia's form and the
finder's duty under Civil Code art. 337.

Not shown at all: Boryspil airport and the 15-51 service in Kyiv (pre-war pages), the State Migration
Service entry (page never opened), Lutsk's ЦНАП (news from 2019 and 2021), and every phone number
known only from news.

## Rechecking

Contacts go stale, and a municipal domain can change hands. Rerun the research every few months,
one agent at a time: the first run used nine in parallel and they exhausted a shared web-search
limit, leaving Rivne, Poltava and parts of Chernivtsi, Sumy and Chernihiv unchecked.
