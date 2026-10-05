-- Removes everything the load tests put into the database: the seeded notices and the ones the
-- k6 scripts created, with their claims, matches, contacts and places.
--
--   docker exec -i derechi-postgres psql -U derechi -d derechi -v ON_ERROR_STOP=1 < load-tests/seed/cleanup.sql
--
-- A row belongs to the load tests when its place id starts with `load-` or its contact email
-- ends with `@load.test`. Nothing else is touched.
--
-- Not one transaction on purpose. The foreign keys on info_id, place_google_place_id and
-- similar_item.lost_item_id have no index, so every deleted parent row makes Postgres scan the
-- child table, dead rows included. Deleting the children, vacuuming them and only then deleting
-- the parents keeps those scans short. Every step is idempotent: if it stops halfway, run it again.

\echo Before:
SELECT (SELECT count(*) FROM lost_item WHERE place_google_place_id LIKE 'load-%')          AS lost_items,
       (SELECT count(*) FROM found_item WHERE place_google_place_id LIKE 'load-%')         AS found_items,
       (SELECT count(*) FROM lost_item_history WHERE place_google_place_id LIKE 'load-%')  AS lost_archived,
       (SELECT count(*) FROM found_item_history WHERE place_google_place_id LIKE 'load-%') AS found_archived,
       (SELECT count(*) FROM contact_info WHERE email LIKE '%@load.test')                  AS contacts,
       (SELECT count(*) FROM place WHERE google_place_id LIKE 'load-%')                    AS places;

-- 1. Claims on load-test notices, live or archived, and claims left with load-test contacts.
DELETE FROM lost_item_claim c
WHERE c.item_id IN (SELECT id FROM lost_item WHERE place_google_place_id LIKE 'load-%')
   OR c.archived_item_id IN (SELECT id FROM lost_item_history WHERE place_google_place_id LIKE 'load-%')
   OR c.contact_info_id IN (SELECT id FROM contact_info WHERE email LIKE '%@load.test');

DELETE FROM found_item_claim c
WHERE c.item_id IN (SELECT id FROM found_item WHERE place_google_place_id LIKE 'load-%')
   OR c.archived_item_id IN (SELECT id FROM found_item_history WHERE place_google_place_id LIKE 'load-%')
   OR c.contact_info_id IN (SELECT id FROM contact_info WHERE email LIKE '%@load.test');

-- 2. Matches the Worker found for notices the load test created.
DELETE FROM similar_item s
WHERE s.lost_item_id IN (SELECT id FROM lost_item WHERE place_google_place_id LIKE 'load-%')
   OR s.found_item_id IN (SELECT id FROM found_item WHERE place_google_place_id LIKE 'load-%');

VACUUM lost_item_claim, found_item_claim, similar_item;

-- 3. The notices.
DELETE FROM lost_item WHERE place_google_place_id LIKE 'load-%';
DELETE FROM found_item WHERE place_google_place_id LIKE 'load-%';
DELETE FROM lost_item_history WHERE place_google_place_id LIKE 'load-%';
DELETE FROM found_item_history WHERE place_google_place_id LIKE 'load-%';

VACUUM lost_item, found_item, lost_item_history, found_item_history;

-- 4. Their contacts and places.
DELETE FROM contact_info WHERE email LIKE '%@load.test';
DELETE FROM place WHERE google_place_id LIKE 'load-%';

VACUUM ANALYZE contact_info, place;
ANALYZE lost_item;
ANALYZE found_item;

\echo After (all zeros expected):
SELECT (SELECT count(*) FROM lost_item WHERE place_google_place_id LIKE 'load-%')          AS lost_items,
       (SELECT count(*) FROM found_item WHERE place_google_place_id LIKE 'load-%')         AS found_items,
       (SELECT count(*) FROM lost_item_history WHERE place_google_place_id LIKE 'load-%')  AS lost_archived,
       (SELECT count(*) FROM found_item_history WHERE place_google_place_id LIKE 'load-%') AS found_archived,
       (SELECT count(*) FROM contact_info WHERE email LIKE '%@load.test')                  AS contacts,
       (SELECT count(*) FROM place WHERE google_place_id LIKE 'load-%')                    AS places;
