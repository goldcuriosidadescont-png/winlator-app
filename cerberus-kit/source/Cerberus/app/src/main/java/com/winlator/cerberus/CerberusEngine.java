package com.winlator.cerberus;

import android.content.Context;

/**
 * Entry point for Cerberus-specific runtime logic.
 * ENGINE-1 is deliberately detection-only.
 */
public final class CerberusEngine {
    public static final String ENGINE_VERSION = "ENGINE-1";
    public static final String UPSTREAM_VERSION = "BrunoDev Winlator 11.2";
    public static final String UPSTREAM_COMMIT = "4f55d117fff1542944e5b91f433470445160ce08";

    private CerberusEngine() {}

    public static CerberusHardwareInfo inspectHardware(Context context) {
        return CerberusHardwareInfo.capture(context.getApplicationContext());
    }
}
