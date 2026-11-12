package com.example.vaivan.core.notificacao

import android.content.Context
import android.content.Intent
import com.example.vaivan.ui.comum.chat.ChatActivity
import com.example.vaivan.ui.inicio.EscolhaTipoPerfilActivity
import com.example.vaivan.ui.motorista.HomeMotoristaActivity
import com.example.vaivan.ui.motorista.entrada.EntradaMotoristaActivity
import com.example.vaivan.ui.motorista.rotas.RotaDetalheActivity
import com.example.vaivan.ui.responsavel.HomeResponsavelActivity

/**
 * Para onde o toque numa notificação leva. O servidor manda "destino" e
 * "perfil" (motorista | responsavel) nos dados; a SplashActivity recebe
 * esses dados como extras com o prefixo [NotificadorVaiVan.EXTRA_PREFIXO].
 *
 * Devolve a pilha de telas a abrir (a escolha de perfil sempre fica no
 * fundo, para o "voltar" funcionar como num acesso normal), ou null quando
 * a Intent não veio de uma notificação.
 */
object DestinoNotificacao {

    /** Extra lido pelas Homes para abrir direto numa aba. */
    const val EXTRA_ABA = "abrirAba"
    const val ABA_ROTAS = "rotas"
    const val ABA_CHAT = "chat"

    fun pilhaDeTelas(context: Context, origem: Intent): Array<Intent>? {
        fun dado(chave: String) = origem.getStringExtra(NotificadorVaiVan.EXTRA_PREFIXO + chave)

        val destino = dado("destino") ?: return null
        val motorista = dado("perfil") == "motorista"

        val escolha = Intent(context, EscolhaTipoPerfilActivity::class.java)

        fun home(aba: String?) = Intent(
            context,
            if (motorista) HomeMotoristaActivity::class.java else HomeResponsavelActivity::class.java
        ).apply { aba?.let { putExtra(EXTRA_ABA, it) } }

        return when (destino) {
            // A Entrada do motorista decide a tela pelo statusMotorista
            // (análise, reenvio de documento ou Home).
            "documentos" -> arrayOf(escolha, Intent(context, EntradaMotoristaActivity::class.java))

            "chat" -> {
                val chatId = dado("chatId") ?: return arrayOf(escolha, home(ABA_CHAT))
                val conversa = Intent(context, ChatActivity::class.java)
                    .putExtra("chatId", chatId)
                    .putExtra("outroUsuarioId", dado("outroUsuarioId"))
                    .putExtra("nomeContato", dado("nomeContato"))
                arrayOf(escolha, home(ABA_CHAT), conversa)
            }

            "solicitacoes" -> {
                val rotaId = dado("rotaId")
                if (motorista && rotaId != null) {
                    val rota = Intent(context, RotaDetalheActivity::class.java)
                        .putExtra(RotaDetalheActivity.EXTRA_ROTA_ID, rotaId)
                    arrayOf(escolha, home(ABA_ROTAS), rota)
                } else {
                    arrayOf(escolha, home(ABA_ROTAS))
                }
            }

            "rotas" -> arrayOf(escolha, home(ABA_ROTAS))

            else -> null
        }
    }
}
