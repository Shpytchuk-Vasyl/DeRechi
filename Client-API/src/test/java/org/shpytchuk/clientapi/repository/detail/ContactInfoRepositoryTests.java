package org.shpytchuk.clientapi.repository.detail;

import org.shpytchuk.clientapi.repository.AbstractRepositoryTests;
import org.junit.jupiter.api.Test;
import org.shpytchuk.clientapi.entity.detail.ContactInfo;
import org.shpytchuk.clientapi.entity.detail.ContactInfo.SocialMediaEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.shpytchuk.clientapi.support.Fixtures.contact;

class ContactInfoRepositoryTests extends AbstractRepositoryTests {

    @Autowired
    private ContactInfoRepository contactInfoRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void roundTripsTheSocialMediaArray() {
        ContactInfo saved = contactInfoRepository.save(
                contact(SocialMediaEnum.TELEGRAM, SocialMediaEnum.WHATSAPP));
        entityManager.flush();
        entityManager.clear();

        ContactInfo found = contactInfoRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getSocialMedias())
                .containsExactly(SocialMediaEnum.TELEGRAM, SocialMediaEnum.WHATSAPP);
    }

    @Test
    void storesEnumsAsTheirOrdinalsInASmallintArray() {
        ContactInfo saved = contactInfoRepository.save(
                contact(SocialMediaEnum.VIBER, SocialMediaEnum.WHATSAPP));
        entityManager.flush();
        entityManager.clear();

        List<Integer> stored = jdbcTemplate.queryForObject(
                "SELECT social_medias FROM contact_info WHERE id = ?",
                (rs, row) -> {
                    Short[] values = (Short[]) rs.getArray("social_medias").getArray();
                    return List.of(values[0].intValue(), values[1].intValue());
                },
                saved.getId());

        assertThat(stored).containsExactly(
                SocialMediaEnum.VIBER.ordinal(), SocialMediaEnum.WHATSAPP.ordinal());
    }

    @Test
    void keepsTheDeclaredOrderOfSocialMedias() {
        ContactInfo saved = contactInfoRepository.save(contact(
                SocialMediaEnum.WHATSAPP, SocialMediaEnum.TELEGRAM, SocialMediaEnum.VIBER));
        entityManager.flush();
        entityManager.clear();

        assertThat(contactInfoRepository.findById(saved.getId()).orElseThrow().getSocialMedias())
                .containsExactly(
                        SocialMediaEnum.WHATSAPP, SocialMediaEnum.TELEGRAM, SocialMediaEnum.VIBER);
    }

    @Test
    void acceptsAContactWithoutSocialMedias() {
        ContactInfo saved = contactInfoRepository.save(contact());
        entityManager.flush();
        entityManager.clear();

        ContactInfo found = contactInfoRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getSocialMedias()).isNull();
        assertThat(found.getPhone()).isEqualTo("+380671234567");
        assertThat(found.getEmail()).isEqualTo("finder@example.com");
    }
}
