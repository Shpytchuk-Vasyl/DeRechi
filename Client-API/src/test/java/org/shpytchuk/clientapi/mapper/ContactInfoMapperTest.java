package org.shpytchuk.clientapi.mapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.shpytchuk.clientapi.entity.detail.ContactInfo;
import org.shpytchuk.clientapi.entity.detail.ContactInfo.SocialMediaEnum;
import org.shpytchuk.clientapi.input.ContactInfoInput;
import org.shpytchuk.clientapi.input.ItemContactInfoInput;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContactInfoMapperTest {

    private static final String PHONE = "+380671234567";

    @Test
    void copiesTheClaimantsContacts() {
        ContactInfo info = ContactInfoMapper.toEntity(
                new ContactInfoInput(PHONE, "Olena+1@Example.com", List.of(SocialMediaEnum.VIBER, SocialMediaEnum.WHATSAPP)));

        assertThat(info.getId()).isNull();
        assertThat(info.getPhone()).isEqualTo(PHONE);
        assertThat(info.getEmail()).as("normalised by the input record, stored as is").isEqualTo("olena@example.com");
        assertThat(info.getSocialMedias()).containsExactly(SocialMediaEnum.VIBER, SocialMediaEnum.WHATSAPP);
    }

    @Test
    void copiesTheAuthorsContactsWithoutTouchingTheEmail() {
        ContactInfo info = ContactInfoMapper.toEntity(
                new ItemContactInfoInput(PHONE, "Finder+1@Example.com", List.of(SocialMediaEnum.TELEGRAM)));

        assertThat(info.getPhone()).isEqualTo(PHONE);
        assertThat(info.getEmail()).isEqualTo("Finder+1@Example.com");
        assertThat(info.getSocialMedias()).containsExactly(SocialMediaEnum.TELEGRAM);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void storesABlankAuthorEmailAsNull(String email) {
        ContactInfo info = ContactInfoMapper.toEntity(new ItemContactInfoInput(PHONE, email, null));

        assertThat(info.getEmail()).isNull();
    }

    @Test
    void leavesMissingMessengersNull() {
        assertThat(ContactInfoMapper.toEntity(new ContactInfoInput(PHONE, "olena@example.com", null)).getSocialMedias()).isNull();
        assertThat(ContactInfoMapper.toEntity(new ItemContactInfoInput(PHONE, null, null)).getSocialMedias()).isNull();
    }

    @Test
    void keepsAnEmptyMessengerListAsAnEmptyArray() {
        assertThat(ContactInfoMapper.toEntity(new ContactInfoInput(PHONE, "olena@example.com", List.of())).getSocialMedias()).isEmpty();
        assertThat(ContactInfoMapper.toEntity(new ItemContactInfoInput(PHONE, null, List.of())).getSocialMedias()).isEmpty();
    }
}
