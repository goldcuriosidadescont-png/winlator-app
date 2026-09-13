# Cerberus Bionic PC v0.6.0 — Container Session

Módulo independente criado na branch `cerberus-bionicpc-v06`. Ele não importa classes do Winlator; o repositório é usado somente como host de CI/Gradle.

## Implementado

- gerenciador visual de containers;
- perfis persistentes por UUID;
- diretórios independentes `prefix/`, `drive_c/`, `home/`, `tmp/`, `logs/`;
- configuração de resolução, runtime, renderer, modo DirectX e memória;
- instalador interno do runtime com download HTTPS e validação SHA-256;
- Box64 0.4.4 Bionic;
- Proton-Wine 11.0-2 x86_64;
- extração `.wcp`/`.wcp.xz` protegida contra path traversal;
- sessão real de runtime: `wine --version`, `wineboot -u`, `cmd.exe /c ver`;
- log por container e encerramento controlado do processo.

## Estado gráfico

Esta versão NÃO finge um desktop Windows. Wine/Box64 e WINEPREFIX são executados de verdade, mas o compositor X11/Surface Android + Vulkan/Turnip ainda não está embutido. O backend gráfico é a próxima etapa antes de jogos 3D.

## Runtime upstream

- Box64: The412Banner/Nightlies, `Box64-0.4.4-Bionic.wcp`
- Proton-Wine: GameNative/proton-wine, `proton-wine-11.0-2-x86_64.wcp.xz`

O APK baixa esses componentes na primeira instalação do runtime em vez de inflar o APK em centenas de megabytes. Os hashes esperados estão fixados em `RuntimeInstaller.java`.
