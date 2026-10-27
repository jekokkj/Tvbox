package br.tvbox.launcher

import android.content.Context

/** Escolhas do usuario, guardadas no aparelho (SharedPreferences "cfg"). */
object Config {
    private const val ARQ = "cfg"

    /** Cores de icone disponiveis (existem arquivos icon_<app>_<cor>.png para todas). */
    val CORES = listOf("verde", "ambar", "creme", "laranja", "lilas", "roxo")

    /** Cor aproximada de cada nome, so para mostrar a amostra nos ajustes. */
    val COR_RGB: Map<String, Int> = mapOf(
        "verde" to 0xFF33FF66.toInt(),
        "ambar" to 0xFFFFB000.toInt(),
        "creme" to 0xFFF4E8C8.toInt(),
        "laranja" to 0xFFFF7A1A.toInt(),
        "lilas" to 0xFFC9A6FF.toInt(),
        "roxo" to 0xFF9B4DFF.toInt()
    )

    val NOMES_DESCANSO = listOf("DESLIGADO", "CAMPO", "ESTRELAS", "RELOGIO")

    /** Minutos parado ate o descanso de tela comecar. */
    val ESPERAS = listOf(1, 3, 5, 10)

    private fun p(ctx: Context) = ctx.getSharedPreferences(ARQ, Context.MODE_PRIVATE)

    fun tema(ctx: Context): String? = p(ctx).getString("tema", null)
    fun setTema(ctx: Context, id: String) { p(ctx).edit().putString("tema", id).apply() }

    /** null = automatico (a cor do tema). */
    fun corIcone(ctx: Context): String? = p(ctx).getString("corIcone", null)
    fun setCorIcone(ctx: Context, cor: String?) { p(ctx).edit().putString("corIcone", cor).apply() }

    fun corFoco(ctx: Context): String? = p(ctx).getString("corFoco", null)
    fun setCorFoco(ctx: Context, cor: String?) { p(ctx).edit().putString("corFoco", cor).apply() }

    /** 0 = desligado, 1 = campo, 2 = estrelas, 3 = relogio. */
    fun descanso(ctx: Context): Int = p(ctx).getInt("descanso", 1).coerceIn(0, 3)
    fun setDescanso(ctx: Context, v: Int) { p(ctx).edit().putInt("descanso", v).apply() }

    /** Indice em [ESPERAS]. */
    fun espera(ctx: Context): Int = p(ctx).getInt("espera", 1).coerceIn(0, ESPERAS.size - 1)
    fun setEspera(ctx: Context, v: Int) { p(ctx).edit().putInt("espera", v).apply() }
}
