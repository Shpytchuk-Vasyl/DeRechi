package org.shpytchuk.clientapi.util;

import jakarta.validation.constraints.NotNull;

public final class ContactMasker {

    private ContactMasker() {
    }

    /**
     * {@code +380671234567} -> {@code +38067*****67}.
     */
    public static String maskPhone(@NotNull String phone) {
        return  phone.replaceAll("(^\\+\\d{5})\\d+(..$)", "$1*****$2");
    }

    /**
     * {@code finder@example.com} -> {@code f*****r@example.com}.
     */
    public static String maskEmail(@NotNull String email) {
        return email.replaceAll("(^.)[^@]+(.@.*)", "$1*****$2");
    }
}
