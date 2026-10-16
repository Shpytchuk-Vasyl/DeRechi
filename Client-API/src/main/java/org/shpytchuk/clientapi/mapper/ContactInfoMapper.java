package org.shpytchuk.clientapi.mapper;

import org.jspecify.annotations.Nullable;
import org.shpytchuk.clientapi.entity.detail.ContactInfo;
import org.shpytchuk.clientapi.entity.detail.ContactInfo.SocialMediaEnum;
import org.shpytchuk.clientapi.input.ContactInfoInput;
import org.shpytchuk.clientapi.input.ItemContactInfoInput;
import org.springframework.util.StringUtils;

import java.util.List;

public final class ContactInfoMapper {

    private ContactInfoMapper() {
    }

    public static ContactInfo toEntity(ContactInfoInput input) {
        return toEntity(input.phone(), input.email(), input.socialMedias());
    }

    public static ContactInfo toEntity(ItemContactInfoInput input) {
        ContactInfo info = toEntity(input.phone(), StringUtils.hasText(input.email()) ? input.email() : null, input.socialMedias());
        info.setNickName(input.nickName());
        info.setProfileUrl(input.profileUrl());
        return info;
    }

    private static ContactInfo toEntity(@Nullable String phone, @Nullable String email, @Nullable List<SocialMediaEnum> socialMedias) {
        ContactInfo info = new ContactInfo();
        info.setPhone(phone);
        info.setEmail(email);
        info.setSocialMedias(socialMedias == null ? null : socialMedias.toArray(SocialMediaEnum[]::new));
        return info;
    }
}
