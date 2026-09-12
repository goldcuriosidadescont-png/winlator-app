package com.winlator.cerberus;

/**
 * Read-only device classification used by the Cerberus runtime.
 * No performance policy is applied at this stage.
 */
public enum CerberusDeviceProfile {
    SM8550("SM8550", "Snapdragon 8 Gen 2 / Adreno 740"),
    ADRENO_7XX("ADRENO_7XX", "Generic Adreno 7xx"),
    ADRENO("ADRENO", "Generic Adreno"),
    GENERIC("GENERIC", "Generic Android device");

    private final String id;
    private final String label;

    CerberusDeviceProfile(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }
}
