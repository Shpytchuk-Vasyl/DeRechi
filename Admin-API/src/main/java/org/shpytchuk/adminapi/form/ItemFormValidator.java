package org.shpytchuk.adminapi.form;

import lombok.AllArgsConstructor;
import org.shpytchuk.adminapi.config.property.CountriesProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.util.Locale;

@Component
@AllArgsConstructor
public class ItemFormValidator implements Validator {

    static final String COUNTRY_CODE = "validation.country";
    static final String CURRENCY_CODE = "validation.currency";

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

        String currency = form.getCurrency();
        if (currency != null && !countries.currencies().contains(currency.trim().toUpperCase(Locale.ROOT))) {
            errors.rejectValue("currency", CURRENCY_CODE);
        }
    }
}
