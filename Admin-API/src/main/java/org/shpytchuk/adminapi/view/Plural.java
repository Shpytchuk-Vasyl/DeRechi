package org.shpytchuk.adminapi.view;

/** Українські числові форми: 1 запис, 2 записи, 5 записів. */
public final class Plural {

    private Plural() {
    }

    public static String records(long count) {
        return count + " " + form(count, "запис", "записи", "записів");
    }

    static String form(long count, String one, String few, String many) {
        long absolute = Math.abs(count);
        long lastTwo = absolute % 100;
        long last = absolute % 10;

        if (lastTwo >= 11 && lastTwo <= 14) {
            return many;
        }
        if (last == 1) {
            return one;
        }
        if (last >= 2 && last <= 4) {
            return few;
        }
        return many;
    }
}
