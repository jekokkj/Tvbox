# RetroLauncher for TVbox

Launcher pixelado para TV box Android (controle remoto). Versao 1.1.

## O que tem
- **Inicio**: YouTube (SmartTube), Spotify, Jogos (RetroArch), Arquivos, **Apps** e **Ajustes**.
- **Apps**: grade com todos os aplicativos instalados. OK abre; **OK segurado** abre o menu
  (abrir / informacoes e parar / desinstalar). "Informacoes e parar" abre a tela do Android do app,
  onde ficam Forcar parada e Limpar dados (um app comum nao pode parar outro sozinho).
- **Ajustes**: tema, cor dos icones, cor do foco, descanso de tela (e quanto tempo esperar), testar o descanso,
  **Debloat**, Wi-Fi e configuracoes do sistema.
- **Descanso de tela** (3): CAMPO (o casal correndo), ESTRELAS (viagem espacial) e RELOGIO (quicando).
  Qualquer tecla fecha.
- **Debloat**: lista os apps inuteis que existem na caixa. Os instalados pelo usuario saem na hora (o Android
  confirma); os de **sistema** saem pelo ADB com `ferramentas/debloat.bat` (Windows) ou `ferramentas/debloat.sh`
  (Termux/Linux). A remocao e so para o usuario 0 e reversivel com `restaurar.bat`. Revise a lista antes de rodar.

## Vita Link (pasta `vita/`, APK separado)
App que mostra a tela do PS Vita na TV box, usando o plugin **UDCD UVC** (o Vita aparece como uma webcam USB).
- Tela de espera pixelada com o logo do PS Vita ("AGUARDANDO CONEXAO"), ajuda de como conectar e menu.
- Ao ligar o Vita no USB, o Android oferece abrir o Vita Link; aceite a permissao USB na tela.
- Durante o video, OK ou MENU abre o menu: resolucao (◀▶), proporcao (ajustar/esticar), desconectar.
- O plugin nao envia audio: use um cabo P2 na saida de fone do Vita.
- O botao **JOGOS** do launcher abre o Vita Link quando ele esta instalado (senao abre o RetroArch).
- Compila separado do launcher (artifact `vitalink-apk`): se um falhar, o outro continua.

## Controles
- Segurar OK ou apertar MENU na tela inicial: troca de tema rapido.
- Voltar: fecha a tela atual (o launcher nunca fecha).

## Compilar
O GitHub Actions (`.github/workflows/build.yml`) compila sozinho a cada push; os APKs ficam em Artifacts (`tvlauncher-apk` e `vitalink-apk`).