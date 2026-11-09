package com.example.vaivan.core.notificacao

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.example.vaivan.R
import com.example.vaivan.ui.inicio.SplashActivity

/**
 * Monta e exibe a notificação do sistema com a identidade do VaiVan:
 * ícone da marca, cor laranja, título/texto em hierarquia clara e conteúdo oculto
 * na tela bloqueada (os alunos são menores de idade).
 */
object NotificadorVaiVan {

    /** Os dados enviados pelo servidor viajam na Intent com este prefixo (ex.: notif_tipo). */
    const val EXTRA_PREFIXO = "notif_"

    @SuppressLint("MissingPermission")
    fun exibir(
        context: Context,
        titulo: String,
        texto: String,
        categoria: String?,
        grupo: String?,
        dados: Map<String, String>
    ) {
        // Android 13+: sem a permissão, a notificação do sistema não aparece
        // (ela continua disponível na Central de Notificações do app).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        CanaisNotificacao.criarTodos(context)

        val canal = CanaisNotificacao.canalDe(categoria)
        // Mesma "etiqueta" = a notificação nova substitui a anterior (ex.: mesma conversa).
        val etiqueta = grupo ?: dados["notificacaoId"] ?: System.nanoTime().toString()

        val abrirApp = Intent(context, SplashActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            dados.forEach { (chave, valor) -> putExtra(EXTRA_PREFIXO + chave, valor) }
        }

        val aoTocar = PendingIntent.getActivity(
            context,
            etiqueta.hashCode(),
            abrirApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val laranja = ContextCompat.getColor(context, R.color.orange)

        // Versão exibida na tela bloqueada: sem nome de aluno, endereço ou mensagem.
        val versaoPublica = NotificationCompat.Builder(context, canal)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setColor(laranja)
            .setContentTitle("VaiVan")
            .setContentText("Você tem uma nova notificação.")
            .build()

        val construtor = NotificationCompat.Builder(context, canal)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setColor(laranja)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setAutoCancel(true)
            .setContentIntent(aoTocar)
            .setCategory(categoriaDoSistema(canal))
            .setPriority(prioridade(canal))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(versaoPublica)
            .setWhen(System.currentTimeMillis())

        iconeGrande(context)?.let { construtor.setLargeIcon(it) }

        NotificationManagerCompat.from(context).notify(etiqueta, 1, construtor.build())
    }

    private fun categoriaDoSistema(canal: String): String = when (canal) {
        CanaisNotificacao.MENSAGENS -> NotificationCompat.CATEGORY_MESSAGE
        CanaisNotificacao.COBRANCA -> NotificationCompat.CATEGORY_REMINDER
        else -> NotificationCompat.CATEGORY_EVENT
    }

    private fun prioridade(canal: String): Int = when (canal) {
        CanaisNotificacao.VIAGEM, CanaisNotificacao.MENSAGENS -> NotificationCompat.PRIORITY_HIGH
        else -> NotificationCompat.PRIORITY_DEFAULT
    }

    private fun iconeGrande(context: Context): Bitmap? = try {
        ContextCompat.getDrawable(context, R.mipmap.ic_vaivan)?.toBitmap(128, 128)
    } catch (e: Exception) {
        null
    }
}