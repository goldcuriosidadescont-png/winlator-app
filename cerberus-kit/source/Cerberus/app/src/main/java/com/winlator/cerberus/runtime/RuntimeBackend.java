package com.winlator.cerberus.runtime;

public enum RuntimeBackend {
    BOX64("box64", "Box64"),
    FEXCORE("fexcore", "FEXCore"),
    WOWBOX64("wowbox64", "WOWBox64");

    public final String id;
    public final String label;
    RuntimeBackend(String id, String label) { this.id = id; this.label = label; }

    public static RuntimeBackend fromId(String value, RuntimeBackend fallback) {
        for (RuntimeBackend item : values()) if (item.id.equalsIgnoreCase(value)) return item;
        return fallback;
    }
}
