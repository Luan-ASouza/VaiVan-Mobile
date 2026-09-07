package com.example.trabalhograua.chat

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

object ChatRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun gerarChatId(uid1: String, uid2: String): String {
        return if (uid1 < uid2) "${uid1}_$uid2" else "${uid2}_$uid1"
    }

    suspend fun iniciarOuObterChat(outroUsuarioId: String): String {
        val meuUid = auth.currentUser?.uid
            ?: throw IllegalStateException("Usuário não está logado")

        val chatId = gerarChatId(meuUid, outroUsuarioId)
        val chatRef = db.collection("chats").document(chatId)

        val snapshot = chatRef.get().await()

        if (!snapshot.exists()) {
            val novoChat = hashMapOf(
                "participantes" to listOf(meuUid, outroUsuarioId),
                "criadoEm" to System.currentTimeMillis(),
                "ultimaMensagem" to "",
                "ultimaMensagemEm" to System.currentTimeMillis(),
                "ultimaMensagemPor" to ""
            )
            chatRef.set(novoChat).await()
        }

        return chatId
    }

    suspend fun enviarMensagem(chatId: String, texto: String) {
        val meuUid = auth.currentUser?.uid
            ?: throw IllegalStateException("Usuário não está logado")

        val mensagem = hashMapOf(
            "remetenteId" to meuUid,
            "texto" to texto,
            "enviadoEm" to System.currentTimeMillis(),
            "lida" to false
        )

        db.collection("chats").document(chatId)
            .collection("mensagens")
            .add(mensagem)
            .await()

        db.collection("chats").document(chatId)
            .update(
                mapOf(
                    "ultimaMensagem" to texto,
                    "ultimaMensagemEm" to System.currentTimeMillis(),
                    "ultimaMensagemPor" to meuUid
                )
            )
            .await()
    }

    fun escutarMensagens(
        chatId: String,
        aoAtualizar: (List<Mensagem>) -> Unit
    ): ListenerRegistration {
        return db.collection("chats").document(chatId)
            .collection("mensagens")
            .orderBy("enviadoEm", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, erro ->
                if (erro != null || snapshot == null) return@addSnapshotListener

                val mensagens = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Mensagem::class.java)?.copy(id = doc.id)
                }
                aoAtualizar(mensagens)
            }
    }
}