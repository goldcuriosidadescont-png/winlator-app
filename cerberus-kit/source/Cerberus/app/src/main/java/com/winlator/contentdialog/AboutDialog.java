package com.winlator.contentdialog;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.TextView;

import com.winlator.R;
import com.winlator.cerberus.CerberusEngine;
import com.winlator.cerberus.CerberusHardwareInfo;

public class AboutDialog extends ContentDialog {
    public AboutDialog(Context context) {
        super(context, R.layout.about_dialog);
        findViewById(R.id.LLBottomBar).setVisibility(View.GONE);

        try {
            final PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);

            TextView tvWebpage = findViewById(R.id.TVWebpage);
            tvWebpage.setText(Html.fromHtml("<a href=\"https://github.com/brunodev85/winlator-app\">BrunoDev upstream source</a>", Html.FROM_HTML_MODE_LEGACY));
            tvWebpage.setMovementMethod(LinkMovementMethod.getInstance());

            ((TextView)findViewById(R.id.TVAppVersion)).setText(context.getString(R.string.version)+" "+pInfo.versionName);
            ((TextView)findViewById(R.id.TVCerberusFork)).setText(context.getString(R.string.cerberus_fork_label)+" • "+context.getPackageName());
            ((TextView)findViewById(R.id.TVCerberusUpstream)).setText(context.getString(R.string.cerberus_upstream_label)+": "+context.getString(R.string.cerberus_upstream_value));
            ((TextView)findViewById(R.id.TVCerberusCommit)).setText(context.getString(R.string.cerberus_base_commit_label)+": 4f55d117fff1542944e5b91f433470445160ce08");

            final CerberusHardwareInfo hardwareInfo = CerberusEngine.inspectHardware(context);
            ((TextView)findViewById(R.id.TVCerberusEngine)).setText(context.getString(R.string.cerberus_engine_label)+": "+CerberusEngine.ENGINE_VERSION+" (read-only)");
            ((TextView)findViewById(R.id.TVCerberusProfile)).setText(context.getString(R.string.cerberus_profile_label)+": "+hardwareInfo.profile.getId()+" — "+hardwareInfo.profile.getLabel());
            ((TextView)findViewById(R.id.TVCerberusSoc)).setText(context.getString(R.string.cerberus_soc_label)+": "+hardwareInfo.getSocDisplayName());
            ((TextView)findViewById(R.id.TVCerberusGpu)).setText(context.getString(R.string.cerberus_gpu_label)+": "+hardwareInfo.getGpuDisplayName());

            String creditsAndThirdPartyAppsHTML = String.join("<br />",
                "GLIBC Patches by (<a href=\"https://github.com/termux-pacman/glibc-packages\">Termux Pacman</a>)",
                "Wine (<a href=\"https://www.winehq.org\">winehq.org</a>)",
                "Box86/Box64 by <a href=\"https://github.com/ptitSeb\">ptitseb</a>",
                "Mesa (Turnip/Zink/VirGL) (<a href=\"https://www.mesa3d.org\">mesa3d.org</a>)",
                "DXVK (<a href=\"https://github.com/doitsujin/dxvk\">github.com/doitsujin/dxvk</a>)",
                "VKD3D (<a href=\"https://gitlab.winehq.org/wine/vkd3d\">gitlab.winehq.org/wine/vkd3d</a>)",
                "CNC DDraw (<a href=\"https://github.com/FunkyFr3sh/cnc-ddraw\">github.com/FunkyFr3sh/cnc-ddraw</a>)"
            );

            TextView tvCreditsAndThirdPartyApps = findViewById(R.id.TVCreditsAndThirdPartyApps);
            tvCreditsAndThirdPartyApps.setText(Html.fromHtml(creditsAndThirdPartyAppsHTML, Html.FROM_HTML_MODE_LEGACY));
            tvCreditsAndThirdPartyApps.setMovementMethod(LinkMovementMethod.getInstance());
        }
        catch (PackageManager.NameNotFoundException e) {}
    }
}
