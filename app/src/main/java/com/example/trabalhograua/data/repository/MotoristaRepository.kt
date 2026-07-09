package com.example.trabalhograua.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.example.trabalhograua.data.local.dao.MotoristaDao
import com.example.trabalhograua.data.local.entities.MotoristaEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * MODELO de Repository: Firestore = fonte da verdade, Room = cache local.
 *
 * Padrão usado:
 * 1. A UI observa SEMPRE o Room (getAll / getById) -> rápido, funciona offline.
 * 2. Em paralelo, um listener do Firestore mantém o Room atualizado sempre
 *    que algo mudar no servidor (ou você pode fazer um fetch único, sem
 *    listener, se preferir economizar leituras).
 * 3. Escritas (criar/editar) vão direto pro Firestore; quando o Firestore
 *    confirma, você atualiza o Room também (ou deixa o listener fazer isso).
 *
 * Repita esse mesmo padrão para as outras entidades (Responsavel, Passageiro,
 * Rota, Viagem, etc.) trocando o nome da coleção e o DAO.
 */
class MotoristaRepository(
    private val motoristaDao: MotoristaDao,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val collection = firestore.collection("motoristas")
    private var listenerRegistration: ListenerRegistration? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    /** UI observa isso. Vem sempre do Room -> instantâneo e funciona offline. */
    fun observarMotoristas(): Flow<List<MotoristaEntity>> = motoristaDao.getAll()

    fun observarMotorista(id: String): Flow<MotoristaEntity?> = motoristaDao.getById(id)

    /**
     * Liga um listener em tempo real do Firestore -> Room.
     * Chame uma vez (ex: no início da sessão do app / login) e pare com
     * pararSincronizacao() quando não precisar mais (ex: logout).
     */
    fun iniciarSincronizacao() {
        listenerRegistration?.remove()
        listenerRegistration = collection.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener

            scope.launch {
                val motoristas = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(MotoristaEntity::class.java)?.copy(
                        id = doc.id,
                        lastUpdated = System.currentTimeMillis()
                    )
                }
                motoristaDao.upsertAll(motoristas)
                // Se quiser remover localmente quem foi excluído no servidor,
                // compare snapshot.documents.map { it.id } com o que já está
                // no Room e chame motoristaDao.deleteById(idsQueSumiram).
            }
        }
    }

    fun pararSincronizacao() {
        listenerRegistration?.remove()
        listenerRegistration = null
    }

    /**
     * Alternativa mais simples (sem listener em tempo real): busca uma vez
     * e grava no cache. Use isso se quiser controlar manualmente quando
     * "gastar" leituras do Firestore, em vez de manter um listener aberto.
     */
    suspend fun sincronizarUmaVez() {
        val snapshot = collection.get().await()
        val motoristas = snapshot.documents.mapNotNull { doc ->
            doc.toObject(MotoristaEntity::class.java)?.copy(
                id = doc.id,
                lastUpdated = System.currentTimeMillis()
            )
        }
        motoristaDao.upsertAll(motoristas)
    }

    /** Escrita: vai pro Firestore, e ao confirmar, atualiza o cache local também. */
    suspend fun salvarMotorista(motorista: MotoristaEntity) {
        val docRef = if (motorista.id.isBlank()) {
            collection.document() // gera um novo id
        } else {
            collection.document(motorista.id)
        }
        val comId = motorista.copy(id = docRef.id, lastUpdated = System.currentTimeMillis())
        docRef.set(comId).await()
        motoristaDao.upsert(comId)
    }

    suspend fun excluirMotorista(id: String) {
        collection.document(id).delete().await()
        motoristaDao.deleteById(id)
    }
}
