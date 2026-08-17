package dev.totem.locksmith.domain;

public enum ContainerKind {
    CHEST("chest"),
    TRAPPED_CHEST("trapped_chest"),
    BARREL("barrel");

    private final String id;

    ContainerKind(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
