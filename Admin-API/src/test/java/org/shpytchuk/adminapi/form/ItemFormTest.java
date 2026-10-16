package org.shpytchuk.adminapi.form;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ItemFormTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void storesBlankContactsAsNullAndTrimsTheRest() {
        ItemForm form = new ItemForm();
        form.setPhone(" ");
        form.setNickName("  @lostcat ");
        form.setProfileUrl("");

        assertThat(form.getPhone()).isNull();
        assertThat(form.getNickName()).isEqualTo("@lostcat");
        assertThat(form.getProfileUrl()).isNull();
    }

    @Test
    void leavesThePhoneOptionalForTheBeanConstraints() {
        assertThat(validator.validateProperty(new ItemForm(), "phone")).isEmpty();
    }

    @Test
    void acceptsOnlyAnHttpsProfileLink() {
        assertThat(profileUrlViolations("https://t.me/lostcat")).isZero();
        assertThat(profileUrlViolations("http://t.me/lostcat")).isOne();
        assertThat(profileUrlViolations("javascript:alert(1)")).isOne();
        assertThat(profileUrlViolations("https://t.me/lost cat")).isOne();
    }

    @Test
    void limitsTheNickNameTo50AndTheProfileLinkTo255Characters() {
        ItemForm form = new ItemForm();
        form.setNickName("n".repeat(50));
        assertThat(validator.validateProperty(form, "nickName")).isEmpty();
        form.setNickName("n".repeat(51));
        assertThat(validator.validateProperty(form, "nickName")).hasSize(1);

        assertThat(profileUrlViolations("https://t.me/" + "u".repeat(255 - 13))).isZero();
        assertThat(profileUrlViolations("https://t.me/" + "u".repeat(256 - 13))).isOne();
    }

    private static int profileUrlViolations(String profileUrl) {
        ItemForm form = new ItemForm();
        form.setProfileUrl(profileUrl);
        return validator.validateProperty(form, "profileUrl").size();
    }
}
