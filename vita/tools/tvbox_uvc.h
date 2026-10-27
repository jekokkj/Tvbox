/*
 * TVBox / Vita Link: correcoes da libuvc para o plugin vita-udcd-uvc (PS Vita).
 *
 * 1) Remontagem de payload bulk.
 *    O Vita manda cada quadro inteiro como UM payload UVC (12 bytes de cabecalho + imagem NV12,
 *    ~0,2 a 1,4 MB) num endpoint bulk. No Android a libuvc limita cada leitura bulk a 16 KB e trata
 *    cada leitura como um payload com cabecalho proprio: os bytes da imagem viram "cabecalhos" e o
 *    quadro nunca fecha. Aqui as leituras sao juntadas ate chegar uma leitura curta (fim do payload,
 *    regra do USB bulk) e so entao o payload inteiro e processado.
 *
 * 2) Conversao NV12/NV21 -> RGBX (a libuvc original so converte YUYV).
 *
 * Este arquivo e copiado para libuvc/include/libuvc/ pelo patch_uvc.py e tambem e usado pelo
 * teste automatico (vita/tools/teste_tvbox_uvc.c), que roda no CI sem Android.
 */
#ifndef TVBOX_UVC_H
#define TVBOX_UVC_H

#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Contadores para a tela de DIAGNOSTICO (definidos em stream.c). */
enum {
    TVBOX_ST_LEITURAS = 0,      /* leituras bulk concluidas */
    TVBOX_ST_PAYLOADS,          /* payloads remontados e entregues */
    TVBOX_ST_ULTIMO_PAYLOAD,    /* tamanho do ultimo payload (bytes) */
    TVBOX_ST_DESCARTADOS,       /* payloads maiores que o buffer (descartados) */
    TVBOX_ST_ERROS_USB,         /* transferencias com erro/timeout/stall */
    TVBOX_ST_QUADROS,           /* quadros completos montados pela libuvc */
    TVBOX_ST_ULTIMO_HEADER,     /* byte de flags do ultimo cabecalho */
    TVBOX_ST_LEITURA_MAX,       /* tamanho de cada leitura bulk pedida */
    TVBOX_ST_CONV_OK,           /* quadros convertidos para RGBX */
    TVBOX_ST_CONV_FALHA,        /* quadros recusados na conversao */
    TVBOX_ST_ULTIMO_FORMATO,    /* uvc_frame_format do ultimo quadro */
    TVBOX_ST_ULTIMO_BYTES,      /* data_bytes do ultimo quadro */
    TVBOX_ST_ULTIMA_LARG,
    TVBOX_ST_ULTIMA_ALT,
    TVBOX_ST_STREAMS,           /* quantas vezes o streaming comecou */
    TVBOX_ST_N = 16
};
extern unsigned long tvbox_uvc_stats[TVBOX_ST_N];

/* ------------------------------------------------------------------ remontagem bulk */

struct tvbox_bulk {
    uint8_t *buf;
    size_t cap;
    size_t len;
    int estourou;
};

static inline void tvbox_bulk_reiniciar(struct tvbox_bulk *b) {
    b->len = 0;
    b->estourou = 0;
}

static inline void tvbox_bulk_liberar(struct tvbox_bulk *b) {
    free(b->buf);
    b->buf = NULL;
    b->cap = 0;
    tvbox_bulk_reiniciar(b);
}

/*
 * Junta uma leitura bulk. [lido] e o que chegou, [pedido] o tamanho da leitura submetida,
 * [capacidade] o maior payload esperado. Devolve 1 quando um payload completo esta em b->buf
 * (b->len bytes): o chamador processa e depois chama tvbox_bulk_reiniciar. Devolve 0 enquanto
 * o payload continua e -1 quando o payload terminou mas foi descartado (grande demais / sem memoria).
 */
static inline int tvbox_bulk_juntar(struct tvbox_bulk *b, const uint8_t *dados, size_t lido,
                                    size_t pedido, size_t capacidade) {
    if (b->cap < capacidade) {
        uint8_t *novo = (uint8_t *) realloc(b->buf, capacidade);
        if (novo) {
            b->buf = novo;
            b->cap = capacidade;
        } else {
            b->estourou = 1;
        }
    }
    if (lido > 0 && !b->estourou) {
        if (b->len + lido <= b->cap) {
            memcpy(b->buf + b->len, dados, lido);
            b->len += lido;
        } else {
            b->estourou = 1;
        }
    }
    if (lido < pedido) { /* leitura curta (ou pacote de tamanho zero): fim do payload */
        if (b->estourou) {
            tvbox_bulk_reiniciar(b);
            return -1;
        }
        return b->len > 0 ? 1 : 0;
    }
    return 0;
}

/* ------------------------------------------------------------------ NV12/NV21 -> RGBX */

static inline uint8_t tvbox_limitar(int v) { return (uint8_t) (v < 0 ? 0 : (v > 255 ? 255 : v)); }

/*
 * Converte NV12 (vu=0) ou NV21 (vu=1) para RGBX (bytes R,G,B,255), BT.601 faixa total
 * (o plugin gera NV12 com o conversor de cor do proprio Vita). Versao em C puro: usada no
 * teste e como reserva se a libyuv falhar.
 */
static inline void tvbox_nv12_para_rgbx_c(const uint8_t *src, int w, int h, uint8_t *dst, int vu) {
    const uint8_t *yp = src;
    const uint8_t *cp = src + (size_t) w * h;
    int y, x;
    for (y = 0; y < h; y++) {
        const uint8_t *yl = yp + (size_t) y * w;
        const uint8_t *cl = cp + (size_t) (y >> 1) * w;
        uint8_t *d = dst + (size_t) y * w * 4;
        for (x = 0; x < w; x++) {
            int Y = yl[x];
            int c0 = cl[x & ~1] - 128;
            int c1 = cl[(x & ~1) + 1] - 128;
            int U = vu ? c1 : c0;
            int V = vu ? c0 : c1;
            d[0] = tvbox_limitar(Y + ((91881 * V) >> 16));
            d[1] = tvbox_limitar(Y - ((22554 * U + 46802 * V) >> 16));
            d[2] = tvbox_limitar(Y + ((116130 * U) >> 16));
            d[3] = 255;
            d += 4;
        }
    }
}

#ifdef __cplusplus
}
#endif

#if defined(__cplusplus) && defined(TVBOX_COM_LIBYUV)
/*
 * Mesma conversao pela libyuv (otimizada com NEON no ARM). Devolve 0 se deu certo.
 * A libyuv chama de "ARGB" os bytes B,G,R,A; passando o plano de cor com U/V trocados junto com a
 * matriz espelhada (kYvuJPEGConstants = BT.601 faixa total) a saida sai em R,G,B,A — o mesmo truque
 * que a propria libyuv usa em NV12ToABGR.
 */
static inline int tvbox_nv12_para_rgbx_yuv(const uint8_t *src, int w, int h, uint8_t *dst, int vu) {
    const uint8_t *y = src;
    const uint8_t *c = src + (size_t) w * h;
    if (!vu)
        return libyuv::NV21ToARGBMatrix(y, w, c, w, dst, w * 4, &libyuv::kYvuJPEGConstants, w, h);
    return libyuv::NV12ToARGBMatrix(y, w, c, w, dst, w * 4, &libyuv::kYvuJPEGConstants, w, h);
}
#endif

#endif /* TVBOX_UVC_H */
