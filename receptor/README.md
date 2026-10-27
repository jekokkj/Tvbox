# Receptor Spotify (Android 7+)

Receptor **Spotify Connect** para TV box com Android 7.0 ou mais novo. A caixa aparece como alto-falante no app do
Spotify do celular (mesma rede Wi-Fi). Precisa de conta **Spotify Premium**.

Baseado no [Spotlet](https://github.com/qvr/spotlet) (MIT), que por sua vez vem do Rusty e do librespot.
Mudancas deste fork:
- `minSdk` 24 (Android 7.0) e compilacao nativa com `--platform 24`.
- Saida de audio por `android.media.AudioTrack` (JNI), em `rust/src/audio_sink.rs`, no lugar do AAudio (so Android 8+).
- `applicationId` `br.tvbox.spotify`, icone em PNG (Android 7 nao tem icone adaptativo).
- Assinatura com chave fixa (os APKs atualizam por cima).

Licenca: MIT (veja `LICENSE`).