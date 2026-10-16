package org.shpytchuk.adminapi.form;

import org.junit.jupiter.api.Test;
import org.shpytchuk.adminapi.config.property.CountriesProperties;
import org.shpytchuk.adminapi.entity.detail.ContactInfo.SocialMediaEnum;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ItemFormValidatorTest {

    private final ItemFormValidator validator =
            new ItemFormValidator(new CountriesProperties(List.of("UA", "PL"), "UA"));

    @Test
    void acceptsAPhoneWithoutANickName() {
        assertThat(phoneErrors(contacts("+380671234567", null))).isEmpty();
        assertThat(phoneErrors(contacts("+380671234567", null, SocialMediaEnum.TELEGRAM, SocialMediaEnum.VIBER))).isEmpty();
    }

    @Test
    void acceptsANickNameWithExactlyOneSocialMediaInsteadOfAPhone() {
        assertThat(phoneErrors(contacts(null, "@lostcat", SocialMediaEnum.TELEGRAM))).isEmpty();
        assertThat(phoneErrors(contacts(null, "Olena Koval", SocialMediaEnum.FACEBOOK))).isEmpty();
    }

    @Test
    void wantsAPhoneWhenThereIsNoNickName() {
        assertThat(phoneErrors(contacts(null, null, SocialMediaEnum.TELEGRAM)))
                .containsExactly(ItemFormValidator.PHONE_OR_NICK_NAME_CODE);
    }

    @Test
    void wantsExactlyOneSocialMediaForANickNameWithoutAPhone() {
        assertThat(phoneErrors(contacts(null, "@lostcat")))
                .containsExactly(ItemFormValidator.PHONE_OR_NICK_NAME_CODE);
        assertThat(phoneErrors(contacts(null, "@lostcat", SocialMediaEnum.TELEGRAM, SocialMediaEnum.FACEBOOK)))
                .containsExactly(ItemFormValidator.PHONE_OR_NICK_NAME_CODE);
    }

    @Test
    void treatsABlankPhoneAndABlankNickNameAsMissing() {
        assertThat(phoneErrors(contacts("  ", "  ", SocialMediaEnum.TELEGRAM)))
                .containsExactly(ItemFormValidator.PHONE_OR_NICK_NAME_CODE);
    }

    private List<String> phoneErrors(ItemForm form) {
        Errors errors = new BeanPropertyBindingResult(form, "form");
        validator.validate(form, errors);
        return errors.getFieldErrors("phone").stream().map(FieldError::getCode).toList();
    }

    private static ItemForm contacts(String phone, String nickName, SocialMediaEnum... socialMedias) {
        ItemForm form = new ItemForm();
        form.setCountryCode("UA");
        form.setPhone(phone);
        form.setNickName(nickName);
        form.setSocialMedias(List.of(socialMedias));
        return form;
    }
}
