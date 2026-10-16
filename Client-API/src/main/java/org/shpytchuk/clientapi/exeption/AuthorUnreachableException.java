package org.shpytchuk.clientapi.exeption;

import lombok.Getter;
import org.shpytchuk.clientapi.entity.detail.ContactInfo.SocialMediaEnum;

@Getter
public class AuthorUnreachableException extends RuntimeException {

    private final SocialMediaEnum socialMedia;
    private final String nickName;
    private final String profileUrl;

    public AuthorUnreachableException(SocialMediaEnum socialMedia, String nickName, String profileUrl) {
        super("The author left no phone, only a nickname in " + socialMedia);
        this.socialMedia = socialMedia;
        this.nickName = nickName;
        this.profileUrl = profileUrl;
    }
}
