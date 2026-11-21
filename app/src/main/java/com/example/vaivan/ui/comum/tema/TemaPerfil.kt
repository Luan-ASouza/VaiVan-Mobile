package com.example.vaivan.ui.comum.tema

import android.content.Intent
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.example.vaivan.R

/**
 * Cor predominante de uma tela usada pelos dois perfis (chat, locais salvos,
 * ajuda): laranja no responsável, escuro no motorista. Quem abre a tela passa
 * o perfil em [EXTRA] (ver [putTema]); sem o extra, vale o laranja.
 */
enum class TemaPerfil(
    /** Onda do canto superior do cabeçalho. */
    @DrawableRes val onda: Int,
    /** Onda larga do topo da conversa. */
    @DrawableRes val ondaChat: Int,
    /** Pílula/botão da ação principal (texto em text_on_dark). */
    @DrawableRes val botao: Int,
    /** Bolha da mensagem enviada. */
    @DrawableRes val bolhaEnviada: Int,
    /** Botão redondo de enviar mensagem. */
    @DrawableRes val botaoRedondo: Int,
    /** Quadrinho atrás de um ícone num card. */
    @DrawableRes val fundoIcone: Int,
    /** Cartão de destaque (texto em text_on_dark). */
    @DrawableRes val fundoDestaque: Int,
    /** Aba/pílula selecionada (texto em text_on_dark); a não selecionada é bg_aba. */
    @DrawableRes val abaSelecionada: Int,
    /** Cor de destaque (ícones, carregamento, números de passo). */
    @ColorRes val destaque: Int,
    /** Texto de destaque sobre fundo branco (ex.: aluno/rota da conversa). */
    @ColorRes val textoDestaque: Int,
    /** Fundo da conversa. */
    @ColorRes val fundoChat: Int
) {
    RESPONSAVEL(
        onda = R.drawable.bg_onda_passageiro,
        ondaChat = R.drawable.bg_onda_chat,
        botao = R.drawable.bg_botao_laranja,
        bolhaEnviada = R.drawable.bg_bolha_enviada,
        botaoRedondo = R.drawable.bg_botao_editar_passageiro,
        fundoIcone = R.drawable.bg_icone_responsavel,
        fundoDestaque = R.drawable.bg_hero_laranja,
        abaSelecionada = R.drawable.bg_aba_selecionada,
        destaque = R.color.passageiro_destaque_forte,
        textoDestaque = R.color.passageiro_texto_chip,
        fundoChat = R.color.chat_fundo
    ),
    MOTORISTA(
        onda = R.drawable.bg_onda_motorista,
        ondaChat = R.drawable.bg_onda_chat_motorista,
        botao = R.drawable.bg_botao_escuro,
        bolhaEnviada = R.drawable.bg_bolha_enviada_motorista,
        botaoRedondo = R.drawable.bg_botao_redondo_escuro,
        fundoIcone = R.drawable.bg_icone_motorista,
        fundoDestaque = R.drawable.bg_hero_card,
        abaSelecionada = R.drawable.bg_aba_escura_selecionada,
        destaque = R.color.rota_destaque,
        textoDestaque = R.color.rota_destaque,
        fundoChat = R.color.background_screen
    );

    companion object {
        const val EXTRA = "temaPerfil"

        fun de(intent: Intent?): TemaPerfil =
            intent?.getStringExtra(EXTRA)
                ?.let { nome -> entries.firstOrNull { it.name == nome } }
                ?: RESPONSAVEL

        fun de(ehMotorista: Boolean): TemaPerfil = if (ehMotorista) MOTORISTA else RESPONSAVEL
    }
}

/** Marca a Intent com o tema de quem está abrindo a tela. */
fun Intent.putTema(tema: TemaPerfil): Intent = putExtra(TemaPerfil.EXTRA, tema.name)
