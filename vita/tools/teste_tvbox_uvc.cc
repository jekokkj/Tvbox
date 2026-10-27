// Teste automatico das correcoes do Vita Link (roda no CI, sem Android):
//  1) remontagem dos payloads bulk do PS Vita lidos em pedacos de 16 KB;
//  2) conversao NV12 -> RGBX (C e libyuv) contra cores conhecidas.
// Compilar: g++ -O2 -I<libyuv/include> -I. teste_tvbox_uvc.cc <libyuv/source/*.cc>
#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <vector>

#include "libyuv.h"
#define TVBOX_COM_LIBYUV 1
#include "tvbox_uvc.h"

unsigned long tvbox_uvc_stats[TVBOX_ST_N];

static int falhas = 0;
#define CONFERE(cond, ...)                 \
    do {                                   \
        if (!(cond)) {                     \
            std::printf("FALHOU: ");       \
            std::printf(__VA_ARGS__);      \
            std::printf("\n");             \
            falhas++;                      \
        }                                  \
    } while (0)

// Simula o host lendo o endpoint bulk em leituras de [leitura] bytes, como a libuvc no Android.
static void testar_remontagem(int w, int h, size_t leitura) {
    const size_t quadro = (size_t) w * h * 3 / 2;
    std::vector<uint8_t> payload(12 + quadro);
    payload[0] = 12;
    payload[1] = 0x80 | 0x02 | 0x01; // EOH | EOF | FID, como o plugin envia
    for (size_t i = 12; i < payload.size(); i++) payload[i] = (uint8_t) (i * 31 + 7);

    struct tvbox_bulk b = {};
    int entregues = 0;
    for (int repeticao = 0; repeticao < 3; repeticao++) { // tres quadros seguidos
        size_t pos = 0;
        bool fim = false;
        while (!fim) {
            size_t n = payload.size() - pos;
            if (n > leitura) n = leitura;
            int r = tvbox_bulk_juntar(&b, payload.data() + pos, n, leitura, 1382400 + 65536);
            pos += n;
            if (r == 1) {
                CONFERE(b.len == payload.size(), "%dx%d: payload remontado com %zu bytes, esperado %zu", w, h, b.len, payload.size());
                CONFERE(std::memcmp(b.buf, payload.data(), payload.size()) == 0, "%dx%d: conteudo diferente", w, h);
                CONFERE(pos == payload.size(), "%dx%d: entregue antes do fim (%zu de %zu)", w, h, pos, payload.size());
                entregues++;
                tvbox_bulk_reiniciar(&b);
                fim = true;
            } else {
                CONFERE(r == 0, "%dx%d: retorno inesperado %d", w, h, r);
                if (pos >= payload.size()) {
                    // payload multiplo exato da leitura: o aparelho manda um pacote de tamanho zero
                    r = tvbox_bulk_juntar(&b, nullptr, 0, leitura, 1382400 + 65536);
                    CONFERE(r == 1 && b.len == payload.size(), "%dx%d: pacote zero nao fechou o payload", w, h);
                    if (r == 1) entregues++;
                    tvbox_bulk_reiniciar(&b);
                    fim = true;
                }
            }
        }
    }
    CONFERE(entregues == 3, "%dx%d: %d quadros entregues, esperado 3", w, h, entregues);
    tvbox_bulk_liberar(&b);
    std::printf("remontagem %dx%d (leitura %zu): %d quadros ok\n", w, h, leitura, entregues);
}

static void testar_estouro() {
    struct tvbox_bulk b = {};
    std::vector<uint8_t> lixo(16384, 1);
    int r = 0;
    for (int i = 0; i < 10; i++) r = tvbox_bulk_juntar(&b, lixo.data(), lixo.size(), 16384, 50000);
    CONFERE(r == 0, "estouro: deveria esperar o fim");
    r = tvbox_bulk_juntar(&b, lixo.data(), 100, 16384, 50000);
    CONFERE(r == -1, "estouro: payload grande demais deveria ser descartado (r=%d)", r);
    CONFERE(b.len == 0, "estouro: buffer nao foi limpo");
    // depois do descarte, o proximo payload normal funciona
    r = tvbox_bulk_juntar(&b, lixo.data(), 500, 16384, 50000);
    CONFERE(r == 1 && b.len == 500, "estouro: proximo payload nao voltou ao normal");
    tvbox_bulk_liberar(&b);
    std::printf("estouro: ok\n");
}

// Cores conhecidas -> NV12 (BT.601 faixa total) -> RGBX: deve voltar perto da cor original.
static void rgb_para_yuv(int r, int g, int b, int *y, int *u, int *v) {
    double Y = 0.299 * r + 0.587 * g + 0.114 * b;
    double U = (b - Y) * 0.564 + 128;
    double V = (r - Y) * 0.713 + 128;
    *y = (int) std::lround(std::fmin(255, std::fmax(0, Y)));
    *u = (int) std::lround(std::fmin(255, std::fmax(0, U)));
    *v = (int) std::lround(std::fmin(255, std::fmax(0, V)));
}

static void testar_conversao(int w, int h) {
    static const int barras[8][3] = {
        {255, 255, 255}, {255, 255, 0}, {0, 255, 255}, {0, 255, 0},
        {255, 0, 255}, {255, 0, 0}, {0, 0, 255}, {16, 16, 16}};
    std::vector<uint8_t> nv12((size_t) w * h * 3 / 2), nv21(nv12.size());
    uint8_t *yp = nv12.data(), *cp = nv12.data() + (size_t) w * h;
    for (int y = 0; y < h; y++)
        for (int x = 0; x < w; x++) {
            const int *c = barras[x * 8 / w];
            int Y, U, V;
            rgb_para_yuv(c[0], c[1], c[2], &Y, &U, &V);
            yp[(size_t) y * w + x] = (uint8_t) Y;
            if ((y & 1) == 0 && (x & 1) == 0) {
                cp[(size_t) (y / 2) * w + x] = (uint8_t) U;
                cp[(size_t) (y / 2) * w + x + 1] = (uint8_t) V;
            }
        }
    std::memcpy(nv21.data(), nv12.data(), (size_t) w * h);
    for (size_t i = (size_t) w * h; i + 1 < nv12.size(); i += 2) {
        nv21[i] = nv12[i + 1];
        nv21[i + 1] = nv12[i];
    }

    std::vector<uint8_t> rc((size_t) w * h * 4), ry(rc.size()), rc21(rc.size()), ry21(rc.size());
    tvbox_nv12_para_rgbx_c(nv12.data(), w, h, rc.data(), 0);
    tvbox_nv12_para_rgbx_c(nv21.data(), w, h, rc21.data(), 1);
    int r1 = tvbox_nv12_para_rgbx_yuv(nv12.data(), w, h, ry.data(), 0);
    int r2 = tvbox_nv12_para_rgbx_yuv(nv21.data(), w, h, ry21.data(), 1);
    CONFERE(r1 == 0 && r2 == 0, "libyuv devolveu erro (%d, %d)", r1, r2);

    int piorC = 0, piorY = 0, piorDif = 0;
    for (int b = 0; b < 8; b++) {
        int x = (2 * b + 1) * w / 16; // meio de cada barra (longe das bordas, onde a cor e misturada)
        int y = h / 2;
        size_t i = ((size_t) y * w + x) * 4;
        for (int k = 0; k < 3; k++) {
            piorC = std::max(piorC, std::abs(rc[i + k] - barras[b][k]));
            piorY = std::max(piorY, std::abs(ry[i + k] - barras[b][k]));
            piorDif = std::max(piorDif, std::abs(rc21[i + k] - rc[i + k]));
            piorDif = std::max(piorDif, std::abs(ry21[i + k] - ry[i + k]));
        }
        CONFERE(rc[i + 3] == 255 && ry[i + 3] == 255, "alfa diferente de 255 na barra %d", b);
        if (b == 5) // vermelho puro: confere a ORDEM dos bytes (R primeiro)
            CONFERE(ry[i] > 200 && ry[i + 2] < 60, "libyuv com R e B trocados: R=%d B=%d", ry[i], ry[i + 2]);
    }
    CONFERE(piorC <= 6, "conversao em C erra a cor em ate %d niveis", piorC);
    CONFERE(piorY <= 8, "conversao libyuv erra a cor em ate %d niveis", piorY);
    CONFERE(piorDif <= 1, "NV21 e NV12 dao resultados diferentes (%d)", piorDif);
    std::printf("conversao %dx%d: erro C %d, erro libyuv %d\n", w, h, piorC, piorY);
}

int main() {
    // Todas as resolucoes que o plugin oferece, com a leitura de 16 KB da libuvc no Android.
    const int res[5][2] = {{960, 544}, {896, 504}, {864, 488}, {480, 272}, {1280, 720}};
    for (auto &r : res) testar_remontagem(r[0], r[1], 16384);
    testar_remontagem(960, 544, 512);          // leituras pequenas
    testar_remontagem(64, 42, 16384);          // payload menor que uma leitura
    // payload multiplo exato da leitura (12 + quadro = 16384*2) para exercitar o pacote zero
    {
        struct tvbox_bulk b = {};
        std::vector<uint8_t> d(16384, 9);
        int r = tvbox_bulk_juntar(&b, d.data(), 16384, 16384, 1 << 20);
        CONFERE(r == 0, "multiplo exato: fechou cedo");
        r = tvbox_bulk_juntar(&b, d.data(), 16384, 16384, 1 << 20);
        CONFERE(r == 0, "multiplo exato: fechou cedo (2)");
        r = tvbox_bulk_juntar(&b, nullptr, 0, 16384, 1 << 20);
        CONFERE(r == 1 && b.len == 32768, "multiplo exato: pacote zero nao fechou");
        tvbox_bulk_liberar(&b);
        std::printf("multiplo exato + pacote zero: ok\n");
    }
    testar_estouro();
    for (auto &r : res) testar_conversao(r[0], r[1]);

    if (falhas) {
        std::printf("\n%d FALHA(S)\n", falhas);
        return 1;
    }
    std::printf("\nTUDO OK\n");
    return 0;
}
