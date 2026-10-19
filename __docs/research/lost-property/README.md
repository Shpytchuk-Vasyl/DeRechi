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
to it), not these files. On 2026-10-19 the official pages were opened by hand and six entries went
in: Kyiv metro office, Lviv city bureau, Chernivtsi trolleybus form, and the rules for the Dnipro
and Kharkiv metro and Chernihiv trolleybuses. Every city page also shows the national block
(Ukrzaliznytsia's form, the finder's duty under Civil Code art. 337).

Held back although grouped A or B: Sumy and Lutsk (the contacts are official, the lost-property
role only from news or an unconfirmed Telegram channel), Odesa, Vinnytsia and Khmelnytskyi (same),
and in Kyiv the Boryspil office, the 15-51 service and the State Migration Service entry (pre-war or
unopened pages). A phone call that confirms the role is enough to add any of them.

## Rechecking

Contacts go stale, and a municipal domain can change hands. Rerun the research every few months,
one agent at a time: the first run used nine in parallel and they exhausted a shared web-search
limit, leaving Rivne, Poltava and parts of Chernivtsi, Sumy and Chernihiv unchecked.
