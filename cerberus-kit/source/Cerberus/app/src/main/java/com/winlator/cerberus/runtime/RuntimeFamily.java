package com.winlator.cerberus.runtime;

public enum RuntimeFamily {
    WINE("wine", "Wine"),
    PROTON("proton", "Proton");

    public final String id;
    public final String label;
    RuntimeFamily(String id, String label) { this.id = id; this.label = label; }

    public static RuntimeFamily fromId(String value) {
        for (RuntimeFamily item : values()) if (item.id.equalsIgnoreCase(value)) return item;
        return WINE;
    }
}
