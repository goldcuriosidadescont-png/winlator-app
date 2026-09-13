# Cerberus PC — Genesis 0.1.0

Primeira base de um projeto original de execução de programas de PC no Android,
criada para Cerberus Tweaks. O nome Cerberus PC é a identidade inicial do projeto.

**Esta entrega é código-fonte, não um APK compilado. O núcleo x86 foi compilado
e testado no host Linux. O app Android, o JNI e o build ARM64 ainda precisam
passar pelo workflow e pela validação em aparelho.**

## O que existe agora

- Interpretador original de um subconjunto de IA-32, em C++17: registradores,
  flags, decodificação ModR/M e SIB, ALU, memória, pilha e saltos.
- Demo original **Cerberus Orbit**: 458 bytes de instruções x86; movimento,
  colisões e pontuação executam dentro do interpretador.
- ABI própria para framebuffer de 320 × 200, retângulos, entrada e apresentação.
- Projeto Android Java + JNI, direcionado a `arm64-v8a`, Android 8+.
- Interface com toque, A/D, setas e eixo horizontal/direcional de gamepad;
  execução limitada a uma fatia por tick, em uma thread dedicada.
- Pausa de agendamento em background; nenhum serviço residente ou permissão root.
- Importação `.c86` pelo seletor de documentos; inspeção limitada de cabeçalhos PE.
- Testes de núcleo, testes Java/JNI para executar com JDK e workflow de build APK.

![Frame real produzido pelo interpretador no host](docs/orbit-frame.png)

## Limite desta versão

**Ainda não executa jogos Windows `.exe`, Steam, jogos comerciais ou Linux ELF.**
O interpretador executa instruções x86 reais, mas o arquivo `.c86` usa nossa ABI
de demonstração. Ele não é um executável Windows disfarçado. O jogo Orbit serve
para provar a ligação entre CPU emulada, estado do jogo, entrada e framebuffer.

Não há loader PE funcional, Win32/NT, x86-64, x87/SSE/AVX, DirectX, Vulkan,
áudio, tradução dinâmica, GPU virtual ou emulação de um PC completo. O botão
`.exe` lê somente o cabeçalho; não carrega seções nem resolve importações.

## Bionic e originalidade

No build Android, `libcerberus.so` é uma biblioteca ARM64 do NDK, usando o ambiente
nativo Bionic do Android e libc++ estática. Não existe rootfs glibc ou chroot.
**Bionic é a libc, libm e o linker do Android; não fornece APIs Windows ou tradução
x86 por si só.** Fonte: [AOSP Bionic](https://android.googlesource.com/platform/bionic/).

Nenhum código de Winlator, Wine, Box64, Box86, QEMU, FEX, DXVK ou VKD3D foi
incorporado. Núcleo, ponte JNI, interface e demo foram escritos para esta entrega.
Android, NDK, JDK, Gradle, binutils e GitHub Actions são ferramentas/plataformas
de build, não um motor de emulação reaproveitado. Não foi realizada auditoria
externa de similaridade ou de propriedade intelectual.

## Gerar o APK pelo GitHub

1. Extraia o ZIP e coloque **o conteúdo** de `CerberusPC-v0.1.0/` na raiz de um
   repositório seu. Inclua a pasta `.github`, que contém o workflow.
2. Faça push. O workflow `Cerberus PC - tests and APK` executa testes de núcleo,
   integração Java/JNI, `assembleDebug` e `lintDebug`.
3. Com a execução concluída com sucesso, baixe o artifact
   `CerberusPC-v0.1.0-arm64-debug` na aba Actions.
4. Extraia `app-debug.apk` do artifact e instale no Android ARM64.

O workflow foi incluído, mas não foi disparado nesta entrega. Não há chave de
produção: o APK gerado é assinado com a chave debug do runner. Builds de runners
diferentes podem ter assinaturas diferentes; para atualizações consistentes,
configure posteriormente uma chave sua. Não há upload para Play Store.

## Build local

Toolchain fixada: JDK 17, Gradle 8.11.1, AGP 8.9.2, SDK 35, Build Tools 35.0.0,
NDK 28.0.13004108, CMake 3.22.1. O alvo é Android ARM64; o host de build é um
PC Linux/macOS/Windows com as ferramentas oficiais. Não é um script AX Manager.

Em Linux/macOS, com JDK, Gradle e `sdkmanager` no PATH e SDK configurado:

```sh
#!/bin/sh
set -eu
cd CerberusPC-v0.1.0
# Aceite as licenças interativamente caso ainda não tenham sido aceitas.
sdkmanager --licenses
sdkmanager 'platforms;android-35' 'build-tools;35.0.0' \
  'ndk;28.0.13004108' 'cmake;3.22.1'
sh tools/build_android.sh
```

Saída: `app/build/outputs/apk/debug/app-debug.apk`.
Opcional: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

Não há wrapper Gradle binário neste pacote. Com Gradle 8.11.1 instalado, pode
gerar o wrapper oficial: `gradle wrapper --gradle-version 8.11.1`. Depois abra
o diretório no Android Studio e sincronize. No Windows, use os equivalentes
`sdkmanager.bat` e `gradle.bat --no-daemon :app:assembleDebug :app:lintDebug`.

Compatibilidade de ferramentas: [AGP 8.9](https://developer.android.com/build/releases/agp-8-9-0-release-notes).
O linker está configurado para alinhamento ELF de 16 KiB, além do padrão do NDK
r28. Isso ainda precisa ser verificado no APK real com as ferramentas Android:
[páginas de 16 KiB](https://developer.android.com/guide/practices/page-sizes).

## Testar o núcleo no Linux x86-64

```sh
#!/bin/sh
set -eu
cd CerberusPC-v0.1.0
# Requer GCC, binutils e Python 3. ASan e UBSan ficam ativos por padrão.
sh tools/test_host.sh
# Integração Java/JNI adicional: requer JDK 17 completo.
sh tools/test_bridge.sh
```

O teste compara a demo recompilada com o asset empacotado e produz um frame
em `build/host/orbit.ppm`. Em ambiente que bloqueia a inspeção de processos do
LeakSanitizer, use `ASAN_OPTIONS=detect_leaks=0 sh tools/test_host.sh`: isso desliga
apenas a busca de vazamentos; ASan/UBSan continuam ativos. Esse foi o caso do host
usado para esta entrega. Consulte `docs/VALIDATION.md` para os resultados reais.

## Arquivos principais

| Caminho | Responsabilidade |
| --- | --- |
| `core/machine.*` | CPU, memória, execução e ABI de apresentação |
| `guest/orbit.s` | Código x86 do minijogo |
| `guest/flat.ld` | Layout guest em 0x10000 |
| `app/src/main/cpp/bridge.cpp` | JNI para a mesma classe de máquina testada no host |
| `app/src/main/java/dev/cerberus/pc/` | Interface, lifecycle, entrada e inspeção PE |
| `app/src/main/assets/orbit.c86` | Demo já montada; sem necessidade de binutils para build APK |
| `tests/` | Testes de CPU e integração Java/JNI |
| `docs/ABI.md` | Formato executável de demonstração e contrato do host |
| `docs/ROADMAP.md` | Caminho até compatibilidade Windows |

Para modificar Orbit, edite o assembly e execute `python3 tools/build_guest.py`
em Linux x86-64 com binutils. Depois rode os testes e recompile o aplicativo.

## Primeiro teste no seu Galaxy S23

Após gerar e instalar o APK: jogue a demo por alguns minutos, teste pausa e
retomada, vá para Home e volte, alterne retrato/paisagem e importe o asset `.c86`.
A rotação recria a sessão e reinicia a demo nesta versão. A retomada após matar o
processo também reinicia; não existem save states. Teste gamepad se disponível.
O contador mostra **frames guest acumulados**, nunca FPS de jogos comerciais.

Este projeto é independente do Cerberus Tweaks/AX Manager e não muda configurações
do Android, SELinux, frequência de CPU/GPU, drivers, GOS ou resolução do sistema.
