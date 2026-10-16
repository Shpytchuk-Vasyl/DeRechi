package org.shpytchuk.adminapi.form;

public enum RewardKind {

    NONE,
    NEGOTIABLE,
    AMOUNT;

    public static RewardKind of(Integer compensation) {
        if (compensation == null) {
            return NONE;
        }
        return compensation == 0 ? NEGOTIABLE : AMOUNT;
    }

    public Integer compensation(Integer amount) {
        return switch (this) {
            case NONE -> null;
            case NEGOTIABLE -> 0;
            case AMOUNT -> amount;
        };
    }
}
