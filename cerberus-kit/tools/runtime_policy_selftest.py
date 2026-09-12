#!/usr/bin/env python3
from pathlib import Path
import shutil, subprocess, tempfile
kit=Path(__file__).resolve().parent.parent
src=kit/'source'/'Cerberus'/'app'/'src'/'main'/'java'/'com'/'winlator'/'cerberus'/'runtime'
javac=shutil.which('javac'); java=shutil.which('java')
if not javac or not java: raise SystemExit('[FAIL] JDK (java+javac) not found')
classes=['RuntimeEngine.java','RuntimeFamily.java','RuntimeArchitecture.java','RuntimeBackend.java','CerberusRuntimeProfile.java','CerberusRuntimeResolver.java','CerberusCompatibilityMatrix.java']
main='''
import com.winlator.cerberus.runtime.*;
public class CerberusV820PolicySelfTest {
  static void req(boolean v,String m){if(!v)throw new RuntimeException(m);}
  public static void main(String[] a){
    CerberusRuntimeProfile c=CerberusRuntimeResolver.resolve(RuntimeEngine.CLASSIC,RuntimeFamily.WINE,RuntimeArchitecture.ARM64EC,RuntimeBackend.WOWBOX64);
    req(c.architecture==RuntimeArchitecture.X86_64,"classic arch"); req(c.backend64==RuntimeBackend.BOX64&&c.backend32==RuntimeBackend.BOX64,"classic backend");
    CerberusRuntimeProfile bx=CerberusRuntimeResolver.resolve(RuntimeEngine.BIONIC,RuntimeFamily.PROTON,RuntimeArchitecture.X86_64,RuntimeBackend.FEXCORE);
    req(bx.backend64==RuntimeBackend.BOX64&&bx.backend32==RuntimeBackend.BOX64,"bionic x64 backend");
    CerberusRuntimeProfile af=CerberusRuntimeResolver.resolve(RuntimeEngine.BIONIC,RuntimeFamily.PROTON,RuntimeArchitecture.ARM64EC,RuntimeBackend.FEXCORE);
    req(af.backend64==RuntimeBackend.FEXCORE&&af.backend32==RuntimeBackend.FEXCORE,"arm fex");
    CerberusRuntimeProfile aw=CerberusRuntimeResolver.resolve(RuntimeEngine.BIONIC,RuntimeFamily.WINE,RuntimeArchitecture.ARM64EC,RuntimeBackend.WOWBOX64);
    req(aw.backend64==RuntimeBackend.FEXCORE&&aw.backend32==RuntimeBackend.WOWBOX64,"arm wow");
    req(CerberusCompatibilityMatrix.validate(c)==null&&CerberusCompatibilityMatrix.validate(bx)==null&&CerberusCompatibilityMatrix.validate(af)==null&&CerberusCompatibilityMatrix.validate(aw)==null,"valid profiles");
    af.backend64=RuntimeBackend.BOX64; req(CerberusCompatibilityMatrix.validate(af)!=null,"reject arm box64");
    System.out.println("CERBERUS_V820_RUNTIME_POLICY_SELFTEST=PASS");
  }
}
'''
with tempfile.TemporaryDirectory(prefix='cerberus-v820-policy-') as td:
    td=Path(td); (td/'CerberusV820PolicySelfTest.java').write_text(main)
    out=td/'classes'; out.mkdir()
    p=subprocess.run([javac,'-encoding','UTF-8','-d',str(out)]+[str(src/x) for x in classes]+[str(td/'CerberusV820PolicySelfTest.java')],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
    if p.returncode: print(p.stdout); raise SystemExit(p.returncode)
    p=subprocess.run([java,'-cp',str(out),'CerberusV820PolicySelfTest'],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
    print(p.stdout.strip())
    raise SystemExit(p.returncode)
