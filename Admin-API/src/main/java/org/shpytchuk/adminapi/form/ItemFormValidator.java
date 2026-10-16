package org.shpytchuk.adminapi.form;

import lombok.AllArgsConstructor;
import org.shpytchuk.adminapi.config.property.CountriesProperties;
import org.shpytchuk.adminapi.entity.detail.ContactInfo.SocialMediaEnum;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.util.List;
import java.util.Locale;

@Component
@AllArgsConstructor
public class ItemFormValidator implements Validator {

    static final String COUNTRY_CODE = "validation.country";
    static final String CURRENCY_CODE = "validation.currency";
    static final String COMPENSATION_CODE = "validation.compensation";
    static final String PHONE_OR_NICK_NAME_CODE = "validation.phoneOrNickName";

    private final CountriesProperties countries;

    @Override
    public boolean supports(Class<?> clazz) {
        return ItemForm.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        ItemForm form = (ItemForm) target;

        String country = form.getCountryCode();
        if (StringUtils.hasText(country) && !countries.supports(country)) {
            errors.rejectValue("countryCode", COUNTRY_CODE);
        }

        if (form.getRewardKind() == RewardKind.AMOUNT && (form.getCompensation() == null || form.getCompensation() < 1)) {
            errors.rejectValue("compensation", COMPENSATION_CODE);
        }

        String currency = form.getCurrency();
        if (currency != null && !countries.currencies().contains(currency.trim().toUpperCase(Locale.ROOT))) {
            errors.rejectValue("currency", CURRENCY_CODE);
        }

        if (!StringUtils.hasText(form.getPhone()) && !reachableByNickName(form)) {
            errors.rejectValue("phone", PHONE_OR_NICK_NAME_CODE);
        }
    }

    private static boolean reachableByNickName(ItemForm form) {
        List<SocialMediaEnum> socialMedias = form.getSocialMedias();
        return StringUtils.hasText(form.getNickName()) && socialMedias != null && socialMedias.size() == 1;
    }
}
