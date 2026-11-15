package com.example.vaivan.core.util

import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object TrocaSenhaHelper {

    private const val TAMANHO_MINIMO = 6

    fun mostrar(activity: AppCompatActivity) {

        val usuario = FirebaseAuth.getInstance().currentUser
        val email = usuario?.email

        if (usuario == null || email.isNullOrBlank()) {
            Toast.makeText(
                activity,
                "Sessão expirada. Faça login novamente.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val dp = activity.resources.displayMetrics.density

        fun criarCampo(dica: String) = EditText(activity).apply {
            hint = dica
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val edtAtual = criarCampo("Senha atual")
        val edtNova = criarCampo("Nova senha")
        val edtConfirmar = criarCampo("Confirmar nova senha")

        val conteudo = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * dp).toInt(), (8 * dp).toInt(), (24 * dp).toInt(), 0)
            addView(edtAtual)
            addView(edtNova)
            addView(edtConfirmar)
        }

        val dialogo = AlertDialog.Builder(activity)
            .setTitle("Trocar senha")
            .setView(conteudo)
            .setPositiveButton("Salvar", null)
            .setNegativeButton("Cancelar", null)
            .create()

        dialogo.setOnShowListener {

            val btnSalvar = dialogo.getButton(AlertDialog.BUTTON_POSITIVE)

            btnSalvar.setOnClickListener {

                val atual = edtAtual.text.toString()
                val nova = edtNova.text.toString()
                val confirmacao = edtConfirmar.text.toString()

                edtAtual.error = null
                edtNova.error = null
                edtConfirmar.error = null

                when {
                    atual.isEmpty() -> {
                        edtAtual.error = "Informe a senha atual"
                        return@setOnClickListener
                    }
                    nova.length < TAMANHO_MINIMO -> {
                        edtNova.error = "Use pelo menos $TAMANHO_MINIMO caracteres"
                        return@setOnClickListener
                    }
                    nova == atual -> {
                        edtNova.error = "A nova senha deve ser diferente da atual"
                        return@setOnClickListener
                    }
                    nova != confirmacao -> {
                        edtConfirmar.error = "As senhas não são iguais"
                        return@setOnClickListener
                    }
                }

                btnSalvar.isEnabled = false

                activity.lifecycleScope.launch {
                    try {
                        val credencial = EmailAuthProvider.getCredential(email, atual)
                        usuario.reauthenticate(credencial).await()

                        usuario.updatePassword(nova).await()

                        Toast.makeText(
                            activity,
                            "Senha alterada com sucesso!",
                            Toast.LENGTH_LONG
                        ).show()

                        dialogo.dismiss()

                    } catch (e: FirebaseAuthInvalidCredentialsException) {
                        edtAtual.error = "Senha atual incorreta"
                        btnSalvar.isEnabled = true

                    } catch (e: FirebaseAuthInvalidUserException) {
                        Toast.makeText(
                            activity,
                            "Sessão expirada. Faça login novamente.",
                            Toast.LENGTH_LONG
                        ).show()
                        btnSalvar.isEnabled = true

                    } catch (e: FirebaseAuthWeakPasswordException) {
                        edtNova.error = "Senha muito fraca. Escolha outra."
                        btnSalvar.isEnabled = true

                    } catch (e: Exception) {
                        Toast.makeText(
                            activity,
                            "Não foi possível trocar a senha: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        btnSalvar.isEnabled = true
                    }
                }
            }
        }

        dialogo.show()
    }
}