package com.example.vaivan.ui.comum.chat

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

object ChatRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /**
     * Identifica de QUAL viagem e de QUAL aluno é a conversa.
     * Cada combinação (rota + aluno) tem a sua própria conversa.
     */
    data class ContextoChat(
        val rotaId: String,
        val passageiroId: String,
        val passageiroNome: String = "",
        val rotaNome: String = ""
    )

    /**
     * Sem contexto: uma conversa por par de usuários (comportamento antigo).
     * Com contexto: uma conversa por par de usuários + rota + aluno.
     */
    private fun gerarChatId(
        uid1: String,
        uid2: String,
        contexto: ContextoChat? = null
    ): String {
        val base = if (uid1 < uid2) "${uid1}_$uid2" else "${uid2}_$uid1"

        return if (contexto == null) {
            base
        } else {
            "${base}_${contexto.rotaId}_${contexto.passageiroId}"
        }
    }

    /**
     * Abre (ou cria) a conversa entre o usuário logado e [outroUsuarioId].
     *
     * Os parâmetros novos são opcionais, então as chamadas antigas continuam funcionando:
     *  - [solicitacaoId]: guarda no chat de qual solicitação aprovada ele nasceu
     *    (usado pela regra do Firestore que limita o chat a quem foi aprovado).
     *  - [mensagemInicial]: o pedido que o responsável escreveu na solicitação.
     *    Vira a primeira mensagem da conversa, enviada pelo usuário logado
     *    (portanto, chame isto pelo app do RESPONSÁVEL).
     *  - [mensagemInicialEm]: horário da mensagem (use o criadoEm da solicitação
     *    para ela ficar no topo da conversa).
     *  - [contexto]: viagem e aluno da conversa. Com ele, cada filho/viagem tem
     *    a sua própria conversa em vez de todas caírem no mesmo chat.
     *
     * A mensagem do pedido tem ID fixo ("pedido_<solicitacaoId>"), então, mesmo que
     * esta função rode várias vezes, ela é gravada uma única vez.
     */
    suspend fun iniciarOuObterChat(
        outroUsuarioId: String,
        solicitacaoId: String? = null,
        mensagemInicial: String? = null,
        mensagemInicialEm: Long? = null,
        contexto: ContextoChat? = null
    ): String {
        val meuUid = auth.currentUser?.uid
            ?: throw IllegalStateException("Usuário não está logado")

        if (meuUid == outroUsuarioId) {
            throw IllegalStateException(
                "Esta conversa seria da sua conta com ela mesma. " +
                        "Para testar o chat, use duas contas diferentes " +
                        "(uma como motorista e outra como responsável)."
            )
        }

        val chatId = gerarChatId(meuUid, outroUsuarioId, contexto)
        val chatRef = db.collection("chats").document(chatId)

        val textoInicial = mensagemInicial?.trim().orEmpty()
        val momentoInicial = mensagemInicialEm ?: System.currentTimeMillis()

        val snapshot = chatRef.get().await()

        if (!snapshot.exists()) {
            val novoChat = hashMapOf<String, Any>(
                "participantes" to listOf(meuUid, outroUsuarioId),
                "criadoEm" to System.currentTimeMillis(),
                "ultimaMensagem" to textoInicial,
                "ultimaMensagemEm" to if (textoInicial.isNotEmpty()) momentoInicial
                else System.currentTimeMillis(),
                "ultimaMensagemPor" to if (textoInicial.isNotEmpty()) meuUid else ""
            )

            if (solicitacaoId != null) {
                novoChat["solicitacaoId"] = solicitacaoId
            }

            if (contexto != null) {
                novoChat["rotaId"] = contexto.rotaId
                novoChat["passageiroId"] = contexto.passageiroId
                novoChat["passageiroNome"] = contexto.passageiroNome
                novoChat["rotaNome"] = contexto.rotaNome
            }

            chatRef.set(novoChat).await()
        }

        // O chat já existe aqui (novo ou antigo): grava o pedido como 1ª mensagem.
        if (solicitacaoId != null && textoInicial.isNotEmpty()) {
            val pedidoRef = chatRef.collection("mensagens")
                .document("pedido_$solicitacaoId")

            if (!pedidoRef.get().await().exists()) {
                pedidoRef.set(
                    hashMapOf(
                        "remetenteId" to meuUid,
                        "texto" to textoInicial,
                        "enviadoEm" to momentoInicial,
                        "lida" to false
                    )
                ).await()
            }
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

    /**
     * [aoErro] é chamado quando o Firestore recusa ou falha a leitura
     * (por exemplo, regra de segurança negando o acesso). Antes o erro era ignorado,
     * e a conversa simplesmente ficava vazia sem ninguém saber o motivo.
     */
    fun escutarMensagens(
        chatId: String,
        aoErro: ((Exception) -> Unit)? = null,
        aoAtualizar: (List<Mensagem>) -> Unit
    ): ListenerRegistration {
        return db.collection("chats").document(chatId)
            .collection("mensagens")
            .orderBy("enviadoEm", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, erro ->

                if (erro != null) {
                    Log.e("ChatRepository", "Erro ao escutar mensagens de $chatId", erro)
                    aoErro?.invoke(erro)
                    return@addSnapshotListener
                }

                if (snapshot == null) return@addSnapshotListener

                val mensagens = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Mensagem::class.java)?.copy(id = doc.id)
                }
                aoAtualizar(mensagens)
            }
    }
}