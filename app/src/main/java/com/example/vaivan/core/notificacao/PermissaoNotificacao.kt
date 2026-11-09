package com.example.vaivan.core.notificacao

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat

/**
 * Pede a permissão de notificações (Android 13+) explicando o motivo antes, uma única vez.
 * Deve ser criada como propriedade da Activity (para registrar o pedido a tempo).
 */
class PermissaoNotificacao(private val activity: ComponentActivity) {

    private val lancador = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* sem ação: sem permissão, as notificações continuam na Central do app */ }

    fun solicitarSeNecessario() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val jaConcedida = ContextCompat.checkSelfPermission(
            activity, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (jaConcedida) return

        val prefs = activity.getSharedPreferences("vaivan_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean(CHAVE_JA_EXPLICOU, false)) return
        prefs.edit().putBoolean(CHAVE_JA_EXPLICOU, true).apply()

        AlertDialog.Builder(activity)
            .setTitle("Ative as notificações")
            .setMessage(
                "Avisamos quando a van iniciar a viagem, quando o aluno embarcar ou chegar, " +
                        "e quando houver novas mensagens e atualizações do seu cadastro."
            )
            .setPositiveButton("Ativar") { _, _ ->
                lancador.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            .setNegativeButton("Agora não", null)
            .show()
    }

    private companion object {
        const val CHAVE_JA_EXPLICOU = "notificacao_permissao_explicada"
    }
}