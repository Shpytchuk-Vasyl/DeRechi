package org.shpytchuk.ingest.source;

import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.model.SocialMedia;

import java.util.Locale;

public record AuthorProfile(SocialMedia network, String nickName, @Nullable String profileUrl) {

    public static final int NICK_NAME_MAX = 50;
    public static final int PROFILE_URL_MAX = 255;

    public AuthorProfile {
        nickName = cut(nickName.strip());
        if (nickName.isEmpty()) {
            throw new IllegalArgumentException("A " + network + " profile needs a nickname");
        }
        if (profileUrl != null && (!profileUrl.startsWith("https://") || profileUrl.length() > PROFILE_URL_MAX)) {
            profileUrl = null;
        }
    }

    public String contactKey() {
        return network.name() + ":" + nickName.toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "AuthorProfile[" + network + "]";
    }

    private static String cut(String name) {
        if (name.length() <= NICK_NAME_MAX) {
            return name;
        }
        int end = Character.isHighSurrogate(name.charAt(NICK_NAME_MAX - 1)) ? NICK_NAME_MAX - 1 : NICK_NAME_MAX;
        return name.substring(0, end).stripTrailing();
    }
}
