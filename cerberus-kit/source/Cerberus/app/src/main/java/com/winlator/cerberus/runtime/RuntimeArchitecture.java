package com.winlator.cerberus.runtime;

public enum RuntimeArchitecture {
    X86_64("x86_64", "x86_64"),
    ARM64EC("arm64ec", "ARM64EC");

    public final String id;
    public final String label;
    RuntimeArchitecture(String id, String label) { this.id = id; this.label = label; }

    public static RuntimeArchitecture fromId(String value) {
        for (RuntimeArchitecture item : values()) if (item.id.equalsIgnoreCase(value)) return item;
        return X86_64;
    }
}
