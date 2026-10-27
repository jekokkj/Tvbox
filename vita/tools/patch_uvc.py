"""Corrige o UVCPreview.cpp da UVCAndroid para aceitar video NV12 (o que o plugin do Vita envia).

Original: todo quadro nao comprimido passa por uvc_yuyv2rgbx(), que recusa qualquer formato
que nao seja YUYV -> nenhum quadro chega na tela. Aqui, NV12/NV21 viram RGBX por uma conversao propria.
Uso: python3 patch_uvc.py caminho/UVCPreview.cpp
"""
import sys

caminho = sys.argv[1]
fonte = open(caminho, encoding="utf-8").read()

ALVO_CHAMADA = "result = uvc_yuyv2rgbx(frame_yuv, frame);   // YUYV => RGBX"
NOVA_CHAMADA = "result = tvbox_para_rgbx(frame_yuv, frame);   // YUYV/NV12/NV21 => RGBX"
ALVO_FUNC = "void UVCPreview::do_preview(uvc_stream_ctrl_t *ctrl) {"

assert fonte.count(ALVO_CHAMADA) == 1, "chamada uvc_yuyv2rgbx nao encontrada (versao diferente?)"
assert fonte.count(ALVO_FUNC) == 1, "do_preview nao encontrado"

CONVERSOR = r'''
// ---- TVBox: conversao NV12/NV21 -> RGBX (BT.601, faixa total, como no plugin vita-udcd-uvc) ----
static inline uint8_t tvbox_limitar(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }

static uvc_error_t tvbox_nv12_para_rgbx(uvc_frame_t *in, uvc_frame_t *out, bool vu) {
    const int w = in->width, h = in->height;
    const size_t minimo = (size_t) w * h * 3 / 2;
    if (!in->data || in->data_bytes < minimo) return UVC_ERROR_INVALID_PARAM;
    uvc_error_t r = uvc_ensure_frame_size(out, (size_t) w * h * 4);
    if (r) return r;
    out->width = w;
    out->height = h;
    out->frame_format = UVC_FRAME_FORMAT_RGBX;
    if (out->library_owns_data) out->step = w * 4;
    out->sequence = in->sequence;
    out->capture_time = in->capture_time;
    out->source = in->source;

    const uint8_t *y_plano = (const uint8_t *) in->data;
    const uint8_t *c_plano = y_plano + (size_t) w * h;
    uint8_t *dst = (uint8_t *) out->data;
    for (int y = 0; y < h; y++) {
        const uint8_t *yl = y_plano + (size_t) y * w;
        const uint8_t *cl = c_plano + (size_t) (y >> 1) * w;
        uint8_t *d = dst + (size_t) y * w * 4;
        for (int x = 0; x < w; x++) {
            const int Y = yl[x];
            const int c0 = cl[x & ~1] - 128;
            const int c1 = cl[(x & ~1) + 1] - 128;
            const int U = vu ? c1 : c0;
            const int V = vu ? c0 : c1;
            d[0] = tvbox_limitar(Y + ((91881 * V) >> 16));                    // R
            d[1] = tvbox_limitar(Y - ((22554 * U + 46802 * V) >> 16));        // G
            d[2] = tvbox_limitar(Y + ((116130 * U) >> 16));                   // B
            d[3] = 255;
            d += 4;
        }
    }
    return UVC_SUCCESS;
}

static uvc_error_t tvbox_para_rgbx(uvc_frame_t *in, uvc_frame_t *out) {
    switch (in->frame_format) {
        case UVC_FRAME_FORMAT_NV12:
            return tvbox_nv12_para_rgbx(in, out, false);
        case UVC_FRAME_FORMAT_NV21:
            return tvbox_nv12_para_rgbx(in, out, true);
        case UVC_FRAME_FORMAT_YUYV:
            return uvc_yuyv2rgbx(in, out);
        default:
            // formato nao informado direito: pelo tamanho, 1,5 byte/pixel e NV12; 2 bytes/pixel e YUYV
            if (in->data_bytes == (size_t) in->width * in->height * 3 / 2)
                return tvbox_nv12_para_rgbx(in, out, false);
            return UVC_ERROR_INVALID_PARAM;
    }
}
// ---- fim TVBox ----

'''

fonte = fonte.replace(ALVO_CHAMADA, NOVA_CHAMADA)
fonte = fonte.replace(ALVO_FUNC, CONVERSOR + ALVO_FUNC)
open(caminho, "w", encoding="utf-8").write(fonte)
print("UVCPreview.cpp corrigido (NV12/NV21)")