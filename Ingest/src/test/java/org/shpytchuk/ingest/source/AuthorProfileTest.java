package org.shpytchuk.ingest.source;

import org.junit.jupiter.api.Test;
import org.shpytchuk.ingest.model.SocialMedia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorProfileTest {

    @Test
    void theContactKeyIsTheNetworkAndTheNicknameInLowerCase() {
        AuthorProfile profile = new AuthorProfile(SocialMedia.TELEGRAM, "@Finder_Lviv", "https://t.me/Finder_Lviv");

        assertThat(profile.contactKey()).isEqualTo("TELEGRAM:@finder_lviv");
        assertThat(new AuthorProfile(SocialMedia.FACEBOOK, "Олена Коваль", null).contactKey())
                .isEqualTo("FACEBOOK:олена коваль");
    }

    @Test
    void theNicknameIsTrimmedAndCutToFiftyCharacters() {
        AuthorProfile profile = new AuthorProfile(SocialMedia.FACEBOOK, "  " + "Я".repeat(60) + " ", null);

        assertThat(profile.nickName()).isEqualTo("Я".repeat(AuthorProfile.NICK_NAME_MAX));
    }

    @Test
    void aCutDoesNotSplitAnEmoji() {
        AuthorProfile profile = new AuthorProfile(SocialMedia.FACEBOOK, "a".repeat(49) + "😀tail", null);

        assertThat(profile.nickName()).isEqualTo("a".repeat(49));
    }

    @Test
    void aProfileUrlThatIsNotHttpsOrTooLongIsDropped() {
        assertThat(new AuthorProfile(SocialMedia.TELEGRAM, "@finder", "http://t.me/finder").profileUrl()).isNull();
        assertThat(new AuthorProfile(SocialMedia.FACEBOOK, "Олена", "https://www.facebook.com/" + "a".repeat(240))
                .profileUrl()).isNull();
        assertThat(new AuthorProfile(SocialMedia.TELEGRAM, "@finder", "https://t.me/finder").profileUrl())
                .isEqualTo("https://t.me/finder");
    }

    @Test
    void aBlankNicknameIsNotAProfile() {
        assertThatThrownBy(() -> new AuthorProfile(SocialMedia.FACEBOOK, "  ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theNicknameAndTheLinkStayOutOfToString() {
        AuthorProfile profile = new AuthorProfile(SocialMedia.TELEGRAM, "@finder", "https://t.me/finder");

        assertThat(profile.toString()).contains("TELEGRAM").doesNotContain("finder");
    }
}
