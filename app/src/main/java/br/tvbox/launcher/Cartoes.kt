package br.tvbox.launcher

/**
 * CARTOES (configuravel): a ordem da lista e a ordem na tela.
 * Ao apertar OK, tenta nesta ordem:
 *  0) abrir uma tela do proprio launcher ([interno]: todos, ajustes, radio, podcasts, painel, controle);
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
        icone = "kodi", rotulo = "KODI", descricao = "FILMES, SÉRIES E MÍDIA (KODI)",
        pacotes = listOf("org.xbmc.kodi"),
        // 32 bits (armeabi-v7a): roda em qualquer TV box com Android 5 ou mais.
        web = "https://mirrors.kodi.tv/releases/android/arm/kodi-21.3-Omega-armeabi-v7a.apk"
    ),
    Cartao(
        icone = "spotify", rotulo = "SPOTIFY", descricao = "SPOTIFY CONNECT (RECEPTOR)",
        // TV Spotify (receptor proprio, br.tvbox.spotify): a caixa vira um alto-falante no app do Spotify do celular (precisa de conta Premium).
        pacotes = listOf("br.tvbox.spotify", "fi.qvr.spotlet", "com.spotify.tv.android", "com.spotify.music"),
        web = "https://github.com/qvr/spotlet/releases/latest"
    ),
    Cartao(icone = "radio", rotulo = "RÁDIO", descricao = "RÁDIOS DO BRASIL AO VIVO (GRÁTIS)", interno = "radio"),
    Cartao(icone = "podcast", rotulo = "PODCASTS", descricao = "PODCASTS POR RSS", interno = "podcasts"),
    Cartao(icone = "painel", rotulo = "PAINEL", descricao = "RELÓGIO, CLIMA, COTAÇÕES E LEMBRETES", interno = "painel"),
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
    Cartao(icone = "controle", rotulo = "CONTROLE", descricao = "CELULAR COMO CONTROLE E ENVIO DE ARQUIVOS", interno = "controle"),
    Cartao(
        icone = "todos", rotulo = "APPS", descricao = "TODOS OS APLICATIVOS",
        interno = "todos"
    ),
    Cartao(
        icone = "ajustes", rotulo = "AJUSTES", descricao = "TEMAS, DESCANSO E SISTEMA",
        interno = "ajustes"
    )
)

/** Icones 16x16 desenhados em codigo (cartoes sem arquivo PNG); pintados com a cor do icone escolhida. */
val ICONES_PIXEL: Map<String, Array<String>> = mapOf(
    "radio" to arrayOf(
        "0000000000011000", "0000000000110000", "0000000001100000", "0111111111111110",
        "1000000000000001", "1011110001111001", "1011110010000101", "1011110010110101",
        "1000000010110101", "1011110010000101", "1000000001111001", "1011110000000001",
        "1000000000000001", "0111111111111110", "0010000000000100", "0000000000000000"
    ),
    "podcast" to arrayOf(
        "0000011111100000", "0000111111110000", "0000111001110000", "0000111111110000",
        "0000111001110000", "0000111111110000", "0000111001110000", "0110111111110110",
        "0110011111100110", "0011000000001100", "0001110000111000", "0000011111100000",
        "0000000110000000", "0000000110000000", "0000011111100000", "0000000000000000"
    ),
    "painel" to arrayOf(
        "0000000110000000", "0000001111000000", "0000011111100000", "0000111001110000",
        "0001110000111000", "0011100000011100", "0111000000001110", "1111111111111111",
        "0010000000000100", "0010111001110100", "0010101001010100", "0010111001110100",
        "0010000000000100", "0010001110000100", "0010001010000100", "0011111111111100"
    ),
    "controle" to arrayOf(
        "0000111111110000", "0000100000010000", "0000101111010000", "0000101111010000",
        "0000101111010000", "0000101111010000", "0000101111010000", "0000100000010000",
        "0000101001010000", "0000100000010000", "0000101111010000", "0000100000010000",
        "0000100110010000", "0000100000010000", "0000111111110000", "0000000000000000"
    ),
    "kodi" to arrayOf(
        "0000000110000000", "0000001111000000", "0000011111100000", "0000111111110000",
        "0001111001111000", "0011111000111100", "0111111000011110", "1111111000001111",
        "1111111000001111", "0111111000011110", "0011111000111100", "0001111001111000",
        "0000111111110000", "0000011111100000", "0000001111000000", "0000000110000000"
    )
)