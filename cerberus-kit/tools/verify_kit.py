#!/usr/bin/env python3
from __future__ import annotations
import argparse, json, os, py_compile, re, shutil, subprocess, sys, tempfile, xml.etree.ElementTree as ET
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]; SRC=ROOT/'source'/'Cerberus'; JAVA=SRC/'app'/'src'/'main'/'java'; RES=SRC/'app'/'src'/'main'/'res'; ASSETS=SRC/'app'/'src'/'main'/'assets'/'cerberus-bionic'
errors=[]; warnings=[]
def text(p):
    try:return p.read_text(encoding='utf-8',errors='replace')
    except:return ''
def need(cond,msg):
    if not cond:errors.append(msg)
def warn(cond,msg):
    if not cond:warnings.append(msg)
def run_py(path,*args):
    p=subprocess.run([sys.executable,str(path),*args],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True);print(p.stdout.rstrip());need(p.returncode==0,f'{path.name} failed ({p.returncode})')
def magic(p):
    try:
        h=p.read_bytes()[:6]
        if h.startswith(b'\xfd7zXZ\x00'):return 'xz'
        if h.startswith(b'\x28\xb5\x2f\xfd'):return 'zstd'
        if h.startswith((b'PK\x03\x04',b'PK\x05\x06',b'PK\x07\x08')):return 'zip'
    except:pass
    return None

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--require-payload',action='store_true');a=ap.parse_args()
    g=text(SRC/'app'/'build.gradle');need(re.search(r'\bversionCode\s+820\b',g),'versionCode 820 ausente');need('8.2.0-Cerberus-Bionic' in g,'versionName incorreto');need(re.search(r'applicationId\s+[\'\"]com\.winlator[\'\"]',g),'applicationId alterado');need("noCompress.add('')" in g or 'noCompress.add("")' in g,'noCompress global ausente')
    props=text(SRC/'gradle.properties');need('-Xmx4096m' in props,'Gradle heap 4G ausente');need(re.search(r'(?m)^org\.gradle\.workers\.max\s*=\s*2\s*$',props),'Gradle workers.max=2 ausente')
    req=['CerberusRuntimeProfile.java','CerberusRuntimeResolver.java','CerberusCompatibilityMatrix.java','CerberusRuntimeStore.java','CerberusCpuComponentStore.java','CerberusGuestComponentStore.java','CerberusBionicInstaller.java','CerberusBionicContainerPattern.java','CerberusBionicCpuComponentInstaller.java','CerberusBionicGuestComponentInstaller.java','CerberusPrefixManager.java','CerberusRuntimePreflight.java','CerberusWcpUtils.java','CerberusProfileUtils.java']
    rr=JAVA/'com'/'winlator'/'cerberus'/'runtime';
    for n in req:need((rr/n).is_file(),'runtime source ausente: '+n)
    launcher=text(JAVA/'com/winlator/xenvironment/components/CerberusBionicProgramLauncherComponent.java');wcp=text(rr/'CerberusWcpUtils.java');tar=text(JAVA/'com/winlator/core/TarCompressorUtils.java');proc=text(JAVA/'com/winlator/core/ProcessHelper.java');pre=text(rr/'CerberusRuntimePreflight.java');prefix=text(rr/'CerberusPrefixManager.java');cpu=text(rr/'CerberusBionicCpuComponentInstaller.java');guest=text(rr/'CerberusBionicGuestComponentInstaller.java');installer=text(rr/'CerberusBionicInstaller.java')
    need('normalizeEntry(entry.getName())' in wcp and 'ZipInputStream' in wcp,'WCP ZIP/./profile.json compatibility fix ausente');need('safeDestination' in wcp,'ZIP traversal guard ausente');need('safeArchiveDestination' in tar and 'normalizeArchivePath' in tar,'TAR traversal/normalization guard ausente')
    need('if (envVars != null)' in proc,'ProcessHelper null env guard ausente')
    for token in ['XDG_CONFIG_DIRS','FONTCONFIG_PATH','ALSA_CONFIG_PATH','OPENSSL_CONF','VK_LAYER_PATH','ENABLE_UTIL_LAYER','LD_LIBRARY_PATH','FEX_SMCCHECKS']:
        need(token in launcher,'Bionic launcher env ausente: '+token)
    need('env.put("FEX_SMC_CHECKS"' not in launcher,'variável FEX_SMC_CHECKS obsoleta emitida')
    need('explicitFex' in cpu and 'explicitWow' in cpu,'CPU component exact-selection guard ausente');need('usr/lib/wine/aarch64-unix' in cpu,'FEX unixlib nested path handling ausente');need('markerMatchesAndPayloadPresent' in guest and 'targets' in guest,'DXVK/VKD3D payload integrity marker ausente')
    need('Runtime WCP selecionado não está instalado' in pre,'runtime selected-missing guard ausente');need('isBuiltinRuntimeId' in pre,'builtin runtime distinction ausente')
    need('oldProfile.family!=newProfile.family' in prefix.replace(' ',''),'prefix family switch guard ausente');need('runtimeId' in prefix and '.cerberus-dxvk.json' in prefix and '.cerberus-vkd3d.json' in prefix,'prefix/runtime marker reset ausente')
    need('public static final int VERSION = 2' in installer and 'hasRequiredLayout' in installer,'Bionic imagefs structural validation/version bump ausente')
    rootfs=text(JAVA/'com/winlator/xenvironment/RootFS.java');need('catch (Exception ignored) { return 0; }' in rootfs,'RootFS malformed-version guard ausente')

    # Wiring/ownership isolation.
    mainj=text(JAVA/'com/winlator/MainActivity.java');container=text(JAVA/'com/winlator/ContainerDetailFragment.java');manager=text(JAVA/'com/winlator/container/ContainerManager.java');x=text(JAVA/'com/winlator/XServerDisplayActivity.java')
    need('cerberusAdrenoFexCore' in mainj and 'cerberusAdrenoBionicDXVK' in mainj and 'cerberusAdrenoWineProton' in mainj,'Bionic component-center wiring ausente')
    need('CerberusPrefixManager.prepareRuntimeChange' in container,'prefix transaction não ligada ao editor');need('CerberusRuntimePreflight.check' in x and 'CerberusBionicProgramLauncherComponent' in x,'preflight/launcher Bionic não ligados');need('RootFS.findBionic' in x,'Bionic root isolado não usado');need('CerberusBionicContainerPattern.extract' in manager,'Bionic pattern não ligado ao ContainerManager');need('setExtraData(srcContainer.getExtraDataCopy())' in manager,'duplicação não preserva metadata Cerberus')
    for p in (SRC/'app'/'src'/'main').rglob('*'):
        if p.is_file() and p.suffix.lower() in {'.java','.kt','.xml','.gradle'}:
            t=text(p).lower()
            if re.search(r'package\s+[^\n]*(bannerlator|winlator\.cmod)',t) or 'com.winlator.cmod' in t:errors.append('donor app code vazou para compiled source: '+str(p.relative_to(SRC)))

    # Resources/XML.
    need('<string name="app_name">Cerberus Winlator</string>' in text(RES/'values/strings.xml'),'branding Cerberus Winlator ausente')
    for p in RES.rglob('*.xml'):
        try:ET.parse(p)
        except Exception as e:errors.append(f'XML inválido {p.relative_to(SRC)}: {e}')

    # Python-only automation policy.
    ps=list(ROOT.glob('*.ps1'));need(not ps,'PowerShell residual: '+', '.join(x.name for x in ps))
    required_py=['PREPARE-BIONIC.py','IMPORT-BIONIC-DONOR.py','VERIFY-BIONIC.py','VERIFY-RUNTIME-JAVAC.py','CONFIGURE-CERBERUS-SDK.py','SYNC-NDK-RUNTIME.py','BUILD-BIONIC.py','COLLECT-APK.py']
    for n in required_py:need((ROOT/n).is_file(),'script Python ausente: '+n)
    for b in ['PREPARE-BIONIC.bat','BUILD-BIONIC.bat']:
        bt=text(ROOT/b).lower();need('powershell' not in bt and '.py' in bt,f'{b} ainda depende de PowerShell')
    pyfiles=[*ROOT.glob('*.py'),*(ROOT/'tools').glob('*.py')]
    with tempfile.TemporaryDirectory(prefix='cerberus-pycompile-') as td:
        for i,p in enumerate(pyfiles):
            try:py_compile.compile(str(p),cfile=str(Path(td)/(str(i)+'.pyc')),doraise=True)
            except Exception as e:errors.append(f'Python syntax {p.name}: {e}')
    # Donor reference files are not distributed application source.
    junk=[p for p in ROOT.rglob('*') if p.relative_to(ROOT).parts[0] not in {'reference','work','.git'} and p.is_file() and (p.suffix=='.pyc' or '__pycache__' in p.parts)];need(not junk,'bytecode/cache residual no pacote: '+', '.join(str(x.relative_to(ROOT)) for x in junk[:10]))

    # JNI integrity / dependency closure. libomp is intentionally sourced from the exact configured NDK by SYNC-NDK-RUNTIME.py.
    jni=SRC/'app'/'src'/'main'/'jniLibs'/'arm64-v8a';libs={p.name for p in jni.glob('*.so')};system={'libc.so','libm.so','libdl.so','liblog.so','libandroid.so','libOpenSLES.so','libz.so','libGLESv1_CM.so','libGLESv2.so','libEGL.so','libvulkan.so','libjnigraphics.so','libnativewindow.so','libaaudio.so'}
    for so in jni.glob('*.so'):
        try:need(so.stat().st_size>0 and so.read_bytes()[:4]==b'\x7fELF',f'JNI inválida: {so.name}')
        except Exception:errors.append('JNI ilegível: '+so.name)
    readelf=shutil.which('readelf');missing={}
    if readelf:
        for so in jni.glob('*.so'):
            out=subprocess.run([readelf,'-d',str(so)],stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True).stdout
            deps=re.findall(r'\(NEEDED\).*?\[(.*?)\]',out);miss=[d for d in deps if d not in libs and d not in system]
            if miss:missing[so.name]=miss
    if missing:
        only_omp=set(sum(missing.values(),[]))=={'libomp.so'} and set(missing)=={'libfluidsynth.so'}
        need(only_omp and (ROOT/'SYNC-NDK-RUNTIME.py').is_file() and 'SYNC-NDK-RUNTIME.py' in text(ROOT/'BUILD-BIONIC.py'),'JNI dependencies ausentes: '+json.dumps(missing,ensure_ascii=False))
        if only_omp:warnings.append('libomp.so é sincronizado do NDK configurado no início do BUILD-BIONIC.py; não é embutido de um NDK arbitrário no source kit.')

    if a.require_payload:
        need(ASSETS.is_dir(),'payload Bionic não preparado; execute PREPARE-BIONIC.py')
        imgs=list(ASSETS.glob('imagefs.*')) if ASSETS.is_dir() else [];x86=list(ASSETS.glob('container-pattern-x86_64.*')) if ASSETS.is_dir() else [];arm=list(ASSETS.glob('container-pattern-arm64ec.*')) if ASSETS.is_dir() else []
        need(bool(imgs),'imagefs Bionic canonical ausente');need(bool(x86),'container pattern x86_64 ausente');need(bool(arm),'container pattern ARM64EC ausente');need((ASSETS/'manifest.json').is_file(),'manifest Bionic ausente')
        for p in imgs+x86+arm:need(p.stat().st_size>1024*64 and magic(p) in {'xz','zstd'},f'payload canonical inválido: {p.name}')

    # Run the executable policy/compile gates only after cheap failures are collected.
    if not errors:run_py(ROOT/'tools'/'runtime_compile_gate.py')
    if not errors:run_py(ROOT/'tools'/'runtime_policy_selftest.py')
    print('\nCERBERUS V8.2.0 DEEP STATIC VALIDATION');print('runtime_java_files=',len(list(rr.glob('*.java'))));print('resource_xml_files=',len(list(RES.rglob('*.xml'))));print('jni_libs=',len(list(jni.glob('*.so'))));print('errors=',len(errors));print('warnings=',len(warnings))
    for x in warnings:print('[WARN]',x)
    for x in errors:print('[FAIL]',x)
    if errors:return 1
    for x in ['WCP TAR/ZIP + ./profile.json compatibility','archive traversal guards','exact runtime/component selection','transactional runtime/prefix ownership','Bionic imagefs structural validation','Bionic launcher env repair','DXVK/VKD3D installed-payload verification','Python-only automation','XML/resource parse','JNI ELF/dependency audit','runtime Java compile gate','runtime architecture policy self-test']:print('[PASS]',x)
    return 0
if __name__=='__main__':raise SystemExit(main())
