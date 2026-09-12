package com.winlator.cerberus;

import java.util.Locale;

/** Pure-Java profile resolver, intentionally independent of Android APIs. */
public final class CerberusProfileResolver {
    private CerberusProfileResolver() {}

    public static CerberusDeviceProfile resolve(
            String socModel,
            String board,
            String hardware,
            String gpuRenderer,
            short adrenoModel) {
        final String fingerprint = (
                safe(socModel) + " " + safe(board) + " " + safe(hardware))
                .toLowerCase(Locale.US);

        if (fingerprint.contains("sm8550") || adrenoModel == 740) {
            return CerberusDeviceProfile.SM8550;
        }

        if (adrenoModel >= 700 && adrenoModel < 800) {
            return CerberusDeviceProfile.ADRENO_7XX;
        }

        if (adrenoModel > 0 || safe(gpuRenderer).toLowerCase(Locale.US).contains("adreno")) {
            return CerberusDeviceProfile.ADRENO;
        }

        return CerberusDeviceProfile.GENERIC;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
