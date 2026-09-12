package com.winlator.cerberus.runtime;

public enum RuntimeEngine {
    CLASSIC("classic", "Classic GLIBC"),
    BIONIC("bionic", "Bionic");

    public final String id;
    public final String label;
    RuntimeEngine(String id, String label) { this.id = id; this.label = label; }

    public static RuntimeEngine fromId(String value) {
        for (RuntimeEngine item : values()) if (item.id.equalsIgnoreCase(value)) return item;
        return CLASSIC;
    }
}
