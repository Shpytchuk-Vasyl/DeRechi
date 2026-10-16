package org.shpytchuk.clientapi.input;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.shpytchuk.clientapi.anotation.PhoneOrNickName;
import org.shpytchuk.clientapi.entity.detail.ContactInfo.SocialMediaEnum;

import java.util.List;

@PhoneOrNickName
public record ItemContactInfoInput(
        @Pattern(regexp = "^\\+[1-9]\\d{7,14}$") String phone,
        @Email @Size(max = 50) String email,
        List<SocialMediaEnum> socialMedias,
        @Size(max = 50) String nickName,
        @Size(max = 255) @Pattern(regexp = "^https://[^\\s/?#]+([/?#]\\S*)?$") String profileUrl
) {
    public ItemContactInfoInput {
        nickName = stripToNull(nickName);
        profileUrl = stripToNull(profileUrl);
    }

    private static String stripToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
