-- Removes rows marked `load-` / `@load.test`. Not one transaction: the foreign keys have no
-- index, so children are deleted and vacuumed before their parents. Safe to re-run.

\echo Before:
SELECT (SELECT count(*) FROM lost_item WHERE place_google_place_id LIKE 'load-%')          AS lost_items,
       (SELECT count(*) FROM found_item WHERE place_google_place_id LIKE 'load-%')         AS found_items,
       (SELECT count(*) FROM lost_item_history WHERE place_google_place_id LIKE 'load-%')  AS lost_archived,
       (SELECT count(*) FROM found_item_history WHERE place_google_place_id LIKE 'load-%') AS found_archived,
       (SELECT count(*) FROM contact_info WHERE email LIKE '%@load.test')                  AS contacts,
       (SELECT count(*) FROM place WHERE google_place_id LIKE 'load-%')                    AS places;

DELETE FROM lost_item_claim c
WHERE c.item_id IN (SELECT id FROM lost_item WHERE place_google_place_id LIKE 'load-%')
   OR c.archived_item_id IN (SELECT id FROM lost_item_history WHERE place_google_place_id LIKE 'load-%')
   OR c.contact_info_id IN (SELECT id FROM contact_info WHERE email LIKE '%@load.test');

DELETE FROM found_item_claim c
WHERE c.item_id IN (SELECT id FROM found_item WHERE place_google_place_id LIKE 'load-%')
   OR c.archived_item_id IN (SELECT id FROM found_item_history WHERE place_google_place_id LIKE 'load-%')
   OR c.contact_info_id IN (SELECT id FROM contact_info WHERE email LIKE '%@load.test');

DELETE FROM similar_item s
WHERE s.lost_item_id IN (SELECT id FROM lost_item WHERE place_google_place_id LIKE 'load-%')
   OR s.found_item_id IN (SELECT id FROM found_item WHERE place_google_place_id LIKE 'load-%');

VACUUM lost_item_claim, found_item_claim, similar_item;

DELETE FROM lost_item WHERE place_google_place_id LIKE 'load-%';
DELETE FROM found_item WHERE place_google_place_id LIKE 'load-%';
DELETE FROM lost_item_history WHERE place_google_place_id LIKE 'load-%';
DELETE FROM found_item_history WHERE place_google_place_id LIKE 'load-%';

VACUUM lost_item, found_item, lost_item_history, found_item_history;

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
