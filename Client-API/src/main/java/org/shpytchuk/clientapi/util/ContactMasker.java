package org.shpytchuk.clientapi.util;

import org.jspecify.annotations.Nullable;

public final class ContactMasker {

    private ContactMasker() {
    }

    /**
     * {@code +380671234567} -> {@code +38067*****67}.
     */
    public static @Nullable String maskPhone(@Nullable String phone) {
        return phone == null ? null : phone.replaceAll("(^\\+\\d{5})\\d+(..$)", "$1*****$2");
    }

    /**
     * {@code finder@example.com} -> {@code f*****r@example.com}.
     */
    public static @Nullable String maskEmail(@Nullable String email) {
        return email == null ? null : email.replaceAll("(^.)[^@]+(.@.*)", "$1*****$2");
    }
}
