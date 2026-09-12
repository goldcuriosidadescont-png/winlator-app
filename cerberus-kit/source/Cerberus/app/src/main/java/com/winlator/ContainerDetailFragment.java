package com.winlator;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;

import com.winlator.box64.Box64Preset;
import com.winlator.box64.Box64PresetManager;
import com.winlator.cerberus.runtime.CerberusCompatibilityMatrix;
import com.winlator.cerberus.runtime.CerberusContainerRuntime;
import com.winlator.cerberus.runtime.CerberusCpuComponentStore;
import com.winlator.cerberus.runtime.CerberusGuestComponentStore;
import com.winlator.cerberus.runtime.CerberusPrefixManager;
import com.winlator.cerberus.runtime.CerberusRuntimeProfile;
import com.winlator.cerberus.runtime.CerberusRuntimeResolver;
import com.winlator.cerberus.runtime.CerberusRuntimeStore;
import com.winlator.cerberus.runtime.RuntimeArchitecture;
import com.winlator.cerberus.runtime.RuntimeBackend;
import com.winlator.cerberus.runtime.RuntimeEngine;
import com.winlator.cerberus.runtime.RuntimeFamily;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.container.Drive;
import com.winlator.container.GraphicsDrivers;
import com.winlator.contentdialog.AddEnvVarDialog;
import com.winlator.contentdialog.AudioDriverConfigDialog;
import com.winlator.contentdialog.ContentDialog;
import com.winlator.contentdialog.VortekConfigDialog;
import com.winlator.core.AppUtils;
import com.winlator.core.Callback;
import com.winlator.container.DXWrapperPicker;
import com.winlator.core.EnvVars;
import com.winlator.core.FileUtils;
import com.winlator.container.GraphicsDriverPicker;
import com.winlator.core.KeyValueSet;
import com.winlator.core.PreloaderDialog;
import com.winlator.core.StringUtils;
import com.winlator.core.WineInfo;
import com.winlator.core.WineInstaller;
import com.winlator.core.WineRegistryEditor;
import com.winlator.core.WineThemeManager;
import com.winlator.core.WineUtils;
import com.winlator.widget.CPUListView;
import com.winlator.widget.ColorPickerView;
import com.winlator.widget.EnvVarsView;
import com.winlator.widget.FrameRating;
import com.winlator.widget.ImagePickerView;
import com.winlator.widget.SeekBar;
import com.winlator.win32.MSLogFont;
import com.winlator.win32.WinVersions;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ContainerDetailFragment extends Fragment {
    private ContainerManager manager;
    private final int containerId;
    private Container container;
    private PreloaderDialog preloaderDialog;
    private Callback<String> openDirectoryCallback;

    public ContainerDetailFragment() {
        this(0);
    }

    public ContainerDetailFragment(int containerId) {
        this.containerId = containerId;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false);
        preloaderDialog = new PreloaderDialog(getActivity());
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == MainActivity.OPEN_DIRECTORY_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                String path = FileUtils.getFilePathFromUri(data.getData());
                if (path != null && openDirectoryCallback != null) openDirectoryCallback.call(path);
            }
            openDirectoryCallback = null;
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(isEditMode() ? R.string.edit_container : R.string.new_container);
    }

    public boolean isEditMode() {
        return container != null;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup root, @Nullable Bundle savedInstanceState) {
        final Context context = getContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        final View view = inflater.inflate(R.layout.container_detail_fragment, root, false);
        manager = new ContainerManager(context);
        container = containerId > 0 ? manager.getContainerById(containerId) : null;

        final EditText etName = view.findViewById(R.id.ETName);

        if (isEditMode()) {
            etName.setText(container.getName());
        }
        else etName.setText(getString(R.string.container)+"-"+manager.getNextContainerId());

        final ArrayList<WineInfo> wineInfos = WineInstaller.getInstalledWineInfos(context);
        final Spinner sWineVersion = view.findViewById(R.id.SWineVersion);
        if (wineInfos.size() > 1) loadWineVersionSpinner(view, sWineVersion, wineInfos);

        final Spinner sRuntimeEngine = view.findViewById(R.id.SCerberusRuntimeEngine);
        final Spinner sRuntimeFamily = view.findViewById(R.id.SCerberusRuntimeFamily);
        final Spinner sRuntimeArch = view.findViewById(R.id.SCerberusRuntimeArch);
        final Spinner sBackend32 = view.findViewById(R.id.SCerberusBackend32);
        final View llBackend32 = view.findViewById(R.id.LLCerberusBackend32);
        final TextView tvRuntimeStatus = view.findViewById(R.id.TVCerberusRuntimeStatus);
        CerberusRuntimeProfile initialRuntime = isEditMode()
                ? CerberusContainerRuntime.read(container)
                : CerberusRuntimeResolver.resolve(RuntimeEngine.CLASSIC, RuntimeFamily.WINE, RuntimeArchitecture.X86_64, RuntimeBackend.FEXCORE);
        setupCerberusRuntimeUI(sRuntimeEngine, sRuntimeFamily, sRuntimeArch, sBackend32, llBackend32, tvRuntimeStatus, initialRuntime);
        final Spinner sRuntimePackage = view.findViewById(R.id.SCerberusRuntimePackage);
        final ArrayList<CerberusRuntimeStore.Info> installedRuntimes = CerberusRuntimeStore.list(context);
        ArrayList<String> runtimeLabels = new ArrayList<>();
        runtimeLabels.add("Bionic embutido");
        int runtimeSelection = 0;
        for (int i = 0; i < installedRuntimes.size(); i++) {
            CerberusRuntimeStore.Info info = installedRuntimes.get(i);
            runtimeLabels.add(info.toString());
            if (info.id.equals(initialRuntime.runtimeId)) runtimeSelection = i + 1;
        }
        sRuntimePackage.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, runtimeLabels));
        sRuntimePackage.setSelection(runtimeSelection);

        final Spinner sFexcoreVersion = view.findViewById(R.id.SCerberusFexcoreVersion);
        final Spinner sWowVersion = view.findViewById(R.id.SCerberusWowVersion);
        final ArrayList<CerberusCpuComponentStore.Info> installedFex = CerberusCpuComponentStore.list(context, CerberusCpuComponentStore.TYPE_FEX);
        final ArrayList<CerberusCpuComponentStore.Info> installedWow = CerberusCpuComponentStore.list(context, CerberusCpuComponentStore.TYPE_WOW);
        ArrayList<String> fexLabels = new ArrayList<>(); fexLabels.add("Automático / empacotado");
        int fexSelection = 0;
        for (int i = 0; i < installedFex.size(); i++) {
            fexLabels.add(installedFex.get(i).toString());
            if (installedFex.get(i).id.equals(initialRuntime.fexcoreVersion)) fexSelection = i + 1;
        }
        sFexcoreVersion.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, fexLabels));
        sFexcoreVersion.setSelection(fexSelection);
        ArrayList<String> wowLabels = new ArrayList<>(); wowLabels.add("Automático / empacotado");
        int wowSelection = 0;
        for (int i = 0; i < installedWow.size(); i++) {
            wowLabels.add(installedWow.get(i).toString());
            if (installedWow.get(i).id.equals(initialRuntime.wowbox64Version)) wowSelection = i + 1;
        }
        sWowVersion.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, wowLabels));
        sWowVersion.setSelection(wowSelection);

        final Spinner sBionicDxvk = view.findViewById(R.id.SCerberusBionicDXVK);
        final Spinner sBionicVkd3d = view.findViewById(R.id.SCerberusBionicVKD3D);
        final ArrayList<CerberusGuestComponentStore.Info> installedBionicDxvk = CerberusGuestComponentStore.list(context, CerberusGuestComponentStore.TYPE_DXVK);
        final ArrayList<CerberusGuestComponentStore.Info> installedBionicVkd3d = CerberusGuestComponentStore.list(context, CerberusGuestComponentStore.TYPE_VKD3D);
        ArrayList<String> bdxLabels = new ArrayList<>(); bdxLabels.add("Container pattern / padrão");
        int bdxSelection = 0;
        for (int i = 0; i < installedBionicDxvk.size(); i++) { bdxLabels.add(installedBionicDxvk.get(i).toString()); if (installedBionicDxvk.get(i).id.equals(initialRuntime.dxvkVersion)) bdxSelection = i + 1; }
        sBionicDxvk.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, bdxLabels)); sBionicDxvk.setSelection(bdxSelection);
        ArrayList<String> bvkLabels = new ArrayList<>(); bvkLabels.add("Container pattern / padrão");
        int bvkSelection = 0;
        for (int i = 0; i < installedBionicVkd3d.size(); i++) { bvkLabels.add(installedBionicVkd3d.get(i).toString()); if (installedBionicVkd3d.get(i).id.equals(initialRuntime.vkd3dVersion)) bvkSelection = i + 1; }
        sBionicVkd3d.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, bvkLabels)); sBionicVkd3d.setSelection(bvkSelection);

        loadScreenSizeSpinner(view, isEditMode() ? container.getScreenSize() : Container.DEFAULT_SCREEN_SIZE);

        final String oldGraphicsDriverConfig = isEditMode() ? container.getGraphicsDriverConfig() : "";
        String selectedGraphicsDriver = isEditMode() ? container.getGraphicsDriver() : GraphicsDrivers.getDefaultDriver(context);
        GraphicsDriverPicker graphicsDriverPicker = new GraphicsDriverPicker(view.findViewById(R.id.LLGraphicsDriver), selectedGraphicsDriver, oldGraphicsDriverConfig);

        String oldDXWrapperConfig = isEditMode() ? container.getDXWrapperConfig() : "";
        String selectedDXWrapper = isEditMode() ? container.getDXWrapper() : Container.DEFAULT_DXWRAPPER;
        DXWrapperPicker dxwrapperPicker = new DXWrapperPicker(view.findViewById(R.id.LLDXWrapper), graphicsDriverPicker, selectedDXWrapper, oldDXWrapperConfig);

        view.findViewById(R.id.BTHelpDXWrapper).setOnClickListener((v) -> AppUtils.showHelpBox(context, v, R.string.dxwrapper_help_content));

        Spinner sAudioDriver = view.findViewById(R.id.SAudioDriver);
        AppUtils.setSpinnerSelectionFromIdentifier(sAudioDriver, isEditMode() ? container.getAudioDriver() : Container.DEFAULT_AUDIO_DRIVER);

        final View vAudioDriverConfig = view.findViewById(R.id.BTAudioDriverConfig);
        vAudioDriverConfig.setTag(isEditMode() ? container.getAudioDriverConfig() : "");
        vAudioDriverConfig.setOnClickListener((v) -> (new AudioDriverConfigDialog(v)).show());

        final Spinner sHUDMode = view.findViewById(R.id.SHUDMode);
        sHUDMode.setSelection(isEditMode() ? container.getHUDMode() : FrameRating.Mode.DISABLED.ordinal());

        final Spinner sStartupSelection = view.findViewById(R.id.SStartupSelection);
        byte oldStartupSelection = isEditMode() ? container.getStartupSelection() : -1;
        sStartupSelection.setSelection(oldStartupSelection != -1 ? oldStartupSelection : Container.STARTUP_SELECTION_ESSENTIAL);

        final Spinner sWinVersion = view.findViewById(R.id.SWinVersion);
        sWinVersion.setTag((byte)-1);

        final Spinner sBox64Preset = view.findViewById(R.id.SBox64Preset);
        Box64PresetManager.loadSpinner(sBox64Preset, isEditMode() ? container.getBox64Preset() : preferences.getString("box64_preset", Box64Preset.DEFAULT));

        final CPUListView cpuListView = view.findViewById(R.id.CPUListView);
        final CPUListView cpuListViewWoW64 = view.findViewById(R.id.CPUListViewWoW64);

        cpuListView.setCheckedCPUList(isEditMode() ? container.getCPUList(true) : Container.getFallbackCPUList());
        cpuListViewWoW64.setCheckedCPUList(isEditMode() ? container.getCPUListWoW64(true) : Container.getFallbackCPUList());

        createWineConfigurationTab(view);
        final EnvVarsView envVarsView = createEnvVarsTab(view);
        createWinComponentsTab(view, isEditMode() ? container.getWinComponents() : Container.DEFAULT_WINCOMPONENTS);
        createDrivesTab(view);

        AppUtils.setupTabLayout(view, R.id.TabLayout, (tabResId) -> {
            if (tabResId == R.id.LLTabAdvanced) if ((byte)sWinVersion.getTag() == -1) WinVersions.loadSpinner(container, sWinVersion);
        }, R.id.LLTabWineConfiguration, R.id.LLTabWinComponents, R.id.LLTabEnvVars, R.id.LLTabDrives, R.id.LLTabAdvanced);

        view.findViewById(R.id.BTConfirm).setOnClickListener((v) -> {
            try {
                String name = etName.getText().toString();
                String screenSize = getScreenSize(view);
                String envVars = envVarsView.getEnvVars();
                String graphicsDriver = graphicsDriverPicker.getGraphicsDriver();
                String dxwrapper = dxwrapperPicker.getDXWrapper();
                String dxwrapperConfig = dxwrapperPicker.getDXWrapperConfig();
                String graphicsDriverConfig = graphicsDriverPicker.getGraphicsDriverConfig();
                String audioDriverConfig = vAudioDriverConfig.getTag().toString();
                String audioDriver = StringUtils.parseIdentifier(sAudioDriver.getSelectedItem());
                String wincomponents = getWinComponents(view);
                String drives = getDrives(view);
                byte hudMode = (byte)sHUDMode.getSelectedItemPosition();
                String cpuList = cpuListView.getCheckedCPUListAsString();
                String cpuListWoW64 = cpuListViewWoW64.getCheckedCPUListAsString();
                byte startupSelection = (byte)sStartupSelection.getSelectedItemPosition();
                String box64Preset = Box64PresetManager.getSpinnerSelectedId(sBox64Preset);
                String desktopTheme = getDesktopTheme(view);
                CerberusRuntimeProfile pendingRuntime = readCerberusRuntimeUI(sRuntimeEngine, sRuntimeFamily, sRuntimeArch, sBackend32);
                if (pendingRuntime.isBionic()) {
                    int rp = sRuntimePackage.getSelectedItemPosition();
                    CerberusRuntimeStore.Info selectedRuntime = rp > 0 && rp - 1 < installedRuntimes.size() ? installedRuntimes.get(rp - 1) : null;
                    pendingRuntime.runtimeId = selectedRuntime != null ? selectedRuntime.id : "builtin-bionic";
                    if (selectedRuntime == null && pendingRuntime.family == RuntimeFamily.PROTON) {
                        AppUtils.showToast(context, "Proton Bionic requer um runtime Proton WCP instalado. 'Bionic embutido' fornece somente Wine.");
                        return;
                    }
                    if (selectedRuntime != null) {
                        if (!selectedRuntime.type.equalsIgnoreCase(pendingRuntime.family.label)) {
                            AppUtils.showToast(context, "Runtime selecionado é " + selectedRuntime.type + ", mas o perfil está em " + pendingRuntime.family.label + ".");
                            return;
                        }
                        if (!selectedRuntime.architecture.equalsIgnoreCase(pendingRuntime.architecture.id)) {
                            AppUtils.showToast(context, "Arquitetura do runtime (" + selectedRuntime.architecture + ") não corresponde ao perfil " + pendingRuntime.architecture.label + ".");
                            return;
                        }
                    }
                    int dp = sBionicDxvk.getSelectedItemPosition();
                    pendingRuntime.dxvkVersion = dp > 0 && dp - 1 < installedBionicDxvk.size() ? installedBionicDxvk.get(dp - 1).id : "";
                    if (!pendingRuntime.dxvkVersion.isEmpty() && !installedBionicDxvk.get(dp - 1).architecture.equalsIgnoreCase(pendingRuntime.architecture.id)) {
                        AppUtils.showToast(context, "DXVK Bionic selecionado não corresponde à arquitetura " + pendingRuntime.architecture.label + ".");
                        return;
                    }
                    int vp = sBionicVkd3d.getSelectedItemPosition();
                    pendingRuntime.vkd3dVersion = vp > 0 && vp - 1 < installedBionicVkd3d.size() ? installedBionicVkd3d.get(vp - 1).id : "";
                    if (!pendingRuntime.vkd3dVersion.isEmpty() && !installedBionicVkd3d.get(vp - 1).architecture.equalsIgnoreCase(pendingRuntime.architecture.id)) {
                        AppUtils.showToast(context, "VKD3D Bionic selecionado não corresponde à arquitetura " + pendingRuntime.architecture.label + ".");
                        return;
                    }
                    if (pendingRuntime.isArm64ec()) {
                        int fp = sFexcoreVersion.getSelectedItemPosition();
                        pendingRuntime.fexcoreVersion = fp > 0 && fp - 1 < installedFex.size() ? installedFex.get(fp - 1).id : "";
                        if (pendingRuntime.backend32 == RuntimeBackend.WOWBOX64) {
                            int wp = sWowVersion.getSelectedItemPosition();
                            pendingRuntime.wowbox64Version = wp > 0 && wp - 1 < installedWow.size() ? installedWow.get(wp - 1).id : "";
                        }
                    }
                }
                else {
                    // Classic remains the legacy GLIBC/Wine engine. Proton packages belong to the Bionic runtime store.
                    if (pendingRuntime.family == RuntimeFamily.PROTON) {
                        AppUtils.showToast(context, "Proton usa o engine Bionic nesta versão. Selecione Bionic e um runtime Proton WCP.");
                        return;
                    }
                    int winePosition = sWineVersion.getSelectedItemPosition();
                    pendingRuntime.runtimeId = winePosition >= 0 && winePosition < wineInfos.size()
                            ? wineInfos.get(winePosition).identifier()
                            : WineInfo.MAIN_WINE_INFO.identifier();
                }
                String runtimeError = CerberusCompatibilityMatrix.validate(pendingRuntime);
                if (runtimeError != null) {
                    AppUtils.showToast(context, runtimeError);
                    return;
                }

                if (isEditMode()) {
                    // Runtime change commits first. If prefix preparation fails, no unrelated container field is mutated.
                    CerberusRuntimeProfile oldRuntime = CerberusContainerRuntime.read(container);
                    if (CerberusPrefixManager.requiresFreshPrefix(oldRuntime, pendingRuntime)) {
                        if (!CerberusPrefixManager.prepareRuntimeChange(context, container, oldRuntime, pendingRuntime)) {
                            AppUtils.showToast(context, "Falha ao criar prefix limpo para a nova engine/arquitetura. O prefix anterior foi preservado.");
                            return;
                        }
                    }

                    container.setName(name);
                    container.setScreenSize(screenSize);
                    container.setEnvVars(envVars);
                    container.setCPUList(cpuList);
                    container.setCPUListWoW64(cpuListWoW64);
                    container.setGraphicsDriver(graphicsDriver);
                    container.setDXWrapper(dxwrapper);
                    container.setDXWrapperConfig(dxwrapperConfig);
                    container.setGraphicsDriverConfig(graphicsDriverConfig);
                    container.setAudioDriver(audioDriver);
                    container.setAudioDriverConfig(audioDriverConfig);
                    container.setWinComponents(wincomponents);
                    container.setDrives(drives);
                    container.setHUDMode(hudMode);
                    container.setStartupSelection(startupSelection);
                    container.setBox64Preset(box64Preset);
                    container.setDesktopTheme(desktopTheme);
                    CerberusContainerRuntime.write(container, pendingRuntime);
                    container.saveData();

                    saveWineRegistryKeys(view);

                    boolean requireRestart = graphicsDriver.equals(GraphicsDrivers.VORTEK) && VortekConfigDialog.isRequireRestart(oldGraphicsDriverConfig, graphicsDriverConfig);
                    if (requireRestart) ContentDialog.confirm(context, R.string.the_settings_have_been_changed_do_you_want_to_restart_the_app, () -> AppUtils.restartApplication(context));

                    getActivity().onBackPressed();
                }
                else {
                    JSONObject data = new JSONObject();
                    data.put("name", name);
                    data.put("screenSize", screenSize);
                    data.put("envVars", envVars);
                    data.put("cpuList", cpuList);
                    data.put("cpuListWoW64", cpuListWoW64);
                    data.put("graphicsDriver", graphicsDriver);
                    data.put("dxwrapper", dxwrapper);
                    data.put("dxwrapperConfig", dxwrapperConfig);
                    data.put("graphicsDriverConfig", graphicsDriverConfig);
                    data.put("audioDriver", audioDriver);
                    data.put("audioDriverConfig", audioDriverConfig);
                    data.put("wincomponents", wincomponents);
                    data.put("drives", drives);
                    data.put("hudMode", hudMode);
                    data.put("startupSelection", startupSelection);
                    data.put("box64Preset", box64Preset);
                    data.put("desktopTheme", desktopTheme);
                    data.put("extraData", CerberusContainerRuntime.toJson(pendingRuntime));

                    if (wineInfos.size() > 1) {
                        data.put("wineVersion", wineInfos.get(sWineVersion.getSelectedItemPosition()).identifier());
                    }

                    preloaderDialog.show(R.string.creating_container);
                    manager.createContainerAsync(data, (container) -> {
                        if (container != null) {
                            this.container = container;
                            saveWineRegistryKeys(view);
                        }
                        preloaderDialog.close();
                        getActivity().onBackPressed();
                    });
                }
            }
            catch (JSONException e) {}
        });
        return view;
    }

    private void setupCerberusRuntimeUI(final Spinner engine, final Spinner family, final Spinner arch, final Spinner backend32,
            final View backend32Panel, final TextView status, CerberusRuntimeProfile initial) {
        engine.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item,
                new String[]{RuntimeEngine.CLASSIC.label, RuntimeEngine.BIONIC.label}));
        family.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item,
                new String[]{RuntimeFamily.WINE.label, RuntimeFamily.PROTON.label}));
        arch.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item,
                new String[]{RuntimeArchitecture.X86_64.label, RuntimeArchitecture.ARM64EC.label}));
        backend32.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item,
                new String[]{RuntimeBackend.FEXCORE.label, RuntimeBackend.WOWBOX64.label}));

        engine.setSelection(initial.engine == RuntimeEngine.BIONIC ? 1 : 0);
        family.setSelection(initial.family == RuntimeFamily.PROTON ? 1 : 0);
        arch.setSelection(initial.architecture == RuntimeArchitecture.ARM64EC ? 1 : 0);
        backend32.setSelection(initial.backend32 == RuntimeBackend.WOWBOX64 ? 1 : 0);

        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateCerberusRuntimeUI(engine, family, arch, backend32, backend32Panel, status);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        };
        engine.setOnItemSelectedListener(listener);
        family.setOnItemSelectedListener(listener);
        arch.setOnItemSelectedListener(listener);
        backend32.setOnItemSelectedListener(listener);
        updateCerberusRuntimeUI(engine, family, arch, backend32, backend32Panel, status);
    }

    private CerberusRuntimeProfile readCerberusRuntimeUI(Spinner engine, Spinner family, Spinner arch, Spinner backend32) {
        RuntimeEngine e = engine.getSelectedItemPosition() == 1 ? RuntimeEngine.BIONIC : RuntimeEngine.CLASSIC;
        RuntimeFamily f = family.getSelectedItemPosition() == 1 ? RuntimeFamily.PROTON : RuntimeFamily.WINE;
        RuntimeArchitecture a = arch.getSelectedItemPosition() == 1 ? RuntimeArchitecture.ARM64EC : RuntimeArchitecture.X86_64;
        RuntimeBackend b32 = backend32.getSelectedItemPosition() == 1 ? RuntimeBackend.WOWBOX64 : RuntimeBackend.FEXCORE;
        return CerberusRuntimeResolver.resolve(e, f, a, b32);
    }

    private void updateCerberusRuntimeUI(Spinner engine, Spinner family, Spinner arch, Spinner backend32, View backend32Panel, TextView status) {
        CerberusRuntimeProfile p = readCerberusRuntimeUI(engine, family, arch, backend32);
        boolean arm = p.engine == RuntimeEngine.BIONIC && p.architecture == RuntimeArchitecture.ARM64EC;
        backend32Panel.setVisibility(arm ? View.VISIBLE : View.GONE);
        View cpuPanel = engine.getRootView().findViewById(R.id.LLCerberusCpuComponents);
        View wowPanel = engine.getRootView().findViewById(R.id.LLCerberusWowVersion);
        View bionicGraphics = engine.getRootView().findViewById(R.id.LLCerberusBionicGraphics);
        if (bionicGraphics != null) bionicGraphics.setVisibility(p.engine == RuntimeEngine.BIONIC ? View.VISIBLE : View.GONE);
        if (cpuPanel != null) cpuPanel.setVisibility(arm ? View.VISIBLE : View.GONE);
        if (wowPanel != null) wowPanel.setVisibility(arm && p.backend32 == RuntimeBackend.WOWBOX64 ? View.VISIBLE : View.GONE);
        View runtimePackagePanel = engine.getRootView().findViewById(R.id.LLCerberusRuntimePackage);
        if (runtimePackagePanel != null) runtimePackagePanel.setVisibility(p.engine == RuntimeEngine.BIONIC ? View.VISIBLE : View.GONE);
        if (p.engine == RuntimeEngine.CLASSIC && arch.getSelectedItemPosition() != 0) arch.setSelection(0);
        String text = p.engine.label + " • " + p.family.label + " • " + p.architecture.label + "\nBackend64: " + p.backend64.label + " • Backend32: " + p.backend32.label;
        status.setText(text);
    }

    private void saveWineRegistryKeys(View view) {
        File userRegFile = new File(container.getRootDir(), ".wine/user.reg");
        try (WineRegistryEditor registryEditor = new WineRegistryEditor(userRegFile)) {
            Spinner sSystemFont = view.findViewById(R.id.SSystemFont);
            WineUtils.setSystemFont(registryEditor, sSystemFont.getSelectedItem().toString());

            SeekBar sbLogPixels = view.findViewById(R.id.SBLogPixels);
            registryEditor.setDwordValue("Control Panel\\Desktop", "LogPixels", (int)sbLogPixels.getValue());

            Spinner sMouseWarpOverride = view.findViewById(R.id.SMouseWarpOverride);

            final String[] mouseWarpOverrideValues = new String[]{"disable", "enable", "force"};
            registryEditor.setStringValue("Software\\Wine\\DirectInput", "MouseWarpOverride", mouseWarpOverrideValues[sMouseWarpOverride.getSelectedItemPosition()]);

            registryEditor.setStringValue("Software\\Wine\\Direct3D", "shader_backend", "glsl");
            registryEditor.setStringValue("Software\\Wine\\Direct3D", "UseGLSL", "enabled");
        }

        Spinner sWinVersion = view.findViewById(R.id.SWinVersion);
        int oldPosition = (byte)sWinVersion.getTag();
        if (oldPosition != -1) {
            int newPosition = sWinVersion.getSelectedItemPosition();
            if (oldPosition != newPosition) WineUtils.setWinVersion(container, newPosition);
        }
    }

    private void createWineConfigurationTab(View view) {
        Context context = getContext();

        WineThemeManager.ThemeInfo desktopTheme = new WineThemeManager.ThemeInfo(isEditMode() ? container.getDesktopTheme() : WineThemeManager.DEFAULT_DESKTOP_THEME);
        RadioGroup rgDesktopTheme = view.findViewById(R.id.RGDesktopTheme);
        rgDesktopTheme.check(desktopTheme.theme == WineThemeManager.Theme.LIGHT ? R.id.RBLight : R.id.RBDark);
        final ImagePickerView ipvDesktopBackgroundImage = view.findViewById(R.id.IPVDesktopBackgroundImage);
        ipvDesktopBackgroundImage.setSelectedSource(desktopTheme.wallpaperId);
        final ColorPickerView cpvDesktopBackgroundColor = view.findViewById(R.id.CPVDesktopBackgroundColor);
        cpvDesktopBackgroundColor.setColor(desktopTheme.backgroundColor);

        Spinner sDesktopBackgroundType = view.findViewById(R.id.SDesktopBackgroundType);
        sDesktopBackgroundType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                WineThemeManager.BackgroundType type = WineThemeManager.BackgroundType.values()[position];
                ipvDesktopBackgroundImage.setVisibility(View.GONE);
                cpvDesktopBackgroundColor.setVisibility(View.GONE);

                if (type == WineThemeManager.BackgroundType.IMAGE) {
                    ipvDesktopBackgroundImage.setVisibility(View.VISIBLE);
                }
                else if (type == WineThemeManager.BackgroundType.COLOR) {
                    cpvDesktopBackgroundColor.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        sDesktopBackgroundType.setSelection(desktopTheme.backgroundType.ordinal());

        File containerDir = isEditMode() ? container.getRootDir() : null;
        File userRegFile = new File(containerDir, ".wine/user.reg");

        try (WineRegistryEditor registryEditor = new WineRegistryEditor(userRegFile)) {
            Spinner sSystemFont = view.findViewById(R.id.SSystemFont);
            MSLogFont msLogFont = (new MSLogFont()).fromByteArray(registryEditor.getHexValues("Control Panel\\Desktop\\WindowMetrics", "CaptionFont"));
            AppUtils.setSpinnerSelectionFromValue(sSystemFont, msLogFont.getFaceName());

            SeekBar sbLogPixels = view.findViewById(R.id.SBLogPixels);
            sbLogPixels.setValue(registryEditor.getDwordValue("Control Panel\\Desktop", "LogPixels", 96));

            List<String> mouseWarpOverrideList = Arrays.asList(context.getString(R.string.disable), context.getString(R.string.enable), context.getString(R.string.force));
            Spinner sMouseWarpOverride = view.findViewById(R.id.SMouseWarpOverride);
            sMouseWarpOverride.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, mouseWarpOverrideList));
            AppUtils.setSpinnerSelectionFromValue(sMouseWarpOverride, registryEditor.getStringValue("Software\\Wine\\DirectInput", "MouseWarpOverride", "disable"));
        }
    }

    public static String getScreenSize(View view) {
        Spinner sScreenSize = view.findViewById(R.id.SScreenSize);
        String value = sScreenSize.getSelectedItem().toString();
        if (sScreenSize.getSelectedItemPosition() == 0) {
            value = Container.DEFAULT_SCREEN_SIZE;
            String strWidth = ((EditText)view.findViewById(R.id.ETScreenWidth)).getText().toString().trim();
            String strHeight = ((EditText)view.findViewById(R.id.ETScreenHeight)).getText().toString().trim();
            if (strWidth.matches("[0-9]+") && strHeight.matches("[0-9]+")) {
                int width = Integer.parseInt(strWidth);
                int height = Integer.parseInt(strHeight);
                if ((width % 2) == 0 && (height % 2) == 0) return width+"x"+height;
            }
        }
        return StringUtils.parseIdentifier(value);
    }

    private String getDesktopTheme(View view) {
        Spinner sDesktopBackgroundType = view.findViewById(R.id.SDesktopBackgroundType);
        WineThemeManager.BackgroundType type = WineThemeManager.BackgroundType.values()[sDesktopBackgroundType.getSelectedItemPosition()];
        RadioGroup rgDesktopTheme = view.findViewById(R.id.RGDesktopTheme);
        ImagePickerView ipvDesktopBackgroundImage = view.findViewById(R.id.IPVDesktopBackgroundImage);
        ColorPickerView cpvDesktopBackground = view.findViewById(R.id.CPVDesktopBackgroundColor);
        WineThemeManager.Theme theme = rgDesktopTheme.getCheckedRadioButtonId() == R.id.RBLight ? WineThemeManager.Theme.LIGHT : WineThemeManager.Theme.DARK;

       String desktopTheme = theme+","+type+","+cpvDesktopBackground.getColorAsString();
        if (type == WineThemeManager.BackgroundType.IMAGE) {
            String selectedSource = ipvDesktopBackgroundImage.getSelectedSource();
            String wallpaperId = !selectedSource.equals(WineThemeManager.DEFAULT_WALLPAPER_ID) && selectedSource.startsWith("wallpaper-") ? selectedSource : "0";
            File userWallpaperFile = WineThemeManager.getUserWallpaperFile(getContext());
            desktopTheme += ","+(userWallpaperFile.isFile() && selectedSource.equals("user-wallpaper") ? userWallpaperFile.lastModified() : wallpaperId);
        }
        return desktopTheme;
    }

    public static void loadScreenSizeSpinner(View view, String selectedValue) {
        final Spinner sScreenSize = view.findViewById(R.id.SScreenSize);

        final LinearLayout llCustomScreenSize = view.findViewById(R.id.LLCustomScreenSize);
        sScreenSize.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                llCustomScreenSize.setVisibility(sScreenSize.getSelectedItemPosition() == 0 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        boolean found = AppUtils.setSpinnerSelectionFromIdentifier(sScreenSize, selectedValue);
        if (!found) {
            sScreenSize.setSelection(0);
            String[] screenSize = selectedValue.split("x");
            ((EditText)view.findViewById(R.id.ETScreenWidth)).setText(screenSize[0]);
            ((EditText)view.findViewById(R.id.ETScreenHeight)).setText(screenSize[1]);
        }
    }

    public static String getWinComponents(View view) {
        ViewGroup parent = view.findViewById(R.id.LLTabWinComponents);
        ArrayList<View> views = new ArrayList<>();
        AppUtils.findViewsWithClass(parent, Spinner.class, views);
        String[] wincomponents = new String[views.size()];

        for (int i = 0; i < views.size(); i++) {
            Spinner spinner = (Spinner)views.get(i);
            wincomponents[i] = spinner.getTag()+"="+spinner.getSelectedItemPosition();
        }
        return String.join(",", wincomponents);
    }

    public static void createWinComponentsTab(View view, String wincomponents) {
        Context context = view.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);
        ViewGroup tabView = view.findViewById(R.id.LLTabWinComponents);
        ViewGroup directxSectionView = tabView.findViewById(R.id.LLWinComponentsDirectX);
        ViewGroup generalSectionView = tabView.findViewById(R.id.LLWinComponentsGeneral);

        for (String[] wincomponent : new KeyValueSet(wincomponents)) {
            final String name = wincomponent[0];
            ViewGroup parent = name.startsWith("direct") || name.startsWith("x") ? directxSectionView : generalSectionView;
            View itemView = inflater.inflate(R.layout.wincomponent_list_item, parent, false);
            ((TextView)itemView.findViewById(R.id.TextView)).setText(StringUtils.getString(context, name));
            Spinner spinner = itemView.findViewById(R.id.Spinner);
            spinner.setSelection(Integer.parseInt(wincomponent[1]), false);
            spinner.setTag(name);
            parent.addView(itemView);
        }
    }

    private EnvVarsView createEnvVarsTab(final View view) {
        final Context context = view.getContext();
        final EnvVarsView envVarsView = view.findViewById(R.id.EnvVarsView);
        envVarsView.setEnvVars(new EnvVars(isEditMode() ? container.getEnvVars() : Container.DEFAULT_ENV_VARS));
        view.findViewById(R.id.BTAddEnvVar).setOnClickListener((v) -> (new AddEnvVarDialog(context, envVarsView)).show());
        return envVarsView;
    }

    private String getDrives(View view) {
        LinearLayout parent = view.findViewById(R.id.LLDrives);
        String drives = "";

        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            Spinner spinner = child.findViewById(R.id.Spinner);
            EditText editText = child.findViewById(R.id.EditText);
            String path = editText.getText().toString().replace(":", "").trim();
            if (!path.isEmpty()) drives += spinner.getSelectedItem()+path;
        }
        return drives;
    }

    private void createDrivesTab(View view) {
        final Context context = getContext();

        final LinearLayout parent = view.findViewById(R.id.LLDrives);
        final View emptyTextView = view.findViewById(R.id.TVDrivesEmptyText);
        LayoutInflater inflater = LayoutInflater.from(context);
        final String drives = isEditMode() ? container.getDrives() : Container.DEFAULT_DRIVES;
        final String[] driveLetters = new String[Container.MAX_DRIVE_LETTERS];
        for (int i = 0; i < driveLetters.length; i++) driveLetters[i] = ((char)(i + 68))+":";

        Callback<Drive> addItem = (drive) -> {
            final View itemView = inflater.inflate(R.layout.drive_list_item, parent, false);
            Spinner spinner = itemView.findViewById(R.id.Spinner);
            spinner.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, driveLetters));
            AppUtils.setSpinnerSelectionFromValue(spinner, drive.letter+":");

            final EditText editText = itemView.findViewById(R.id.EditText);
            editText.setText(drive.path);

            itemView.findViewById(R.id.BTSearch).setOnClickListener((v) -> showDriveSearchPopupMenu(v, drive, editText));
            itemView.findViewById(R.id.BTRemove).setOnClickListener((v) -> {
                parent.removeView(itemView);
                if (parent.getChildCount() == 0) emptyTextView.setVisibility(View.VISIBLE);
            });
            parent.addView(itemView);
        };
        for (Drive drive : Container.drivesIterator(drives)) addItem.call(drive);

        view.findViewById(R.id.BTAddDrive).setOnClickListener((v) -> {
            if (parent.getChildCount() >= Container.MAX_DRIVE_LETTERS) return;
            final String nextDriveLetter = String.valueOf(driveLetters[parent.getChildCount()].charAt(0));
            addItem.call(new Drive(nextDriveLetter, ""));
        });

        if (drives.isEmpty()) emptyTextView.setVisibility(View.VISIBLE);
    }

    private void showDriveSearchPopupMenu(View anchorView, final Drive drive, final EditText editText) {
        final FragmentActivity activity = getActivity();
        final Fragment $this = ContainerDetailFragment.this;

        PopupMenu popupMenu = new PopupMenu(activity, anchorView);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) popupMenu.setForceShowIcon(true);
        popupMenu.inflate(R.menu.drive_search_popup_menu);
        Menu menu = popupMenu.getMenu();
        SubMenu subMenu = menu.findItem(R.id.menu_item_locations).getSubMenu();
        ArrayList<Container> containers = manager.getContainers();
        for (int i = 0; i < containers.size(); i++) {
            Container container = containers.get(i);
            subMenu.add(0, 0, container.id, container.getName()+" (Drive C:)");
        }

        popupMenu.setOnMenuItemClickListener((menuItem) -> {
            int itemId = menuItem.getItemId();
            switch (itemId) {
                case R.id.menu_item_open_directory:
                    openDirectoryCallback = (path) -> {
                        drive.path = path;
                        editText.setText(path);
                    };

                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.fromFile(Environment.getExternalStorageDirectory()));
                    activity.startActivityFromFragment($this, intent, MainActivity.OPEN_DIRECTORY_REQUEST_CODE);
                    break;
                case R.id.menu_item_downloads:
                    drive.path = AppUtils.DIRECTORY_DOWNLOADS;
                    editText.setText(AppUtils.DIRECTORY_DOWNLOADS);
                    break;
                case R.id.menu_item_internal_storage:
                    drive.path = AppUtils.INTERNAL_STORAGE;
                    editText.setText(AppUtils.INTERNAL_STORAGE);
                    break;
                default:
                    Container container = manager.getContainerById(menuItem.getOrder());
                    if (container != null) {
                        String path = container.getRootDir()+"/.wine/drive_c";
                        drive.path = path;
                        editText.setText(path);
                    }
                    break;
            }
            return true;
        });

        popupMenu.show();
    }

    private void loadWineVersionSpinner(final View view, Spinner sWineVersion, final ArrayList<WineInfo> wineInfos) {
        final Context context = getContext();
        sWineVersion.setEnabled(!isEditMode());
        view.findViewById(R.id.LLWineVersion).setVisibility(View.VISIBLE);
        sWineVersion.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, wineInfos));
        if (isEditMode()) AppUtils.setSpinnerSelectionFromValue(sWineVersion, WineInfo.fromIdentifier(context, container.getWineVersion()).toString());
    }
}
