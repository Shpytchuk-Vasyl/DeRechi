package org.shpytchuk.clientapi.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.shpytchuk.clientapi.anotation.PhoneOrNickName;
import org.shpytchuk.clientapi.input.ItemContactInfoInput;

public class PhoneOrNickNameValidator implements ConstraintValidator<PhoneOrNickName, ItemContactInfoInput> {

    static final String NICK_NAME_REQUIRED = "must be given when there is no phone";
    static final String ONE_NETWORK_REQUIRED = "must name exactly one network when there is no phone";

    @Override
    public boolean isValid(ItemContactInfoInput contact, ConstraintValidatorContext context) {
        if (contact == null || contact.phone() != null) {
            return true;
        }
        boolean hasNickName = contact.nickName() != null;
        boolean hasOneNetwork = contact.socialMedias() != null && contact.socialMedias().size() == 1;
        if (hasNickName && hasOneNetwork) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        if (!hasNickName) {
            reject(context, "nickName", NICK_NAME_REQUIRED);
        }
        if (!hasOneNetwork) {
            reject(context, "socialMedias", ONE_NETWORK_REQUIRED);
        }
        return false;
    }

    private static void reject(ConstraintValidatorContext context, String property, String message) {
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(property)
                .addConstraintViolation();
    }
}
