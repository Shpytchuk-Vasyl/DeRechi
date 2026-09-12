package org.shpytchuk.clientapi.dto;

import org.shpytchuk.clientapi.entity.ContactInfo.SocialMediaEnum;

import java.util.List;

public record ContactInfoDto(Long id, String phone, String email, List<SocialMediaEnum> socialMedias) {
}
