package com.example.vaivan.data.repository

import com.example.vaivan.data.local.dao.AvisoAusenciaDao
import com.example.vaivan.data.local.entities.AvisoAusenciaEntity
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Aviso de ausência que o responsável registra com antecedência (filho não
 * vai usar o transporte). Mesmo padrão de SolicitacaoInclusaoRepository:
 * Firestore é a fonte da verdade, mirrorado pra Room dos dois lados
 * (responsável cria/cancela; motorista só lê; IniciarViagemUseCase consome
 * ao montar a próxima viagem).
 */
class AvisoAusenciaRepository(
    private val dao: AvisoAusenciaDao,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val collection = firestore.collection("avisos_ausencia")
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        const val TIPO_DIA_INTEIRO = "DIA_INTEIRO"
        const val TIPO_SOMENTE_IDA = "SOMENTE_IDA"
        const val TIPO_SOMENTE_VOLTA = "SOMENTE_VOLTA"
    }

    fun observarAvisoAtivo(rotaId: String, passageiroId: String): Flow<AvisoAusenciaEntity?> =
        dao.getAtivo(rotaId, passageiroId)

    fun observarPorResponsavel(responsavelId: String): Flow<List<AvisoAusenciaEntity>> =
        dao.getPorResponsavel(responsavelId)

    fun observarPorMotorista(motoristaId: String): Flow<List<AvisoAusenciaEntity>> =
        dao.getPorMotorista(motoristaId)

    private fun mirrorarMudancas(query: Query): ListenerRegistration =
        query.addSnapshotListener { snapshot, erro ->
            if (erro != null || snapshot == null) return@addSnapshotListener

            scope.launch {
                for (mudanca in snapshot.documentChanges) {
                    val documento = mudanca.document
                    when (mudanca.type) {
                        DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                            documento.toObject(AvisoAusenciaEntity::class.java)
                                ?.copy(id = documento.id)
                                ?.let { dao.upsert(it) }
                        }
                        DocumentChange.Type.REMOVED -> dao.deleteById(documento.id)
                    }
                }
            }
        }

    /** Mantém o Room do responsável atualizado — ex.: quando o motorista
     * consome/finaliza o aviso ao iniciar a viagem. */
    fun iniciarSincronizacaoDoResponsavel(responsavelId: String): ListenerRegistration =
        mirrorarMudancas(collection.whereEqualTo("responsavelId", responsavelId))

    /** Mantém o Room do motorista atualizado com os avisos das suas rotas. */
    fun iniciarSincronizacaoDoMotorista(motoristaId: String): ListenerRegistration =
        mirrorarMudancas(collection.whereEqualTo("motoristaId", motoristaId))

    suspend fun salvarAviso(aviso: AvisoAusenciaEntity): AvisoAusenciaEntity = withContext(Dispatchers.IO) {
        val docRef = collection.document()
        val comId = aviso.copy(
            id = docRef.id,
            criadoEm = System.currentTimeMillis(),
            lastUpdated = System.currentTimeMillis()
        )
        docRef.set(comId).await()
        dao.upsert(comId)
        comId
    }

    suspend fun excluirAviso(aviso: AvisoAusenciaEntity) = withContext(Dispatchers.IO) {
        collection.document(aviso.id).delete().await()
        dao.deleteById(aviso.id)
    }

    /** Lido direto do Firestore (não confia no cache) no momento de iniciar
     * uma viagem — mesmo motivo de IniciarViagemUseCase já usar
     * consultarRota/consultarPassageiro. */
    suspend fun consultarAvisosAtivos(rotaId: String): List<AvisoAusenciaEntity> =
        withContext(Dispatchers.IO) {
            collection.whereEqualTo("rotaId", rotaId).get().await().documents.mapNotNull { documento ->
                documento.toObject(AvisoAusenciaEntity::class.java)?.copy(id = documento.id)
            }
        }

    /** Zera o lado consumido pela direção que acabou de virar uma viagem; se
     * os dois lados já estiverem zerados, o aviso cumpriu seu papel e é
     * apagado em vez de só atualizado. */
    suspend fun marcarConsumido(aviso: AvisoAusenciaEntity, direcao: String) = withContext(Dispatchers.IO) {
        val pendenteIda = if (direcao == ViagemRepository.DIRECAO_IDA) false else aviso.pendenteIda
        val pendenteVolta = if (direcao == ViagemRepository.DIRECAO_VOLTA) false else aviso.pendenteVolta

        if (!pendenteIda && !pendenteVolta) {
            excluirAviso(aviso)
        } else {
            val atualizado = aviso.copy(
                pendenteIda = pendenteIda,
                pendenteVolta = pendenteVolta,
                lastUpdated = System.currentTimeMillis()
            )
            collection.document(aviso.id).set(atualizado).await()
            dao.upsert(atualizado)
        }
    }
}
