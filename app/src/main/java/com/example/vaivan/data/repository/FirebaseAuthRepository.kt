package com.example.vaivan.data.repository

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository {

    private val auth = FirebaseAuth.getInstance()

    fun recuperarSenha(
        email: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {

        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { exception ->
                onError(exception)
            }
    }

    fun cadastrar(
        email: String,
        senha: String,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {

        auth.createUserWithEmailAndPassword(
            email,
            senha
        )
            .addOnSuccessListener { result ->

                val uid = result.user?.uid

                if (uid != null) {
                    onSuccess(uid)
                } else {
                    onError(
                        IllegalStateException(
                            "Usuário criado sem UID."
                        )
                    )
                }
            }
            .addOnFailureListener { exception ->
                onError(exception)
            }
    }

    fun verificarEmail(
        email: String,
        onResult: (Boolean) -> Unit,
        onError: (Exception) -> Unit
    ) {

        @Suppress("DEPRECATION")
        auth.fetchSignInMethodsForEmail(email)
            .addOnSuccessListener { result ->

                val existe =
                    !result.signInMethods.isNullOrEmpty()

                onResult(existe)
            }
            .addOnFailureListener { exception ->
                onError(exception)
            }
    }

    fun login(
        email: String,
        senha: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {

        auth.signInWithEmailAndPassword(
            email,
            senha
        )
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { exception ->
                onError(exception)
            }
    }

    /**
     * Troca a senha do usuário logado. Reautentica com a senha atual antes
     * (o Firebase exige login recente pra `updatePassword`).
     * Erros do Firebase são propagados (ex.: FirebaseAuthInvalidCredentialsException
     * = senha atual errada).
     */
    suspend fun alterarSenha(
        senhaAtual: String,
        novaSenha: String
    ) {
        val usuario = auth.currentUser
            ?: throw FirebaseAuthInvalidUserException("sem-sessao", "Sessão expirada.")
        val email = usuario.email
            ?: throw FirebaseAuthInvalidUserException("sem-email", "Conta sem e-mail.")

        usuario.reauthenticate(EmailAuthProvider.getCredential(email, senhaAtual)).await()
        usuario.updatePassword(novaSenha).await()
    }

    fun usuarioAtual() =
        auth.currentUser

    fun logout() {
        auth.signOut()
    }
}