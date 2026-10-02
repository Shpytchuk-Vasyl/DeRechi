package org.shpytchuk.clientapi.input;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.shpytchuk.clientapi.entity.detail.ContactInfo.SocialMediaEnum;

import java.util.List;

public record ContactInfoInput(
        @NotBlank @Pattern(regexp = "^\\+[1-9]\\d{7,14}$") String phone,
        @NotBlank @Email @Size(max = 50) String email,
        List<SocialMediaEnum> socialMedias
) {
}
