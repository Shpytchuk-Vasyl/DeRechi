package org.shpytchuk.clientapi.input;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.shpytchuk.clientapi.entity.detail.ContactInfo.SocialMediaEnum;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.shpytchuk.clientapi.entity.detail.ContactInfo.SocialMediaEnum.FACEBOOK;
import static org.shpytchuk.clientapi.entity.detail.ContactInfo.SocialMediaEnum.TELEGRAM;
import static org.shpytchuk.clientapi.entity.detail.ContactInfo.SocialMediaEnum.VIBER;

class ItemContactInfoInputTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    private static final String PHONE = "+380671234567";
    private static final String NICK_NAME = "@olena";
    private static final String PROFILE = "https://t.me/olena";

    @Test
    void acceptsAPhoneAlone() {
        assertThat(violations(new ItemContactInfoInput(PHONE, null, null, null, null))).isEmpty();
    }

    @Test
    void acceptsANickNameInOneNetworkWithoutAPhone() {
        assertThat(violations(nickNameOnly(List.of(TELEGRAM), NICK_NAME, PROFILE))).isEmpty();
    }

    @Test
    void acceptsANickNameWithoutAProfileLink() {
        assertThat(violations(nickNameOnly(List.of(FACEBOOK), "Olena Petrenko", null))).isEmpty();
    }

    @Test
    void doesNotLimitTheNetworksNextToAPhone() {
        assertThat(violations(new ItemContactInfoInput(PHONE, null, List.of(TELEGRAM, VIBER), NICK_NAME, null))).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void requiresANickNameWithoutAPhone(String nickName) {
        assertThat(violations(nickNameOnly(List.of(TELEGRAM), nickName, PROFILE))).containsExactly("nickName");
    }

    @Test
    void requiresExactlyOneNetworkWithoutAPhone() {
        assertThat(violations(nickNameOnly(List.of(TELEGRAM, FACEBOOK), NICK_NAME, PROFILE))).containsExactly("socialMedias");
        assertThat(violations(nickNameOnly(List.of(), NICK_NAME, PROFILE))).containsExactly("socialMedias");
        assertThat(violations(nickNameOnly(null, NICK_NAME, PROFILE))).containsExactly("socialMedias");
    }

    @Test
    void reportsTheMissingNickNameAndNetworkTogether() {
        assertThat(violations(new ItemContactInfoInput(null, "finder@example.com", null, null, null)))
                .containsExactlyInAnyOrder("nickName", "socialMedias");
    }

    @Test
    void trimsTheNickNameAndTheProfileLink() {
        ItemContactInfoInput input = nickNameOnly(List.of(TELEGRAM), "  @olena\t", " " + PROFILE + " ");

        assertThat(input.nickName()).isEqualTo(NICK_NAME);
        assertThat(input.profileUrl()).isEqualTo(PROFILE);
        assertThat(violations(input)).isEmpty();
    }

    @Test
    void acceptsFiftyCharactersOfNickNameAfterTrimming() {
        assertThat(violations(nickNameOnly(List.of(FACEBOOK), "  " + "a".repeat(50) + "  ", null))).isEmpty();
    }

    @Test
    void rejectsANickNameOverFiftyCharacters() {
        assertThat(violations(nickNameOnly(List.of(FACEBOOK), "a".repeat(51), null))).containsExactly("nickName");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://t.me/olena", "t.me/olena", "javascript:alert(1)", "https://", "https:// t.me/olena", "ftp://t.me/olena"})
    void rejectsAProfileLinkThatIsNotHttps(String profileUrl) {
        assertThat(violations(nickNameOnly(List.of(TELEGRAM), NICK_NAME, profileUrl))).containsExactly("profileUrl");
    }

    @Test
    void acceptsAFacebookProfileLinkWithAQuery() {
        assertThat(violations(nickNameOnly(List.of(FACEBOOK), "Olena Petrenko",
                "https://www.facebook.com/profile.php?id=100012345678901"))).isEmpty();
    }

    @Test
    void rejectsAProfileLinkOver255Characters() {
        String profileUrl = "https://t.me/" + "a".repeat(243);

        assertThat(violations(nickNameOnly(List.of(TELEGRAM), NICK_NAME, profileUrl))).containsExactly("profileUrl");
    }

    @Test
    void keepsTheE164RuleForAGivenPhone() {
        assertThat(violations(new ItemContactInfoInput("0671234567", null, List.of(TELEGRAM), NICK_NAME, PROFILE)))
                .containsExactly("phone");
        assertThat(violations(new ItemContactInfoInput("", null, List.of(TELEGRAM), NICK_NAME, PROFILE)))
                .containsExactly("phone");
    }

    private static ItemContactInfoInput nickNameOnly(List<SocialMediaEnum> socialMedias, String nickName, String profileUrl) {
        return new ItemContactInfoInput(null, null, socialMedias, nickName, profileUrl);
    }

    private static Set<String> violations(ItemContactInfoInput input) {
        return VALIDATOR.validate(input).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }
}
