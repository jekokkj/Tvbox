package br.tvbox.launcher

import android.content.Context
import org.json.JSONArray

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

    val NOMES_DESCANSO = listOf("DESLIGADO", "CAMPO", "ESTRELAS", "RELOGIO", "PS2", "CIDADE")

    /** Minutos parado ate o descanso de tela comecar. */
    val ESPERAS = listOf(1, 3, 5, 10)

    private fun p(ctx: Context) = ctx.getSharedPreferences(ARQ, Context.MODE_PRIVATE)

    fun tema(ctx: Context): String? = p(ctx).getString("tema", null)
    fun setTema(ctx: Context, id: String) { p(ctx).edit().putString("tema", id).commit() }

    /** null = automatico (a cor do tema). */
    fun corIcone(ctx: Context): String? = p(ctx).getString("corIcone", null)
    fun setCorIcone(ctx: Context, cor: String?) { p(ctx).edit().putString("corIcone", cor).commit() }

    fun corFoco(ctx: Context): String? = p(ctx).getString("corFoco", null)
    fun setCorFoco(ctx: Context, cor: String?) { p(ctx).edit().putString("corFoco", cor).commit() }

    /** 0 = desligado, 1 = campo, 2 = estrelas, 3 = relogio, 4 = ps2, 5 = cidade. */
    fun descanso(ctx: Context): Int = p(ctx).getInt("descanso", 1).coerceIn(0, NOMES_DESCANSO.size - 1)
    fun setDescanso(ctx: Context, v: Int) { p(ctx).edit().putInt("descanso", v).commit() }

    /** Indice em [ESPERAS]. */
    fun espera(ctx: Context): Int = p(ctx).getInt("espera", 1).coerceIn(0, ESPERAS.size - 1)
    fun setEspera(ctx: Context, v: Int) { p(ctx).edit().putInt("espera", v).commit() }

    // ---------------------------------------------------------------- listas guardadas como JSON

    private fun lista(ctx: Context, chave: String): JSONArray =
        try { JSONArray(p(ctx).getString(chave, "[]")) } catch (e: Exception) { JSONArray() }

    private fun setLista(ctx: Context, chave: String, a: JSONArray) { p(ctx).edit().putString(chave, a.toString()).commit() }

    /** Radios favoritas: objetos {nome, url, info}. */
    fun favoritas(ctx: Context): JSONArray = lista(ctx, "radiosFav")
    fun setFavoritas(ctx: Context, a: JSONArray) = setLista(ctx, "radiosFav", a)

    /** Podcasts assinados: objetos {nome, feed}. null = ainda nao montou a lista inicial. */
    fun podcasts(ctx: Context): JSONArray? =
        if (p(ctx).contains("podcasts")) lista(ctx, "podcasts") else null
    fun setPodcasts(ctx: Context, a: JSONArray) = setLista(ctx, "podcasts", a)

    /** Posicao (ms) em que o episodio parou; -1 = ouvido ate o fim; 0 = nunca tocado. */
    fun posicao(ctx: Context, episodio: String): Int = ctx.getSharedPreferences("pos", Context.MODE_PRIVATE).getInt(episodio, 0)
    fun setPosicao(ctx: Context, episodio: String, ms: Int) {
        ctx.getSharedPreferences("pos", Context.MODE_PRIVATE).edit().putInt(episodio, ms).apply()
    }

    /** Lembretes do painel: objetos {texto, quando (texto livre)}. */
    fun lembretes(ctx: Context): JSONArray = lista(ctx, "lembretes")
    fun setLembretes(ctx: Context, a: JSONArray) = setLista(ctx, "lembretes", a)

    /** Feeds de noticias do painel (urls). */
    fun feedsNoticias(ctx: Context): JSONArray {
        val a = lista(ctx, "feedsNoticias")
        return if (a.length() > 0) a else JSONArray().put("https://g1.globo.com/rss/g1/")
    }
    fun setFeedsNoticias(ctx: Context, a: JSONArray) = setLista(ctx, "feedsNoticias", a)

    fun cidade(ctx: Context): String = p(ctx).getString("cidade", null) ?: "São Paulo"
    fun setCidade(ctx: Context, c: String) { p(ctx).edit().putString("cidade", c).remove("cidadeGeo").commit() }

    /** Coordenadas da cidade ja procuradas: "lat,lon,nome". */
    fun cidadeGeo(ctx: Context): String? = p(ctx).getString("cidadeGeo", null)
    fun setCidadeGeo(ctx: Context, v: String) { p(ctx).edit().putString("cidadeGeo", v).commit() }

    /** Guarda a ultima resposta de um servico (para o painel funcionar sem internet). */
    fun cache(ctx: Context, chave: String): String? = ctx.getSharedPreferences("cache", Context.MODE_PRIVATE).getString(chave, null)
    fun setCache(ctx: Context, chave: String, v: String) {
        ctx.getSharedPreferences("cache", Context.MODE_PRIVATE).edit().putString(chave, v).apply()
    }

    /** Controle pelo celular ligado (servidor na rede local). */
    fun controle(ctx: Context): Boolean = p(ctx).getBoolean("controle", true)
    fun setControle(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("controle", v).commit() }
}
