# Cerberus Winlator 0.1.0-BASE

Upstream: BrunoDev Winlator 11.2

- Source repository: `https://github.com/brunodev85/winlator-app`
- Pinned upstream commit: `4f55d117fff1542944e5b91f433470445160ce08`
- Internal namespace retained: `com.winlator`
- External Android applicationId: `com.cerberus.winlator`
- Version code: `1`
- Version name: `0.1.0-BASE`

## BASE invariants

This branch intentionally keeps the upstream Java package/namespace intact. Only the externally visible Android identity and package-bound runtime paths are changed. This minimizes risk in JNI, custom views, reflection and native integration while allowing the Cerberus fork to coexist with the upstream app.

The following package-bound paths were migrated to the Cerberus applicationId:

- Winlator native cache path
- Gladio X11 socket path
- Vortek socket path
- Java internal storage path
- Android FileProvider authority

No Box64, Wine, Turnip, DXVK, VKD3D, Vortek, Gladio, CPU-affinity or performance policy has been modified in this BASE build.
