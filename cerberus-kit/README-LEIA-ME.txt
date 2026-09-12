CERBERUS WINLATOR V8.2.0 — BIONIC FULL INTEGRATION
==================================================

BASE DO APLICATIVO
------------------
A base compilada é source/Cerberus (Cerberus/Winlator). O donor Bionic NÃO é a
base, NÃO fornece MainActivity/UI compilada e NÃO é renomeado para Cerberus.

O donor é usado somente para importar payloads técnicos do engine Bionic
(imagefs/container pattern) e para consulta de código em work/donor-code-reference.

ARQUITETURA
-----------
Classic GLIBC:
  x86_64 -> Box64

Cerberus Bionic:
  x86_64  -> Box64
  ARM64EC -> FEXCore (64-bit)
  32-bit em ARM64EC -> FEXCore ou WOWBox64

Wine/Proton NÃO escolhe o tradutor. A arquitetura escolhe.

O QUE ESTA INTEGRADO
--------------------
- UI Cerberus moderna com Explorar -> Adreno Tools.
- Turnip/Adrenotools/Box64/DXVK/VKD3D/WineD3D Classic preservados.
- Wine/Proton WCP Bionic x86_64/ARM64EC via catálogo.
- FEXCore e WOWBox64 WCP via catálogo.
- FEX Unixlib: DLL + .so sincronizados; pacote DLL-only remove .so stale.
- DXVK/VKD3D Bionic WCP separados por arquitetura.
- Runtime Resolver por arquitetura.
- Containers antigos sem metadata continuam Classic/x86_64/Box64.
- Engine/arquitetura alterada cria prefix novo em staging e preserva o anterior
  como .wine-rollback-*.
- Preflight bloqueia launch inconsistente.
- HODLL automático:
    FEXCore 32-bit -> libwow64fex.dll
    WOWBox64       -> wowbox64.dll
- Variável correta: FEX_SMCCHECKS.
- RootFS Bionic isolado em files/cerberus-bionic/rootfs; Classic não é sobrescrito.

PREPARAR
--------
1. Extraia este ZIP em pasta nova.
2. Execute o fluxo Python principal:
     python PREPARE-BIONIC.py
   No Windows, PREPARE-BIONIC.bat é apenas um launcher opcional para Python.
   Ele baixa o donor pinado sem smudge geral do Git LFS e puxa SOMENTE os objetos
   Bionic selecionados. O donor permanece em reference/BionicDonor.
3. O PREPARE executa VERIFY-BIONIC e aborta se código do donor entrar na base.

BUILD
-----
Execute o fluxo Python principal:
  python BUILD-BIONIC.py

No Windows, BUILD-BIONIC.bat é apenas um launcher opcional para Python.

O build:
- configura JDK/SDK;
- exige Android 35, Build Tools 35.0.0, NDK 24.0.8215888, CMake 3.22.1;
- valida o source/payload;
- compila source/Cerberus;
- usa heap Gradle 4 GB, 2 workers e noCompress global;
- encontra o APK recursivamente, sem depender de um caminho AGP fixo.

APK final esperado:
  output/Cerberus-Winlator-V8.2.0-BIONIC-FULL-INTEGRATION.apk

COMPONENTES DENTRO DO APP
-------------------------
Explorar -> Adreno Tools:
- BIONIC CPU: FEXCore / WOWBox64
- BIONIC GRAPHICS: DXVK / VKD3D
- WINE / PROTON: runtimes Bionic

Depois, em Container -> Editar, selecione:
- Engine Classic ou Bionic
- Wine/Proton
- x86_64 ou ARM64EC
- runtime instalado
- backend 32-bit ARM64EC
- FEX/WOWBox64 instalado
- DXVK/VKD3D Bionic opcional

DONOR PIN
---------
Repo: https://github.com/Succubussix/winlator-bionic-glibc.git
Branch: winlator_bionic
Commit esperado: prefixo 1a07048

O pin existe apenas para reprodução do payload Bionic; o código do app donor não
é compilado.
