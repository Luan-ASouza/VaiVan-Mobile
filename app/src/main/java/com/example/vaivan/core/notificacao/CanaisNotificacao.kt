package com.example.vaivan.core.notificacao

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Canais de notificação do Android. Cada categoria tem o seu canal, para o usuário poder
 * desligar uma categoria sem perder as outras (boa prática e exigência do Android 8+).
 */
object CanaisNotificacao {

    const val VIAGEM = "viagem"
    const val MENSAGENS = "mensagens"
    const val CADASTRO = "cadastro"
    const val COBRANCA = "cobranca"

    /** Traduz a categoria enviada pelo servidor para o id do canal. */
    fun canalDe(categoria: String?): String = when (categoria) {
        VIAGEM -> VIAGEM
        MENSAGENS -> MENSAGENS
        COBRANCA -> COBRANCA
        else -> CADASTRO
    }

    fun criarTodos(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val gerenciador = context.getSystemService(NotificationManager::class.java) ?: return

        gerenciador.createNotificationChannels(
            listOf(
                canal(
                    VIAGEM, "Viagem e segurança",
                    "Início da viagem, embarque, chegada e avisos da van.",
                    NotificationManager.IMPORTANCE_HIGH
                ),
                canal(
                    MENSAGENS, "Mensagens",
                    "Novas mensagens do chat.",
                    NotificationManager.IMPORTANCE_HIGH
                ),
                canal(
                    CADASTRO, "Cadastro e documentos",
                    "Aprovação do cadastro, documentos e solicitações de vaga.",
                    NotificationManager.IMPORTANCE_DEFAULT
                ),
                canal(
                    COBRANCA, "Cobrança e planos",
                    "Vencimentos, atrasos e situação da assinatura.",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun canal(
        id: String,
        nome: String,
        descricao: String,
        importancia: Int
    ): NotificationChannel =
        NotificationChannel(id, nome, importancia).apply {
            description = descricao
            enableLights(true)
            lightColor = Color.parseColor("#F6B12F")
            setShowBadge(true)
        }
}