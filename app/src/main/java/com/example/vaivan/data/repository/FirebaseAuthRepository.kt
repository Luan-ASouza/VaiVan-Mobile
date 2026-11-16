package com.example.vaivan.data.repository

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
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

    /** Cria a conta no Auth e já deixa a sessão aberta nela. Se o e-mail já
     * existir, lança `FirebaseAuthUserCollisionException`. */
    suspend fun criarConta(email: String, senha: String): FirebaseUser =
        auth.createUserWithEmailAndPassword(email, senha).await().user
            ?: throw IllegalStateException("Usuário criado sem UID.")

    /** Entra numa conta existente. Senha errada lança
     * `FirebaseAuthInvalidCredentialsException`. O usuário devolvido vem com
     * `isEmailVerified` atualizado (token novo). */
    suspend fun entrar(email: String, senha: String): FirebaseUser =
        auth.signInWithEmailAndPassword(email, senha).await().user
            ?: throw IllegalStateException("Login sem usuário.")

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