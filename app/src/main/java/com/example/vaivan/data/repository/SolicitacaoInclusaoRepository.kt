package com.example.vaivan.data.repository

import android.content.Context
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.dao.SolicitacaoInclusaoDao
import com.example.vaivan.data.local.entities.SolicitacaoInclusaoEntity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class SolicitacaoInclusaoRepository(
    private val context: Context,
    private val dao: SolicitacaoInclusaoDao,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val collection = firestore.collection("solicitacoes_inclusao")
    private val collectionPassageiros = firestore.collection("passageiros")
    private val scope = CoroutineScope(Dispatchers.IO)

    private val rotaRepository by lazy {
        RotaRepository(
            context,
            VaivanDatabase.getInstance(context).rotaDao(),
            VaivanDatabase.getInstance(context).paradaRotaDao()
        )
    }

    fun observarPendentesPorMotorista(motoristaId: String): Flow<List<SolicitacaoInclusaoEntity>> =
        dao.getPendentesPorMotorista(motoristaId)

    fun observarPorResponsavel(responsavelId: String): Flow<List<SolicitacaoInclusaoEntity>> =
        dao.getPorResponsavel(responsavelId)

    suspend fun enviarSolicitacao(item: SolicitacaoInclusaoEntity) = withContext(Dispatchers.IO) {
        val docRef = collection.document()
        val comId = item.copy(id = docRef.id, criadoEm = System.currentTimeMillis(), status = "PENDENTE")
        docRef.set(comId).await()
        dao.upsert(comId)
    }

    suspend fun aceitar(solicitacao: SolicitacaoInclusaoEntity) = withContext(Dispatchers.IO) {

        collectionPassageiros.document(solicitacao.passageiroId)
            .update("motoristaId", solicitacao.motoristaId)
            .await()

        rotaRepository.recalcularComNovaParada(
            rotaId = solicitacao.rotaId,
            novaParada = ParadaCandidata(
                passageiroId = solicitacao.passageiroId,
                nomePassageiro = solicitacao.nomePassageiro,
                localId = solicitacao.localEmbarqueId,
                nomeLocal = solicitacao.nomeLocalEmbarque,
                endereco = solicitacao.enderecoEmbarque,
                latitude = solicitacao.latitudeEmbarque,
                longitude = solicitacao.longitudeEmbarque
            )
        )

        val atualizada = solicitacao.copy(status = "ACEITA", respondidoEm = System.currentTimeMillis())
        collection.document(solicitacao.id).set(atualizada).await()
        dao.upsert(atualizada)
    }

    suspend fun recusar(solicitacao: SolicitacaoInclusaoEntity) = withContext(Dispatchers.IO) {
        val atualizada = solicitacao.copy(status = "RECUSADA", respondidoEm = System.currentTimeMillis())
        collection.document(solicitacao.id).set(atualizada).await()
        dao.upsert(atualizada)
    }

    fun aceitarAsync(solicitacao: SolicitacaoInclusaoEntity, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        scope.launch {
            try {
                aceitar(solicitacao)
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e) }
            }
        }
    }

    fun recusarAsync(solicitacao: SolicitacaoInclusaoEntity, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        scope.launch {
            try {
                recusar(solicitacao)
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e) }
            }
        }
    }

    fun enviarSolicitacaoAsync(item: SolicitacaoInclusaoEntity, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        scope.launch {
            try {
                enviarSolicitacao(item)
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e) }
            }
        }
    }
}