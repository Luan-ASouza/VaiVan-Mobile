package com.example.trabalhograua.repository

import com.example.trabalhograua.model.Usuario
import com.google.firebase.firestore.FirebaseFirestore

class FirebaseRepository {

    private val db = FirebaseFirestore.getInstance()

    fun salvarUsuario(
        uid: String,
        usuario: Usuario,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {

        db.collection("usuarios")
            .document(uid)
            .set(usuario)
            .addOnSuccessListener {

                onSuccess()

            }
            .addOnFailureListener {

                onError(it)

            }

    }
}