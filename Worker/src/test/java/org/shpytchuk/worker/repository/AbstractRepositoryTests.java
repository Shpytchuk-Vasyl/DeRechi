package org.shpytchuk.worker.repository;

import org.shpytchuk.worker.entity.detail.ContactInfo;
import org.shpytchuk.worker.entity.detail.Place;
import org.shpytchuk.worker.entity.matching.Claim;
import org.shpytchuk.worker.entity.thing.Thing;
import org.shpytchuk.worker.entity.thing.ThingCategory;
import org.shpytchuk.worker.support.AbstractPostgresTests;
import org.shpytchuk.worker.support.Fixtures;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Repository and service tests on the real schema. Worker's own {@link Place} maps only the id and the
 * coordinate, so places are written with SQL; everything else goes through the entities.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class AbstractRepositoryTests extends AbstractPostgresTests {

    @Autowired
    protected TestEntityManager entityManager;

    @Autowired
    protected JdbcTemplate jdbc;

    protected ThingCategory category(String key) {
        Long id = jdbc.queryForObject("SELECT id FROM thing_category WHERE key = ?", Long.class, key);
        return entityManager.find(ThingCategory.class, id);
    }

    protected Place place(String googlePlaceId, double lat, double lon) {
        jdbc.update("""
                INSERT INTO place (google_place_id, name, country_code, coordinate)
                VALUES (?, ?, 'UA', CAST(ST_SetSRID(ST_MakePoint(?, ?), 4326) AS geography))
                """, googlePlaceId, googlePlaceId, lon, lat);
        return entityManager.find(Place.class, googlePlaceId);
    }

    protected <T extends Thing> T item(T item, String title, String description, LocalDate date,
                                       ThingCategory category, Place place) {
        ContactInfo author = entityManager.persist(Fixtures.contact("+380671234567", "author@example.com"));
        T saved = entityManager.persist(Fixtures.item(item, title, description, date, category, place, author));
        entityManager.flush();
        return saved;
    }

    protected <C extends Claim> C claim(C claim, Thing item, String phone, Instant createdAt) {
        ContactInfo claimant = entityManager.persist(Fixtures.contact(phone, phone.substring(1) + "@example.com"));
        C saved = entityManager.persist(Fixtures.claim(claim, item, claimant, createdAt));
        entityManager.flush();
        return saved;
    }
}
