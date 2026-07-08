package com.example.trabalhograua.repository

import com.google.firebase.auth.FirebaseAuth

class FirebaseAuthRepository {

    private val auth = FirebaseAuth.getInstance()

    fun cadastrar(
        email: String,
        senha: String,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {

        auth.createUserWithEmailAndPassword(email, senha)
            .addOnSuccessListener {

                val uid = it.user!!.uid

                onSuccess(uid)

            }
            .addOnFailureListener {

                onError(it)

            }

    }

    fun login(
        email: String,
        senha: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {

        auth.signInWithEmailAndPassword(email, senha)
            .addOnSuccessListener {

                onSuccess()

            }
            .addOnFailureListener {

                onError(it)

            }

    }

    fun usuarioAtual() = auth.currentUser

    fun logout() {
        auth.signOut()
    }
}