package org.shpytchuk.adminapi.form;

import org.junit.jupiter.api.Test;
import org.shpytchuk.adminapi.config.property.CountriesProperties;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RewardKindTest {

    private final ItemFormValidator validator = new ItemFormValidator(new CountriesProperties(List.of("UA"), "UA"));

    @Test
    void zeroIsARewardByAgreementAndNullNoReward() {
        assertThat(RewardKind.of(null)).isEqualTo(RewardKind.NONE);
        assertThat(RewardKind.of(0)).isEqualTo(RewardKind.NEGOTIABLE);
        assertThat(RewardKind.of(500)).isEqualTo(RewardKind.AMOUNT);
    }

    @Test
    void storesNullZeroOrTheAmount() {
        assertThat(RewardKind.NONE.compensation(500)).isNull();
        assertThat(RewardKind.NEGOTIABLE.compensation(500)).isZero();
        assertThat(RewardKind.AMOUNT.compensation(500)).isEqualTo(500);
    }

    @Test
    void anAmountIsRequiredOnlyForASum() {
        assertThat(errors(RewardKind.AMOUNT, null).getFieldError("compensation")).isNotNull();
        assertThat(errors(RewardKind.AMOUNT, 0).getFieldError("compensation")).isNotNull();
        assertThat(errors(RewardKind.AMOUNT, 1).hasFieldErrors("compensation")).isFalse();
        assertThat(errors(RewardKind.NEGOTIABLE, null).hasFieldErrors("compensation")).isFalse();
        assertThat(errors(RewardKind.NONE, null).hasFieldErrors("compensation")).isFalse();
    }

    private Errors errors(RewardKind kind, Integer compensation) {
        ItemForm form = new ItemForm();
        form.setRewardKind(kind);
        form.setCompensation(compensation);
        Errors errors = new BeanPropertyBindingResult(form, "form");
        validator.validate(form, errors);
        return errors;
    }
}
