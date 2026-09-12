package com.winlator;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Spinner;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import com.google.android.material.navigation.NavigationView;
import com.winlator.contentdialog.AboutDialog;
import com.winlator.core.AppUtils;
import com.winlator.core.Callback;
import com.winlator.core.LocaleHelper;
import com.winlator.core.GeneralComponents;
import com.winlator.core.DefaultVersion;
import com.winlator.core.PreloaderDialog;
import com.winlator.xenvironment.RootFSInstaller;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    public static final boolean DEBUG_MODE = false; // FIXME change to false
    public static final @IntRange(from = 1, to = 19) byte CONTAINER_PATTERN_COMPRESSION_LEVEL = 9;
    public static final byte PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE = 1;
    public static final byte OPEN_FILE_REQUEST_CODE = 2;
    public static final byte EDIT_INPUT_CONTROLS_REQUEST_CODE = 3;
    public static final byte OPEN_DIRECTORY_REQUEST_CODE = 4;
    private DrawerLayout drawerLayout;
    public final PreloaderDialog preloaderDialog = new PreloaderDialog(this);
    private boolean editInputControls = false;
    private int selectedProfileId;
    private Callback<Uri> openFileCallback;
    private SharedPreferences preferences;
    private Fragment currentFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppUtils.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        drawerLayout = findViewById(R.id.DrawerLayout);
        NavigationView navigationView = findViewById(R.id.NavigationView);
        navigationView.setNavigationItemSelectedListener(this);

        setSupportActionBar(findViewById(R.id.Toolbar));
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        Intent intent = getIntent();
        editInputControls = intent.getBooleanExtra("edit_input_controls", false);
        if (editInputControls) {
            selectedProfileId = intent.getIntExtra("selected_profile_id", 0);
            actionBar.setHomeAsUpIndicator(R.drawable.icon_action_bar_back);
            onNavigationItemSelected(navigationView.getMenu().findItem(R.id.menu_item_input_controls));
            navigationView.setCheckedItem(R.id.menu_item_input_controls);
        }
        else {
            boolean showShortcutsFirst = preferences.getBoolean("show_shortcuts_first", false);
            int selectedMenuItemId = intent.getIntExtra("selected_menu_item_id", 0);
            int menuItemId = selectedMenuItemId > 0 ? selectedMenuItemId : (showShortcutsFirst ? R.id.menu_item_shortcuts : R.id.menu_item_containers);

            actionBar.setHomeAsUpIndicator(R.drawable.icon_action_bar_menu);
            onNavigationItemSelected(navigationView.getMenu().findItem(menuItemId));
            navigationView.setCheckedItem(menuItemId);
            if (!requestAppPermissions()) RootFSInstaller.installIfNeeded(this);

            int containerId = intent.getIntExtra("container_id", 0);
            String startPath = intent.getStringExtra("start_path");
            if (containerId > 0 && startPath != null) {
                showFragment(new ContainerFileManagerFragment(containerId, startPath));
            }
        }
        cerberusLoadAdrenoCenter();
        cerberusSetNavState("cerberus_nav_home");
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setSystemLocale(newBase));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                RootFSInstaller.installIfNeeded(this);
            }
            else finish();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MainActivity.OPEN_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (openFileCallback != null) {
                openFileCallback.call(data.getData());
                openFileCallback = null;
            }
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if ((newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE ||
            newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) && currentFragment instanceof BaseFileManagerFragment) {
            ((BaseFileManagerFragment)currentFragment).onOrientationChanged();
        }
    }

    @Override
    public void onBackPressed() {
        if (currentFragment != null && currentFragment.isVisible()) {
            if (currentFragment instanceof BaseFileManagerFragment) {
                BaseFileManagerFragment fileManagerFragment = (BaseFileManagerFragment)currentFragment;
                if (fileManagerFragment.onBackPressed()) return;
            }
            else if (currentFragment instanceof ContainersFragment) {
                finish();
            }
        }

        showFragment(new ContainersFragment());
    }

    public void setOpenFileCallback(Callback<Uri> openFileCallback) {
        this.openFileCallback = openFileCallback;
    }

    private boolean requestAppPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) return false;

        String[] permissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};
        ActivityCompat.requestPermissions(this, permissions, PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        int itemId = menuItem.getItemId();
        if (itemId == R.id.menu_item_add ||
            itemId == R.id.menu_item_home ||
            itemId == R.id.menu_item_view_style ||
            itemId == R.id.menu_item_new_folder) {
            return super.onOptionsItemSelected(menuItem);
        }
        else {
            if (editInputControls) {
                setResult(RESULT_OK);
                finish();
            }
            else {
                if (currentFragment instanceof BaseFileManagerFragment) {
                    BaseFileManagerFragment fileManagerFragment = (BaseFileManagerFragment)currentFragment;
                    if (fileManagerFragment.onOptionsMenuClicked()) return true;
                }
                drawerLayout.openDrawer(GravityCompat.START);
            }
            return true;
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (fragmentManager.getBackStackEntryCount() > 0) {
            fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }

        switch (item.getItemId()) {
            case R.id.menu_item_shortcuts:
                preferences.edit().putBoolean("show_shortcuts_first", true).apply();
                showFragment(new ShortcutsFragment());
                break;
            case R.id.menu_item_containers:
                preferences.edit().putBoolean("show_shortcuts_first", false).apply();
                showFragment(new ContainersFragment());
                break;
            case R.id.menu_item_input_controls:
                showFragment(new InputControlsFragment(selectedProfileId));
                break;
            case R.id.menu_item_settings:
                showFragment(new SettingsFragment());
                break;
            case R.id.menu_item_about:
                (new AboutDialog(this)).show();
                break;
        }
        return true;
    }

    private View cerberusFindTaggedView(String tag) {
        View root = findViewById(android.R.id.content);
        return root != null ? root.findViewWithTag(tag) : null;
    }

    private Spinner cerberusGetSpinner(String tag) {
        View view = cerberusFindTaggedView(tag);
        return view instanceof Spinner ? (Spinner)view : null;
    }

    private void cerberusSetPanel(String tag) {
        View fragment = findViewById(R.id.FLFragmentContainer);
        View explore = cerberusFindTaggedView("cerberus_explore_panel");
        View adreno = cerberusFindTaggedView("cerberus_adreno_panel");
        if (fragment != null) fragment.setVisibility(tag == null ? View.VISIBLE : View.GONE);
        if (explore != null) explore.setVisibility("cerberus_explore_panel".equals(tag) ? View.VISIBLE : View.GONE);
        if (adreno != null) adreno.setVisibility("cerberus_adreno_panel".equals(tag) ? View.VISIBLE : View.GONE);
    }

    private void cerberusHidePanels() { cerberusSetPanel(null); }

    private void cerberusSetNavState(String selectedTag) {
        String[] tags = {"cerberus_nav_home", "cerberus_nav_games", "cerberus_nav_explore", "cerberus_nav_tools", "cerberus_nav_settings"};
        for (String tag : tags) {
            View v = cerberusFindTaggedView(tag);
            if (v != null) v.setSelected(tag.equals(selectedTag));
        }
    }

    private void cerberusNavigate(Fragment fragment, String navTag) {
        cerberusHidePanels();
        showFragment(fragment);
        cerberusSetNavState(navTag);
    }

    private void cerberusLoadComponent(GeneralComponents.Type type, String spinnerTag, String defaultValue) {
        Spinner spinner = cerberusGetSpinner(spinnerTag);
        if (spinner != null) GeneralComponents.loadSpinner(type, spinner, null, defaultValue);
    }

    private void cerberusLoadAdrenoCenter() {
        cerberusLoadComponent(GeneralComponents.Type.TURNIP, "cerberus_spinner_turnip", DefaultVersion.TURNIP);
        cerberusLoadComponent(GeneralComponents.Type.ADRENOTOOLS_DRIVER, "cerberus_spinner_adrenotools", "System");
        cerberusLoadComponent(GeneralComponents.Type.BOX64, "cerberus_spinner_box64", DefaultVersion.BOX64);
        cerberusLoadComponent(GeneralComponents.Type.DXVK, "cerberus_spinner_dxvk", DefaultVersion.MAJOR_DXVK);
        cerberusLoadComponent(GeneralComponents.Type.VKD3D, "cerberus_spinner_vkd3d", DefaultVersion.VKD3D);
        cerberusLoadComponent(GeneralComponents.Type.WINED3D, "cerberus_spinner_wined3d", DefaultVersion.WINED3D);
        Spinner fex = cerberusGetSpinner("cerberus_spinner_fexcore");
        if (fex != null) com.winlator.cerberus.runtime.CerberusCpuComponentRepository.loadInstalledSpinner(this, fex, com.winlator.cerberus.runtime.CerberusCpuComponentStore.TYPE_FEX);
        Spinner wow = cerberusGetSpinner("cerberus_spinner_wowbox64");
        if (wow != null) com.winlator.cerberus.runtime.CerberusCpuComponentRepository.loadInstalledSpinner(this, wow, com.winlator.cerberus.runtime.CerberusCpuComponentStore.TYPE_WOW);
        Spinner bdx = cerberusGetSpinner("cerberus_spinner_bionic_dxvk");
        if (bdx != null) com.winlator.cerberus.runtime.CerberusGuestComponentRepository.loadInstalledSpinner(this, bdx, com.winlator.cerberus.runtime.CerberusGuestComponentStore.TYPE_DXVK);
        Spinner bvk = cerberusGetSpinner("cerberus_spinner_bionic_vkd3d");
        if (bvk != null) com.winlator.cerberus.runtime.CerberusGuestComponentRepository.loadInstalledSpinner(this, bvk, com.winlator.cerberus.runtime.CerberusGuestComponentStore.TYPE_VKD3D);
        Spinner wine = cerberusGetSpinner("cerberus_spinner_wineproton");
        if (wine != null) com.winlator.cerberus.runtime.CerberusRuntimeRepository.loadInstalledSpinner(this, wine);
    }

    private void cerberusOpenUrl(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { AppUtils.showToast(this, "Não foi possível abrir o link."); }
    }

    public void cerberusOpenDrawer(View v) { drawerLayout.openDrawer(GravityCompat.START); }
    public void cerberusNavHome(View v) { cerberusNavigate(new ContainersFragment(), "cerberus_nav_home"); }
    public void cerberusNavGames(View v) { cerberusNavigate(new ShortcutsFragment(), "cerberus_nav_games"); }
    public void cerberusNavSettings(View v) { cerberusNavigate(new SettingsFragment(), "cerberus_nav_settings"); }
    public void cerberusNavTools(View v) { cerberusSetPanel("cerberus_explore_panel"); cerberusSetNavState("cerberus_nav_tools"); }
    public void cerberusNavTweaks(View v) { cerberusNavTools(v); }
    public void cerberusNavExplore(View v) { cerberusSetPanel("cerberus_explore_panel"); cerberusSetNavState("cerberus_nav_explore"); }
    public void cerberusShowAdrenoTools(View v) { cerberusSetPanel("cerberus_adreno_panel"); cerberusLoadAdrenoCenter(); }
    public void cerberusAdrenoBack(View v) { cerberusSetPanel("cerberus_explore_panel"); }
    public void cerberusOpenWinlatorComponents(View v) { cerberusShowAdrenoTools(v); }
    public void cerberusOpenWineSettings(View v) { cerberusNavigate(new SettingsFragment(), "cerberus_nav_settings"); }

    public void cerberusAdrenoTurnip(View v) { manageDownload(GeneralComponents.Type.TURNIP, "cerberus_spinner_turnip", DefaultVersion.TURNIP); }
    public void cerberusAdrenoTurnipFile(View v) { manageFile(GeneralComponents.Type.TURNIP, "cerberus_spinner_turnip", DefaultVersion.TURNIP); }
    public void cerberusAdrenoBox64(View v) { manageDownload(GeneralComponents.Type.BOX64, "cerberus_spinner_box64", DefaultVersion.BOX64); }
    public void cerberusAdrenoBox64File(View v) { manageFile(GeneralComponents.Type.BOX64, "cerberus_spinner_box64", DefaultVersion.BOX64); }
    public void cerberusAdrenoDXVK(View v) { manageDownload(GeneralComponents.Type.DXVK, "cerberus_spinner_dxvk", DefaultVersion.MAJOR_DXVK); }
    public void cerberusAdrenoDXVKFile(View v) { manageFile(GeneralComponents.Type.DXVK, "cerberus_spinner_dxvk", DefaultVersion.MAJOR_DXVK); }
    public void cerberusAdrenoVKD3D(View v) { manageDownload(GeneralComponents.Type.VKD3D, "cerberus_spinner_vkd3d", DefaultVersion.VKD3D); }
    public void cerberusAdrenoVKD3DFile(View v) { manageFile(GeneralComponents.Type.VKD3D, "cerberus_spinner_vkd3d", DefaultVersion.VKD3D); }
    public void cerberusAdrenoWineD3D(View v) { manageDownload(GeneralComponents.Type.WINED3D, "cerberus_spinner_wined3d", DefaultVersion.WINED3D); }
    public void cerberusAdrenoWineD3DFile(View v) { manageFile(GeneralComponents.Type.WINED3D, "cerberus_spinner_wined3d", DefaultVersion.WINED3D); }

    private void manageDownload(GeneralComponents.Type type, String tag, String def) {
        Spinner spinner = cerberusGetSpinner(tag);
        if (spinner != null) GeneralComponents.showDownloadableListDialog(type, spinner, def);
    }
    private void manageFile(GeneralComponents.Type type, String tag, String def) {
        Spinner spinner = cerberusGetSpinner(tag);
        if (spinner != null) GeneralComponents.openFileForInstall(this, type, spinner, def);
    }

    public void cerberusOpenOneUiReleases(View v) { cerberusOpenUrl("https://github.com/K11MCH1/AdrenoToolsDrivers/releases"); }
    public void cerberusAdrenoInstalledDrivers(View v) { cerberusLoadComponent(GeneralComponents.Type.ADRENOTOOLS_DRIVER, "cerberus_spinner_adrenotools", "System"); }
    public void cerberusAdrenoFexCore(View v) { com.winlator.cerberus.runtime.CerberusCpuComponentRepository.showRepository(this, cerberusGetSpinner("cerberus_spinner_fexcore"), com.winlator.cerberus.runtime.CerberusCpuComponentStore.TYPE_FEX); }
    public void cerberusAdrenoFexCoreInstalled(View v) { com.winlator.cerberus.runtime.CerberusCpuComponentRepository.showInstalled(this, com.winlator.cerberus.runtime.CerberusCpuComponentStore.TYPE_FEX); }
    public void cerberusAdrenoWowBox64(View v) { com.winlator.cerberus.runtime.CerberusCpuComponentRepository.showRepository(this, cerberusGetSpinner("cerberus_spinner_wowbox64"), com.winlator.cerberus.runtime.CerberusCpuComponentStore.TYPE_WOW); }
    public void cerberusAdrenoWowBox64Installed(View v) { com.winlator.cerberus.runtime.CerberusCpuComponentRepository.showInstalled(this, com.winlator.cerberus.runtime.CerberusCpuComponentStore.TYPE_WOW); }
    public void cerberusAdrenoBionicDXVK(View v) { com.winlator.cerberus.runtime.CerberusGuestComponentRepository.showRepository(this, cerberusGetSpinner("cerberus_spinner_bionic_dxvk"), com.winlator.cerberus.runtime.CerberusGuestComponentStore.TYPE_DXVK); }
    public void cerberusAdrenoBionicDXVKInstalled(View v) { com.winlator.cerberus.runtime.CerberusGuestComponentRepository.showInstalled(this, com.winlator.cerberus.runtime.CerberusGuestComponentStore.TYPE_DXVK); }
    public void cerberusAdrenoBionicVKD3D(View v) { com.winlator.cerberus.runtime.CerberusGuestComponentRepository.showRepository(this, cerberusGetSpinner("cerberus_spinner_bionic_vkd3d"), com.winlator.cerberus.runtime.CerberusGuestComponentStore.TYPE_VKD3D); }
    public void cerberusAdrenoBionicVKD3DInstalled(View v) { com.winlator.cerberus.runtime.CerberusGuestComponentRepository.showInstalled(this, com.winlator.cerberus.runtime.CerberusGuestComponentStore.TYPE_VKD3D); }
    public void cerberusAdrenoWineProton(View v) { com.winlator.cerberus.runtime.CerberusRuntimeRepository.showRepository(this, cerberusGetSpinner("cerberus_spinner_wineproton")); }
    public void cerberusAdrenoWineProtonInstalled(View v) { com.winlator.cerberus.runtime.CerberusRuntimeRepository.showInstalled(this); }
    public void cerberusAdrenoDXVKInstalled(View v) { cerberusLoadComponent(GeneralComponents.Type.DXVK, "cerberus_spinner_dxvk", DefaultVersion.MAJOR_DXVK); }

    public void showFragment(Fragment fragment) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction()
            .replace(R.id.FLFragmentContainer, fragment)
            .commit();

        drawerLayout.closeDrawer(GravityCompat.START);
        currentFragment = fragment;
    }
}
