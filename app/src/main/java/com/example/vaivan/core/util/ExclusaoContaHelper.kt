package com.example.vaivan.core.util

import android.content.Intent
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.ui.inicio.LoginActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Fluxo de "excluir minha conta" (confirmação + reautenticação + navegação de
 * volta pro Login) — igual pros dois perfis, só muda qual ExcluirContaUseCase
 * é chamado, por isso fica aqui em vez de duplicado nos dois PerfilFragment.
 *
 * O FirebaseAuth exige login recente pra excluir a conta, por isso pede a
 * senha atual antes de reautenticar.
 */
object ExclusaoContaHelper {

    fun confirmarExclusaoConta(
        fragment: Fragment,
        excluirDados: suspend (uid: String) -> Unit
    ) {
        val contexto = fragment.requireContext()

        ConfirmacaoExclusaoHelper.confirmar(
            contexto = contexto,
            titulo = "Excluir sua conta?",
            mensagem = "Essa ação é permanente e remove seus dados cadastrados " +
                    "(veículos, rotas, passageiros, documentos etc.). " +
                    "Para confirmar, informe sua senha atual.",
            textoConfirmar = "Continuar"
        ) {
            pedirSenhaEExcluir(fragment, excluirDados)
        }
    }

    private fun pedirSenhaEExcluir(
        fragment: Fragment,
        excluirDados: suspend (uid: String) -> Unit
    ) {
        val contexto = fragment.requireContext()

        val campoSenha = EditText(contexto).apply {
            hint = "Senha atual"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val padding = (20 * contexto.resources.displayMetrics.density).toInt()
        val container = LinearLayout(contexto).apply {
            setPadding(padding, padding, padding, padding)
            addView(campoSenha)
        }

        MaterialAlertDialogBuilder(contexto)
            .setTitle("Confirme sua senha")
            .setView(container)
            .setPositiveButton("Excluir conta") { _, _ ->
                val senha = campoSenha.text?.toString()?.trim() ?: ""

                if (senha.isBlank()) {
                    Toast.makeText(contexto, "Informe sua senha.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                executarExclusao(fragment, senha, excluirDados)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun executarExclusao(
        fragment: Fragment,
        senha: String,
        excluirDados: suspend (uid: String) -> Unit
    ) {
        val contexto = fragment.requireContext()
        val usuario = FirebaseAuth.getInstance().currentUser
        val email = usuario?.email
        val uid = usuario?.uid

        if (usuario == null || email == null || uid == null) {
            Toast.makeText(contexto, "Sessão expirada. Faça login novamente.", Toast.LENGTH_SHORT).show()
            return
        }

        fragment.viewLifecycleOwner.lifecycleScope.launch {
            try {
                val credencial = EmailAuthProvider.getCredential(email, senha)
                usuario.reauthenticate(credencial).await()

                excluirDados(uid)

                withContext(Dispatchers.IO) {
                    VaivanDatabase.getInstance(contexto).clearAllTables()
                }

                usuario.delete().await()

                val intent = Intent(contexto, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                contexto.startActivity(intent)

            } catch (erro: Exception) {
                Toast.makeText(contexto, "Erro ao excluir conta: ${erro.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
