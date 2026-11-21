package com.example.vaivan.ui.motorista.chat

import com.example.vaivan.ui.comum.chat.ListaConversasFragment
import com.example.vaivan.ui.comum.tema.TemaPerfil

/** Aba de mensagens do motorista: lista as conversas reais (com os responsáveis aprovados). */
class ChatFragment : ListaConversasFragment() {
    override val subtitulo = "Converse com os responsáveis dos alunos"
    override val tema = TemaPerfil.MOTORISTA
}
