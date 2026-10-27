"""Corrige a UVCAndroid 1.0.13 para o PS Vita (plugin vita-udcd-uvc).

Causas da tela preta encontradas no codigo:
  1. stream.c: no Android cada leitura bulk e limitada a 16 KB e tratada como um payload UVC com
     cabecalho proprio. O Vita manda o quadro inteiro num payload so (~0,2-1,4 MB, um cabecalho de
     12 bytes no comeco) -> os bytes da imagem eram lidos como cabecalhos e nenhum quadro fechava.
     Correcao: juntar as leituras ate uma leitura curta (fim do payload) e processar o payload inteiro.
  2. UVCPreview.cpp: todo quadro nao comprimido passava por uvc_yuyv2rgbx(), que recusa NV12.
     Correcao: NV12/NV21 -> RGBX pela libyuv (com reserva em C).
  3. Contadores nativos para a tela de DIAGNOSTICO (UVCCamera.nativeTvboxStats()).

Uso: python3 patch_uvc.py <pasta src/main da libuvccamera> <tvbox_uvc.h>
Para no primeiro trecho que nao bater (versao diferente da biblioteca).
"""
import os
import shutil
import sys

raiz = sys.argv[1]
cabecalho = sys.argv[2]
jni = os.path.join(raiz, "jni")


def ler(caminho):
    with open(caminho, encoding="utf-8") as f:
        return f.read()


def gravar(caminho, texto):
    with open(caminho, "w", encoding="utf-8") as f:
        f.write(texto)


def trocar(texto, alvo, novo, nome):
    n = texto.count(alvo)
    assert n == 1, "%s: esperado 1 trecho, achei %d" % (nome, n)
    return texto.replace(alvo, novo)


# ---------------------------------------------------------------- cabecalho compartilhado
shutil.copy(cabecalho, os.path.join(jni, "libuvc", "include", "libuvc", "tvbox_uvc.h"))

# ---------------------------------------------------------------- stream.c
caminho = os.path.join(jni, "libuvc", "src", "stream.c")
s = ler(caminho)

s = trocar(
    s,
    "void _uvc_swap_buffers(uvc_stream_handle_t *strmh) {\n    uint8_t *tmp_buf;\n",
    "#include \"libuvc/tvbox_uvc.h\"\n"
    "unsigned long tvbox_uvc_stats[TVBOX_ST_N];\n"
    "static struct tvbox_bulk tvbox_remonta;\n"
    "static uvc_stream_handle_t *tvbox_remonta_dono = NULL;\n\n"
    "void _uvc_swap_buffers(uvc_stream_handle_t *strmh) {\n    uint8_t *tmp_buf;\n"
    "    tvbox_uvc_stats[TVBOX_ST_QUADROS]++;\n",
    "stream.c/_uvc_swap_buffers",
)

s = trocar(
    s,
    "                /* This is a bulk mode transfer, so it just has one payload transfer */\n"
    "                _uvc_process_payload(strmh, transfer->buffer, transfer->actual_length);\n",
    "                /* TVBox: bulk -> junta as leituras de 16 KB ate o fim do payload (PS Vita manda o quadro inteiro num payload) */\n"
    "                tvbox_uvc_stats[TVBOX_ST_LEITURAS]++;\n"
    "                tvbox_uvc_stats[TVBOX_ST_LEITURA_MAX] = (unsigned long) transfer->length;\n"
    "                if (tvbox_remonta_dono != strmh) {\n"
    "                    tvbox_bulk_reiniciar(&tvbox_remonta);\n"
    "                    tvbox_remonta_dono = strmh;\n"
    "                }\n"
    "                {\n"
    "                    int tvbox_r = tvbox_bulk_juntar(&tvbox_remonta, transfer->buffer,\n"
    "                            (size_t) transfer->actual_length, (size_t) transfer->length,\n"
    "                            (size_t) strmh->cur_ctrl.dwMaxVideoFrameSize + 65536);\n"
    "                    if (tvbox_r == 1) {\n"
    "                        tvbox_uvc_stats[TVBOX_ST_PAYLOADS]++;\n"
    "                        tvbox_uvc_stats[TVBOX_ST_ULTIMO_PAYLOAD] = (unsigned long) tvbox_remonta.len;\n"
    "                        if (tvbox_remonta.len >= 2)\n"
    "                            tvbox_uvc_stats[TVBOX_ST_ULTIMO_HEADER] = tvbox_remonta.buf[1];\n"
    "                        _uvc_process_payload(strmh, tvbox_remonta.buf, tvbox_remonta.len);\n"
    "                        tvbox_bulk_reiniciar(&tvbox_remonta);\n"
    "                    } else if (tvbox_r < 0) {\n"
    "                        tvbox_uvc_stats[TVBOX_ST_DESCARTADOS]++;\n"
    "                    }\n"
    "                }\n",
    "stream.c/callback bulk",
)

s = trocar(
    s,
    "        case LIBUSB_TRANSFER_OVERFLOW:\n"
    "            UVC_DEBUG(\"retrying transfer, status = %d\", transfer->status);\n",
    "        case LIBUSB_TRANSFER_OVERFLOW:\n"
    "            UVC_DEBUG(\"retrying transfer, status = %d\", transfer->status);\n"
    "            tvbox_uvc_stats[TVBOX_ST_ERROS_USB]++;\n"
    "            if (transfer->num_iso_packets == 0) tvbox_bulk_reiniciar(&tvbox_remonta);\n",
    "stream.c/erros",
)

s = trocar(
    s,
    "    size_t total_transfer_size = 0;\n",
    "    size_t total_transfer_size = 0;\n"
    "    tvbox_bulk_reiniciar(&tvbox_remonta);\n"
    "    tvbox_remonta_dono = strmh;\n"
    "    tvbox_uvc_stats[TVBOX_ST_STREAMS]++;\n",
    "stream.c/uvc_stream_start",
)
gravar(caminho, s)

# ---------------------------------------------------------------- UVCPreview.cpp
caminho = os.path.join(jni, "UVCCamera", "UVCPreview.cpp")
p = ler(caminho)

ALVO_CHAMADA = "result = uvc_yuyv2rgbx(frame_yuv, frame);   // YUYV => RGBX"
ALVO_FUNC = "void UVCPreview::do_preview(uvc_stream_ctrl_t *ctrl) {"

CONVERSOR = r'''
// ---- TVBox: NV12/NV21 -> RGBX (o plugin do PS Vita so envia NV12) ----
#include <jni.h>
#include "libyuv.h"
#define TVBOX_COM_LIBYUV 1
#include "libuvc/tvbox_uvc.h"

static uvc_error_t tvbox_nv12_para_rgbx(uvc_frame_t *in, uvc_frame_t *out, bool vu) {
    const int w = in->width, h = in->height;
    const size_t minimo = (size_t) w * h * 3 / 2;
    if (w <= 0 || h <= 0 || !in->data || in->data_bytes < minimo) return UVC_ERROR_INVALID_PARAM;
    uvc_error_t r = uvc_ensure_frame_size(out, (size_t) w * h * 4);
    if (r) return r;
    out->width = w;
    out->height = h;
    out->frame_format = UVC_FRAME_FORMAT_RGBX;
    if (out->library_owns_data) out->step = w * 4;
    out->sequence = in->sequence;
    out->capture_time = in->capture_time;
    out->source = in->source;
    const uint8_t *src = (const uint8_t *) in->data;
    uint8_t *dst = (uint8_t *) out->data;
    if (tvbox_nv12_para_rgbx_yuv(src, w, h, dst, vu ? 1 : 0) != 0)
        tvbox_nv12_para_rgbx_c(src, w, h, dst, vu ? 1 : 0);
    return UVC_SUCCESS;
}

static uvc_error_t tvbox_para_rgbx(uvc_frame_t *in, uvc_frame_t *out) {
    tvbox_uvc_stats[TVBOX_ST_ULTIMO_FORMATO] = (unsigned long) in->frame_format;
    tvbox_uvc_stats[TVBOX_ST_ULTIMO_BYTES] = (unsigned long) in->data_bytes;
    tvbox_uvc_stats[TVBOX_ST_ULTIMA_LARG] = (unsigned long) in->width;
    tvbox_uvc_stats[TVBOX_ST_ULTIMA_ALT] = (unsigned long) in->height;
    uvc_error_t r;
    switch (in->frame_format) {
        case UVC_FRAME_FORMAT_NV12:
            r = tvbox_nv12_para_rgbx(in, out, false);
            break;
        case UVC_FRAME_FORMAT_NV21:
            r = tvbox_nv12_para_rgbx(in, out, true);
            break;
        case UVC_FRAME_FORMAT_YUYV:
            r = uvc_yuyv2rgbx(in, out);
            break;
        default:
            // formato mal informado: pelo tamanho, 1,5 byte por pixel e NV12
            if (in->data_bytes >= (size_t) in->width * in->height * 3 / 2 &&
                in->data_bytes < (size_t) in->width * in->height * 2)
                r = tvbox_nv12_para_rgbx(in, out, false);
            else
                r = UVC_ERROR_INVALID_PARAM;
    }
    if (r) tvbox_uvc_stats[TVBOX_ST_CONV_FALHA]++;
    else tvbox_uvc_stats[TVBOX_ST_CONV_OK]++;
    return r;
}

// Contadores para a tela de DIAGNOSTICO do Vita Link (ver tvbox_uvc.h).
extern "C" JNIEXPORT jlongArray JNICALL
Java_com_serenegiant_usb_UVCCamera_nativeTvboxStats(JNIEnv *env, jclass clazz) {
    jlongArray arr = env->NewLongArray(TVBOX_ST_N);
    if (!arr) return NULL;
    jlong v[TVBOX_ST_N];
    for (int i = 0; i < TVBOX_ST_N; i++) v[i] = (jlong) tvbox_uvc_stats[i];
    env->SetLongArrayRegion(arr, 0, TVBOX_ST_N, v);
    return arr;
}
// ---- fim TVBox ----

'''

p = trocar(p, ALVO_CHAMADA, "result = tvbox_para_rgbx(frame_yuv, frame);   // YUYV/NV12/NV21 => RGBX", "UVCPreview.cpp/chamada")
p = trocar(p, ALVO_FUNC, CONVERSOR + ALVO_FUNC, "UVCPreview.cpp/do_preview")
gravar(caminho, p)

# ---------------------------------------------------------------- UVCCamera.java
caminho = os.path.join(raiz, "java", "com", "serenegiant", "usb", "UVCCamera.java")
j = ler(caminho)
j = trocar(
    j,
    "    private native int nativeConnect(long id_camera, int fileDescriptor, int quirks);\n",
    "    private native int nativeConnect(long id_camera, int fileDescriptor, int quirks);\n\n"
    "    /** TVBox: contadores nativos do streaming (ver tvbox_uvc.h), para o diagnostico do Vita Link. */\n"
    "    public static native long[] nativeTvboxStats();\n",
    "UVCCamera.java",
)
gravar(caminho, j)

print("UVCAndroid corrigida: remontagem bulk, NV12/NV21 e contadores")
