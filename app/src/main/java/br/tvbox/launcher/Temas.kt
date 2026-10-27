package br.tvbox.launcher

/**
 * TEMAS (configuravel): para criar um novo, copie um bloco e mude as cores.
 * Cores no formato 0xAARRGGBB (paleta da pagina "PALETA E FONTES" do PDF de design).
 *  - painel / borda: cartao normal; painelFoco / anel: cartao em foco
 *  - destaque: foco e relogio; texto: texto e icones; contorno: texto sobre o destaque
 *  - corIcone / corIconeFoco: sufixo dos icones em res/drawable-nodpi
 *    (icon_<app>_<cor>.png; cores disponiveis: ambar, creme, laranja, lilas, roxo, verde)
 *  - wallpaper: nome do fundo em res/drawable-nodpi (wp_<nome>.png)
 * Para trocar de tema na TV: segure o OK (ou aperte MENU).
 * O easter egg usa o tema que estiver escolhido.
 */
class Tema(
    val id: String,
    val nome: String,
    val fundo: Int,
    val painel: Int,
    val borda: Int,
    val texto: Int,
    val destaque: Int,
    val painelFoco: Int,
    val anel: Int,
    val contorno: Int,
    val apagado: Int,
    val corIcone: String,
    val corIconeFoco: String,
    val wallpaper: String
)

val TEMAS = listOf(
    Tema(
        id = "fosforo", nome = "FOSFORO",
        fundo = 0xFF0B140D.toInt(), painel = 0xFF102417.toInt(), borda = 0xFF1F6B35.toInt(),
        texto = 0xFF33FF66.toInt(), destaque = 0xFFFFB000.toInt(),
        painelFoco = 0xFF241A00.toInt(), anel = 0xFF6B4A00.toInt(), contorno = 0xFF07110A.toInt(),
        apagado = 0xFF1F8A40.toInt(),
        corIcone = "verde", corIconeFoco = "ambar", wallpaper = "fosforo"
    ),
    Tema(
        id = "locadora", nome = "LOCADORA",
        fundo = 0xFF0E1A3A.toInt(), painel = 0xFF17285A.toInt(), borda = 0xFF3B5AA8.toInt(),
        texto = 0xFFF4E8C8.toInt(), destaque = 0xFFFF7A1A.toInt(),
        painelFoco = 0xFF3A1E08.toInt(), anel = 0xFF7A3A0E.toInt(), contorno = 0xFF0A1430.toInt(),
        apagado = 0xFF8C8A7A.toInt(),
        corIcone = "creme", corIconeFoco = "laranja", wallpaper = "locadora"
    ),
    Tema(
        id = "roxo", nome = "ROXO",
        fundo = 0xFF140A22.toInt(), painel = 0xFF1F1238.toInt(), borda = 0xFF5B2D9E.toInt(),
        texto = 0xFFE9DDFF.toInt(), destaque = 0xFFC66BFF.toInt(),
        painelFoco = 0xFF3A145C.toInt(), anel = 0xFF6A2A9A.toInt(), contorno = 0xFF0E0618.toInt(),
        apagado = 0xFF7A5FA0.toInt(),
        corIcone = "lilas", corIconeFoco = "roxo", wallpaper = "roxo"
    )
)
