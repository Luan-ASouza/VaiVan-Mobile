package com.example.vaivan.ui.ajuda

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.repository.UsuarioRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Abre uma conversa no WhatsApp da equipe, já com uma mensagem pronta
 * que diz quem está pedindo ajuda (nome e tipo de conta).
 *
 * Para trocar o número, altere [NUMERO] (código do país + DDD + número, só dígitos).
 */
object WhatsAppHelper {

    private const val NUMERO = "5554996395035"

    fun abrir(
        activity: AppCompatActivity,
        perfil: PerfilAjuda?,
        assunto: String? = null
    ) {
        activity.lifecycleScope.launch {

            val nome = buscarNome(activity)
            val mensagem = montarMensagem(nome, perfil, assunto)

            val uri = Uri.parse("https://wa.me/$NUMERO?text=" + Uri.encode(mensagem))

            try {
                activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(
                    activity,
                    "Não foi possível abrir o WhatsApp.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private suspend fun buscarNome(activity: AppCompatActivity): String? {

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return null

        return try {
            UsuarioRepository(VaivanDatabase.getInstance(activity).usuarioDao())
                .observarUsuario(uid)
                .first()
                ?.nome
        } catch (e: Exception) {
            null
        }
    }

    private fun montarMensagem(
        nome: String?,
        perfil: PerfilAjuda?,
        assunto: String?
    ): String {

        val quem = when {
            !nome.isNullOrBlank() && perfil != null -> "Sou $nome (${perfil.rotulo.lowercase()})"
            !nome.isNullOrBlank() -> "Sou $nome"
            perfil != null -> "Sou ${perfil.rotulo.lowercase()}"
            else -> null
        }

        val duvida = if (assunto.isNullOrBlank()) {
            "preciso de ajuda com o app VaiVan"
        } else {
            "preciso de ajuda com o app VaiVan sobre: $assunto"
        }

        return if (quem != null) "Olá! $quem e $duvida." else "Olá! Eu $duvida."
    }
}