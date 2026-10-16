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
        assertThat(info.getNickName()).isNull();
        assertThat(info.getProfileUrl()).isNull();
    }

    @Test
    void copiesTheAuthorsContactsWithoutTouchingTheEmail() {
        ContactInfo info = ContactInfoMapper.toEntity(
                author(PHONE, "Finder+1@Example.com", List.of(SocialMediaEnum.TELEGRAM)));

        assertThat(info.getPhone()).isEqualTo(PHONE);
        assertThat(info.getEmail()).isEqualTo("Finder+1@Example.com");
        assertThat(info.getSocialMedias()).containsExactly(SocialMediaEnum.TELEGRAM);
    }

    @Test
    void copiesAnAuthorKnownOnlyByANickNameWithoutAPhone() {
        ContactInfo info = ContactInfoMapper.toEntity(new ItemContactInfoInput(
                null, null, List.of(SocialMediaEnum.FACEBOOK), "  Olena Petrenko ", "https://www.facebook.com/olena.petrenko"));

        assertThat(info.getPhone()).isNull();
        assertThat(info.getEmail()).isNull();
        assertThat(info.getSocialMedias()).containsExactly(SocialMediaEnum.FACEBOOK);
        assertThat(info.getNickName()).as("trimmed by the input record").isEqualTo("Olena Petrenko");
        assertThat(info.getProfileUrl()).isEqualTo("https://www.facebook.com/olena.petrenko");
    }

    @Test
    void keepsTheNickNameNextToAPhone() {
        ContactInfo info = ContactInfoMapper.toEntity(new ItemContactInfoInput(
                PHONE, null, List.of(SocialMediaEnum.TELEGRAM), "@olena", "https://t.me/olena"));

        assertThat(info.getPhone()).isEqualTo(PHONE);
        assertThat(info.getNickName()).isEqualTo("@olena");
        assertThat(info.getProfileUrl()).isEqualTo("https://t.me/olena");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void storesABlankAuthorEmailAsNull(String email) {
        ContactInfo info = ContactInfoMapper.toEntity(author(PHONE, email, null));

        assertThat(info.getEmail()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void storesABlankNickNameAndProfileLinkAsNull(String blank) {
        ContactInfo info = ContactInfoMapper.toEntity(new ItemContactInfoInput(PHONE, null, null, blank, blank));

        assertThat(info.getNickName()).isNull();
        assertThat(info.getProfileUrl()).isNull();
    }

    @Test
    void leavesMissingMessengersNull() {
        assertThat(ContactInfoMapper.toEntity(new ContactInfoInput(PHONE, "olena@example.com", null)).getSocialMedias()).isNull();
        assertThat(ContactInfoMapper.toEntity(author(PHONE, null, null)).getSocialMedias()).isNull();
    }

    @Test
    void keepsAnEmptyMessengerListAsAnEmptyArray() {
        assertThat(ContactInfoMapper.toEntity(new ContactInfoInput(PHONE, "olena@example.com", List.of())).getSocialMedias()).isEmpty();
        assertThat(ContactInfoMapper.toEntity(author(PHONE, null, List.of())).getSocialMedias()).isEmpty();
    }

    private static ItemContactInfoInput author(String phone, String email, List<SocialMediaEnum> socialMedias) {
        return new ItemContactInfoInput(phone, email, socialMedias, null, null);
    }
}
