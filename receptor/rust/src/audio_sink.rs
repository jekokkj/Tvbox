//! Saida de audio para Android 7+ via `android.media.AudioTrack` (chamado por JNI).
//!
//! ## Por que isto existe
//!
//! O Spotlet original toca o audio pelo AAudio (cpal 0.16), que so existe a partir do
//! Android 8.0 (API 26). No Android 7 nao ha `libaaudio.so`: o app nem compila para API 24.
//! O `AudioTrack` existe desde o Android 1.0, entao esta saida:
//!   - recebe o PCM do librespot (f64 -> s16);
//!   - escreve num `AudioTrack` em modo STREAM (a escrita bloqueia quando o buffer enche,
//!     o que da a contrapressao que o player precisa);
//!   - o proprio Android troca de rota (alto-falante, HDMI, Bluetooth) sem derrubar a faixa.
//!     Se a faixa morrer (`ERROR_DEAD_OBJECT`), ela e recriada na proxima escrita.

use jni::objects::{GlobalRef, JValue};
use jni::{JNIEnv, JavaVM};
use librespot::playback::audio_backend::{Sink, SinkError, SinkResult};
use librespot::playback::convert::Converter;
use librespot::playback::decoder::AudioPacket;
use log::{error, info, warn};

/// librespot entrega estereo a 44,1 kHz.
const SAMPLE_RATE: i32 = 44_100;
const CHANNEL_OUT_STEREO: i32 = 12;
const ENCODING_PCM_16BIT: i32 = 2;
const STREAM_MUSIC: i32 = 3;
const MODE_STREAM: i32 = 1;

pub struct AudioTrackSink {
    vm: JavaVM,
    track: Option<GlobalRef>,
}

impl AudioTrackSink {
    pub fn new() -> Self {
        // O contexto Android (e com ele a JavaVM) foi registrado em initAndroidContext.
        let vm = unsafe { JavaVM::from_raw(ndk_context::android_context().vm().cast()) }
            .expect("JavaVM indisponivel: initAndroidContext nao foi chamado");
        let mut sink = Self { vm, track: None };
        sink.abrir();
        sink
    }

    /// Cria o AudioTrack (descarta o anterior, se houver).
    fn abrir(&mut self) {
        self.fechar();
        match self.criar_track() {
            Ok(t) => {
                info!("AudioTrack aberto ({} Hz, estereo, PCM 16 bits)", SAMPLE_RATE);
                self.track = Some(t);
            }
            Err(e) => error!("Falha ao abrir o AudioTrack: {e}"),
        }
    }

    fn criar_track(&self) -> Result<GlobalRef, String> {
        let mut env = self
            .vm
            .attach_current_thread_permanently()
            .map_err(|e| e.to_string())?;
        env.with_local_frame(16, |env| -> Result<GlobalRef, jni::errors::Error> {
            let cls = env.find_class("android/media/AudioTrack")?;
            let min = env
                .call_static_method(
                    &cls,
                    "getMinBufferSize",
                    "(III)I",
                    &[
                        JValue::Int(SAMPLE_RATE),
                        JValue::Int(CHANNEL_OUT_STEREO),
                        JValue::Int(ENCODING_PCM_16BIT),
                    ],
                )?
                .i()?;
            // Buffer folgado (4x o minimo): TV boxes fracas engasgam com buffers justos.
            let buffer = min.max(4096) * 4;
            let obj = env.new_object(
                &cls,
                "(IIIIII)V",
                &[
                    JValue::Int(STREAM_MUSIC),
                    JValue::Int(SAMPLE_RATE),
                    JValue::Int(CHANNEL_OUT_STEREO),
                    JValue::Int(ENCODING_PCM_16BIT),
                    JValue::Int(buffer),
                    JValue::Int(MODE_STREAM),
                ],
            )?;
            env.new_global_ref(obj)
        })
        .map_err(|e| e.to_string())
    }

    fn fechar(&mut self) {
        if let Some(t) = self.track.take() {
            if let Ok(mut env) = self.vm.attach_current_thread_permanently() {
                let _ = env.call_method(&t, "pause", "()V", &[]);
                let _ = env.call_method(&t, "flush", "()V", &[]);
                let _ = env.call_method(&t, "release", "()V", &[]);
                clear_exception(&mut env);
            }
        }
    }

    fn chamar_sem_retorno(&self, metodo: &str) {
        if let Some(t) = &self.track {
            if let Ok(mut env) = self.vm.attach_current_thread_permanently() {
                if let Err(e) = env.call_method(t, metodo, "()V", &[]) {
                    warn!("AudioTrack.{metodo} falhou: {e}");
                }
                clear_exception(&mut env);
            }
        }
    }

    /// Escreve PCM no AudioTrack (bloqueia ate caber). Devolve o codigo do Android (negativo = erro).
    fn escrever(&self, amostras: &[i16]) -> Result<i32, String> {
        let track = self.track.as_ref().ok_or_else(|| "sem AudioTrack".to_string())?;
        let mut env = self
            .vm
            .attach_current_thread_permanently()
            .map_err(|e| e.to_string())?;
        let r = env.with_local_frame(8, |env| -> Result<i32, jni::errors::Error> {
            let arr = env.new_short_array(amostras.len() as i32)?;
            env.set_short_array_region(&arr, 0, amostras)?;
            env.call_method(
                track,
                "write",
                "([SII)I",
                &[
                    JValue::Object(&arr),
                    JValue::Int(0),
                    JValue::Int(amostras.len() as i32),
                ],
            )?
            .i()
        });
        clear_exception(&mut env);
        r.map_err(|e| e.to_string())
    }
}

/// Qualquer excecao Java pendente precisa ser limpa antes da proxima chamada JNI.
fn clear_exception(env: &mut JNIEnv) {
    if env.exception_check().unwrap_or(false) {
        let _ = env.exception_clear();
    }
}

impl Drop for AudioTrackSink {
    fn drop(&mut self) {
        self.fechar();
    }
}

impl Sink for AudioTrackSink {
    fn start(&mut self) -> SinkResult<()> {
        if self.track.is_none() {
            self.abrir();
        }
        self.chamar_sem_retorno("play");
        Ok(())
    }

    fn stop(&mut self) -> SinkResult<()> {
        // pause() corta na hora e mantem o que ja foi escrito (retoma em start()).
        self.chamar_sem_retorno("pause");
        Ok(())
    }

    fn write(&mut self, packet: AudioPacket, converter: &mut Converter) -> SinkResult<()> {
        let samples = packet
            .samples()
            .map_err(|e| SinkError::OnWrite(e.to_string()))?;
        let pcm: Vec<i16> = converter.f64_to_s16(samples);

        if self.track.is_none() {
            self.abrir();
            self.chamar_sem_retorno("play");
        }
        match self.escrever(&pcm) {
            Ok(n) if n >= 0 => {}
            Ok(codigo) => {
                // ERROR_DEAD_OBJECT (-6) e afins: recria a faixa e tenta uma vez.
                warn!("AudioTrack.write devolveu {codigo}; recriando a saida de audio");
                self.abrir();
                self.chamar_sem_retorno("play");
                let _ = self.escrever(&pcm);
            }
            Err(e) => {
                // Sem saida no momento: descarta o pacote para o player seguir vivo.
                warn!("Audio indisponivel ({e}); pacote descartado");
                self.track = None;
            }
        }
        Ok(())
    }
}
