package br.tvbox.launcher

/**
 * CARTOES (configuravel): a ordem da lista e a ordem na tela.
 * Ao apertar OK, tenta nesta ordem:
 *  0) abrir uma tela do proprio launcher ([interno]: "todos" ou "ajustes");
 *  1) abrir o primeiro app de [pacotes] que estiver instalado;
 *  2) uma acao do sistema ([acao]);
 *  3) abrir a pagina do app na Play Store ([loja]);
 *  4) abrir a pagina de download no navegador ([web]).
 * [icone] e o nome do icone em res/drawable-nodpi (icon_<icone>_<cor>.png);
 * o icone "todos" e desenhado em codigo (grade de quadrados), nao tem arquivo.
 * [rotulo] e [descricao]: maiusculas (a fonte aceita acentos como Á É Í Ó Ú Â Ê Ô Ã Õ Ç).
 */
class Cartao(
    val icone: String,
    val rotulo: String,
    val descricao: String,
    val pacotes: List<String> = emptyList(),
    val acao: String? = null,
    val loja: String? = null,
    val web: String? = null,
    val interno: String? = null
)

val CARTOES = listOf(
    Cartao(
        icone = "youtube", rotulo = "YOUTUBE", descricao = "VÍDEOS (SMARTTUBE)",
        pacotes = listOf("org.smarttube.stable", "com.liskovsoft.smarttubetv.beta", "com.teamsmart.videomanager.tv"),
        web = "https://github.com/yuliskov/SmartTube/releases"
    ),
    Cartao(
        icone = "spotify", rotulo = "SPOTIFY", descricao = "SPOTIFY CONNECT (RECEPTOR)",
        // TV Spotify (receptor proprio, br.tvbox.spotify): a caixa vira um alto-falante no app do Spotify do celular (precisa de conta Premium).
        pacotes = listOf("br.tvbox.spotify", "fi.qvr.spotlet", "com.spotify.tv.android", "com.spotify.music"),
        web = "https://github.com/qvr/spotlet/releases/latest"
    ),
    Cartao(
        icone = "jogos", rotulo = "JOGOS", descricao = "PS VITA E RETRÔ",
        pacotes = listOf("br.tvbox.vita", "com.retroarch", "com.retroarch.aarch64", "com.google.android.play.games"),
        loja = "com.retroarch"
    ),
    Cartao(
        icone = "arquivos", rotulo = "ARQUIVOS", descricao = "SEUS ARQUIVOS",
        pacotes = listOf(
            "com.droidlogic.FileBrowser", "com.google.android.documentsui", "com.android.documentsui",
            "com.mediatek.filemanager", "com.estrongs.android.pop"
        ),
        acao = "android.settings.INTERNAL_STORAGE_SETTINGS"
    ),
    Cartao(
        icone = "todos", rotulo = "APPS", descricao = "TODOS OS APLICATIVOS",
        interno = "todos"
    ),
    Cartao(
        icone = "ajustes", rotulo = "AJUSTES", descricao = "TEMAS, DESCANSO E SISTEMA",
        interno = "ajustes"
    )
)
