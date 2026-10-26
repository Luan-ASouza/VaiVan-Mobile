package com.example.vaivan.ui.motorista.assinatura

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.vaivan.data.models.Assinatura
import com.example.vaivan.domain.usecase.assinatura.AssinaturaPolicy
import com.example.vaivan.domain.usecase.assinatura.AssinaturaPolicy.Estado
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Funções de apoio compartilhadas pelas telas de assinatura. */
object AssinaturaUi {

    private val localeBr = Locale("pt", "BR")

    fun formatarValor(centavos: Long): String =
        NumberFormat.getCurrencyInstance(localeBr).format(centavos / 100.0)

    fun formatarData(milissegundos: Long): String =
        SimpleDateFormat("dd/MM/yyyy", localeBr).format(Date(milissegundos))

    /** Rótulo e cor do "selo" de status da assinatura. */
    fun rotuloDoEstado(estado: Estado, cancelamentoAgendado: Boolean): Pair<String, Int> =
        when (estado) {
            Estado.SEM_PLANO -> Pair("Sem plano", Color.parseColor("#6B6B6B"))
            Estado.AGUARDANDO_PAGAMENTO -> Pair("Pagamento pendente", Color.parseColor("#F6B12F"))
            Estado.ATIVA ->
                if (cancelamentoAgendado) {
                    Pair("Cancelamento agendado", Color.parseColor("#F6B12F"))
                } else {
                    Pair("Ativa", Color.parseColor("#2CA144"))
                }
            Estado.EM_CARENCIA -> Pair("Pagamento em atraso", Color.parseColor("#F6B12F"))
            Estado.SUSPENSA -> Pair("Suspensa", Color.parseColor("#E53935"))
            Estado.EXPIRADA -> Pair("Expirada", Color.parseColor("#6B6B6B"))
            Estado.CANCELADA -> Pair("Cancelada", Color.parseColor("#6B6B6B"))
        }

    /** Texto do aviso exibido no topo da Home do motorista (null = sem aviso). */
    fun mensagemDoBanner(estado: Estado, assinatura: Assinatura?): String? =
        when (estado) {
            Estado.SEM_PLANO ->
                "Você ainda não tem um plano. Toque aqui para escolher e liberar a criação de rotas."
            Estado.AGUARDANDO_PAGAMENTO ->
                "Pagamento pendente. Toque aqui para concluir e ativar o seu plano."
            Estado.EM_CARENCIA ->
                "Pagamento em atraso. Regularize até " +
                        formatarData(AssinaturaPolicy.dataLimiteDaCarencia(assinatura!!)) +
                        " para evitar a suspensão."
            Estado.SUSPENSA ->
                "Assinatura suspensa: novas rotas e novos passageiros estão bloqueados. Toque aqui para regularizar."
            Estado.EXPIRADA, Estado.CANCELADA ->
                "Seu plano não está ativo. Toque aqui para assinar novamente."
            Estado.ATIVA ->
                if (assinatura != null && assinatura.cancelamentoAgendado) {
                    "Seu plano termina em ${formatarData(assinatura.proximaCobranca)}. Toque aqui para ver."
                } else {
                    null
                }
        }

    fun corDoBanner(estado: Estado): Int =
        when (estado) {
            Estado.SUSPENSA -> Color.parseColor("#E53935")
            else -> Color.parseColor("#F6B12F")
        }

    /** Aviso quando uma ação é bloqueada pelo plano, com atalho para a tela de assinatura. */
    fun mostrarBloqueio(context: Context, mensagem: String) {
        AlertDialog.Builder(context)
            .setTitle("Ação indisponível")
            .setMessage(mensagem)
            .setPositiveButton("Ver planos") { _, _ ->
                context.startActivity(Intent(context, AssinaturaActivity::class.java))
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    /** Evita que o conteúdo fique embaixo das barras do sistema (tela cheia). */
    fun aplicarInsets(raiz: View) {
        ViewCompat.setOnApplyWindowInsetsListener(raiz) { view, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(raiz)
    }
}