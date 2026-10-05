-- `items` lost and `items` found notices on items / 5 places. Rows are marked for cleanup.sql:
-- place ids start with `load-`, contact emails end with `@load.test`.

\if :{?items}
\else
    \set items 50000
\endif

SELECT greatest(:items / 5, 1) AS places \gset

SELECT exists(SELECT 1 FROM place WHERE google_place_id LIKE 'load-seed-%') AS seeded \gset
\if :seeded
    \echo 'Load-test rows are already there. Run cleanup.sql first.'
    \quit
\endif

\echo Seeding :items lost and :items found notices on :places places

BEGIN;

CREATE TEMP TABLE load_city ON COMMIT DROP AS
SELECT row_number() OVER () AS n, v.name, v.lat, v.lon, v.country_code
FROM (VALUES ('Київ', 50.4501, 30.5234, 'UA', 8),
             ('Харків', 49.9935, 36.2304, 'UA', 4),
             ('Одеса', 46.4825, 30.7233, 'UA', 4),
             ('Дніпро', 48.4647, 35.0462, 'UA', 3),
             ('Львів', 49.8397, 24.0297, 'UA', 4),
             ('Запоріжжя', 47.8388, 35.1396, 'UA', 2),
             ('Вінниця', 49.2331, 28.4682, 'UA', 2),
             ('Полтава', 49.5883, 34.5514, 'UA', 1),
             ('Чернігів', 51.4982, 31.2893, 'UA', 1),
             ('Івано-Франківськ', 48.9226, 24.7111, 'UA', 1),
             ('Ужгород', 48.6208, 22.2879, 'UA', 1),
             ('Черкаси', 49.4444, 32.0598, 'UA', 1),
             ('Warszawa', 52.2297, 21.0122, 'PL', 2),
             ('Kraków', 50.0647, 19.9450, 'PL', 1),
             ('Wrocław', 51.1079, 17.0385, 'PL', 1),
             ('Berlin', 52.5200, 13.4050, 'DE', 1),
             ('München', 48.1351, 11.5820, 'DE', 1),
             ('Paris', 48.8566, 2.3522, 'FR', 1),
             ('Lyon', 45.7640, 4.8357, 'FR', 1)) AS v(name, lat, lon, country_code, weight)
         CROSS JOIN LATERAL generate_series(1, v.weight);

-- The same words as lib/data.js.
CREATE TEMP TABLE load_vocab ON COMMIT DROP AS
SELECT row_number() OVER () AS n, v.category_key, v.noun
FROM (VALUES ('DOCUMENTS', 'паспорт'),
             ('DOCUMENTS', 'посвідчення водія'),
             ('DOCUMENTS', 'студентський квиток'),
             ('DOCUMENTS', 'ID-картка'),
             ('WALLET', 'гаманець'),
             ('WALLET', 'портмоне'),
             ('WALLET', 'банківська картка'),
             ('ELECTRONICS', 'телефон'),
             ('ELECTRONICS', 'навушники'),
             ('ELECTRONICS', 'ноутбук'),
             ('ELECTRONICS', 'планшет'),
             ('ELECTRONICS', 'смарт-годинник'),
             ('JEWELRY', 'обручка'),
             ('JEWELRY', 'сережки'),
             ('JEWELRY', 'ланцюжок'),
             ('JEWELRY', 'браслет'),
             ('ANIMALS', 'собака'),
             ('ANIMALS', 'кіт'),
             ('ANIMALS', 'папуга'),
             ('KEYS', 'ключі'),
             ('KEYS', 'ключі від авто'),
             ('KEYS', 'брелок'),
             ('BAGS', 'рюкзак'),
             ('BAGS', 'сумка'),
             ('BAGS', 'валіза'),
             ('OTHER', 'парасолька'),
             ('OTHER', 'окуляри'),
             ('OTHER', 'дитяча іграшка'),
             ('OTHER', 'шарф')) AS v(category_key, noun);

INSERT INTO place (google_place_id, name, coordinate, country_code)
SELECT 'load-seed-' || g,
       c.name || ', ' || (ARRAY ['вул. Центральна', 'вул. Шевченка', 'просп. Незалежності',
                                 'вул. Франка', 'пл. Ринок', 'вул. Садова'])[1 + g % 6] || ' ' || (1 + g % 150),
       ST_SetSRID(ST_MakePoint(c.lon + (random() - 0.5) * 0.4, c.lat + (random() - 0.5) * 0.25), 4326)::geography,
       c.country_code
FROM generate_series(1, :places) AS g
         JOIN load_city c ON c.n = 1 + g % (SELECT count(*) FROM load_city);

CREATE TEMP TABLE load_pick ON COMMIT DROP AS
SELECT g,
       kind,
       1 + floor(random() * (SELECT count(*) FROM load_vocab))::int AS vocab_n,
       'load-seed-' || (1 + floor(random() * :places)::int)        AS place_id,
       current_date - floor(random() * 60)::int                    AS date,
       CASE WHEN random() < 0.5 THEN (1 + floor(random() * 50)::int) * 100 END AS compensation,
       (ARRAY ['біля метро', 'у парку', 'в автобусі', 'на вокзалі', 'у ТРЦ', 'біля школи',
               'на пляжі', 'в кафе'])[1 + floor(random() * 8)::int] AS landmark,
       (ARRAY ['чорний', 'коричневий', 'червоний', 'синій', 'сірий', 'білий',
               'зелений'])[1 + floor(random() * 7)::int]            AS color,
       (ARRAY ['Прошу повернути за винагороду.', 'Втрачено ввечері.', 'Є особливі прикмети, напишіть.',
               'Знайдено зранку, зберігаю в себе.'])[1 + floor(random() * 4)::int] AS phrase,
       random()                                                    AS r
FROM generate_series(1, :items) AS g
         CROSS JOIN (VALUES ('lost'), ('found')) AS k(kind);

INSERT INTO contact_info (email, phone)
SELECT 'seed-' || kind || '-' || g || '@load.test',
       CASE kind WHEN 'lost' THEN '+38050' ELSE '+38067' END || lpad(g::text, 7, '0')
FROM load_pick;

INSERT INTO lost_item (title, description, date, compensation, currency, image, category_id, info_id,
                       place_google_place_id)
SELECT upper(left(v.noun, 1)) || substr(v.noun, 2) || ' ' || p.landmark,
       CASE WHEN p.r < 0.2 THEN NULL ELSE 'Колір: ' || p.color || '. ' || p.phrase END,
       p.date,
       p.compensation,
       CASE pl.country_code WHEN 'UA' THEN 'UAH' WHEN 'PL' THEN 'PLN' ELSE 'EUR' END,
       CASE WHEN p.r > 0.7 THEN 'http://localhost:8080/files/load/seed.jpg' END,
       tc.id,
       ci.id,
       p.place_id
FROM load_pick p
         JOIN load_vocab v ON v.n = p.vocab_n
         JOIN thing_category tc ON tc.key = v.category_key
         JOIN place pl ON pl.google_place_id = p.place_id
         JOIN contact_info ci ON ci.email = 'seed-lost-' || p.g || '@load.test'
WHERE p.kind = 'lost';

INSERT INTO found_item (title, description, date, compensation, currency, image, category_id, info_id,
                        place_google_place_id)
SELECT upper(left(v.noun, 1)) || substr(v.noun, 2) || ' ' || p.landmark,
       CASE WHEN p.r < 0.2 THEN NULL ELSE 'Колір: ' || p.color || '. ' || p.phrase END,
       p.date,
       p.compensation,
       CASE pl.country_code WHEN 'UA' THEN 'UAH' WHEN 'PL' THEN 'PLN' ELSE 'EUR' END,
       'http://localhost:8080/files/load/seed.jpg',
       tc.id,
       ci.id,
       p.place_id
FROM load_pick p
         JOIN load_vocab v ON v.n = p.vocab_n
         JOIN thing_category tc ON tc.key = v.category_key
         JOIN place pl ON pl.google_place_id = p.place_id
         JOIN contact_info ci ON ci.email = 'seed-found-' || p.g || '@load.test'
WHERE p.kind = 'found';

COMMIT;

ANALYZE place;
ANALYZE contact_info;
ANALYZE lost_item;
ANALYZE found_item;

SELECT (SELECT count(*) FROM lost_item WHERE place_google_place_id LIKE 'load-%')  AS lost_items,
       (SELECT count(*) FROM found_item WHERE place_google_place_id LIKE 'load-%') AS found_items,
       (SELECT count(*) FROM place WHERE google_place_id LIKE 'load-%')            AS places;
