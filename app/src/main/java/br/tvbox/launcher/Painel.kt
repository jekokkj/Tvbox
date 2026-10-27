package br.tvbox.launcher

import android.graphics.Canvas

/** Uma tela inteira que abre por cima do launcher (apps, ajustes, debloat, descanso de tela). */
interface Painel {
    /** false = a tela desenha o proprio fundo (descanso de tela). */
    val usaFundo: Boolean get() = true

    fun desenhar(cv: Canvas, w: Float, h: Float, t: Tema)

    /** Tecla curta. Devolve true se consumiu. */
    fun tecla(k: Int): Boolean

    /** OK segurado. Devolve true se consumiu. */
    fun teclaLonga(): Boolean = false

    /** Botao VOLTAR. Devolve true se o painel deve ser fechado (false = tratou por dentro). */
    fun voltar(): Boolean = true

    /** O launcher voltou ao primeiro plano (ex.: depois de uma tela do sistema). */
    fun aoRetomar() {}

    fun aoFechar() {}
}
