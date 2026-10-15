package org.shpytchuk.clientapi.entity;

public enum ItemKind {

    LOST("lost", "LostItem"),
    FOUND("found", "FoundItem");

    private final String label;
    private final String entityName;

    ItemKind(String label, String entityName) {
        this.label = label;
        this.entityName = entityName;
    }

    public String label() {
        return label;
    }

    public String entityName() {
        return entityName;
    }

    public String claimEntityName() {
        return entityName + "Claim";
    }

    /**
     * {@code notice.<kind>.<verb>} and {@code claim.<kind>.<verb>}. The keys are a contract with Worker and
     * {@code docker/rabbitmq/definitions.json}, so the shape is fixed here and nowhere else.
     */
    public String noticeKey(String verb) {
        return "notice." + label + "." + verb;
    }

    public String claimKey(String verb) {
        return "claim." + label + "." + verb;
    }
}
