package com.example.vaivan.core.notificacao

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Endereço do aparelho para receber push. Fica em dispositivos/{uid}/tokens/{token}
 * (coleção privada: só o próprio usuário lê e grava; o servidor lê para enviar).
 */
object TokenDispositivo {

    private const val TAG = "TokenDispositivo"

    /** Registra o token atual do aparelho para o usuário logado. */
    suspend fun registrar() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            gravar(uid, token)
        } catch (e: Exception) {
            Log.w(TAG, "Não foi possível registrar o token do aparelho", e)
        }
    }

    suspend fun gravar(uid: String, token: String) {
        FirebaseFirestore.getInstance()
            .collection("dispositivos").document(uid)
            .collection("tokens").document(token)
            .set(
                mapOf(
                    "token" to token,
                    "plataforma" to "android",
                    "atualizadoEm" to System.currentTimeMillis()
                )
            )
            .await()
    }

    /**
     * Chamar ANTES do signOut: o aparelho deixa de receber as notificações desta conta
     * (importante se outra pessoa entrar no mesmo celular). Nunca trava a saída da conta.
     */
    suspend fun removerDesteDispositivo() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        try {
            withTimeoutOrNull(4_000L) {
                val mensageiro = FirebaseMessaging.getInstance()
                val token = mensageiro.token.await()

                // Invalida o token (é o que realmente impede o recebimento)...
                mensageiro.deleteToken().await()

                // ...e limpa o registro no banco.
                FirebaseFirestore.getInstance()
                    .collection("dispositivos").document(uid)
                    .collection("tokens").document(token)
                    .delete()
                    .await()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Não foi possível remover o token do aparelho", e)
        }
    }
}