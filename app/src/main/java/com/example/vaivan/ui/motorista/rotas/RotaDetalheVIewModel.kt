package com.example.vaivan.ui.motorista.rotas

import androidx.lifecycle.ViewModel
import com.example.vaivan.data.local.entities.RotaEntity
import com.example.vaivan.data.local.entities.SolicitacaoInclusaoEntity
import com.example.vaivan.data.local.entities.ViagemEntity
import com.example.vaivan.data.repository.RotaRepository
import com.example.vaivan.data.repository.SolicitacaoInclusaoRepository
import com.example.vaivan.domain.usecase.solicitacao.AceitarSolicitacaoUseCase
import com.example.vaivan.domain.usecase.viagem.IniciarViagemUseCase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.Flow

class RotaDetalheViewModel(
    private val rotaRepository: RotaRepository,
    private val solicitacaoRepository: SolicitacaoInclusaoRepository,
    private val aceitarSolicitacaoUseCase: AceitarSolicitacaoUseCase,
    private val iniciarViagemUseCase: IniciarViagemUseCase
) : ViewModel() {

    // Traz do Firestore para o Room do motorista as solicitações recebidas.
    private var listenerSolicitacoes: ListenerRegistration? = null

    init {
        val motoristaId = FirebaseAuth.getInstance().currentUser?.uid
        if (motoristaId != null) {
            listenerSolicitacoes =
                solicitacaoRepository.iniciarSincronizacaoDoMotorista(motoristaId)
        }
    }

    override fun onCleared() {
        listenerSolicitacoes?.remove()
        listenerSolicitacoes = null
        super.onCleared()
    }

    fun observarRota(
        rotaId: String
    ): Flow<RotaEntity?> {

        return rotaRepository.observarRotaPorId(
            rotaId
        )
    }

    fun observarParadas(
        rotaId: String
    ) =
        rotaRepository.observarParadas(
            rotaId
        )

    fun observarSolicitacoes(
        motoristaId: String
    ) =
        solicitacaoRepository.observarPendentesPorMotorista(
            motoristaId
        )

    suspend fun aceitarSolicitacao(
        solicitacao: SolicitacaoInclusaoEntity
    ) {

        aceitarSolicitacaoUseCase(
            solicitacao
        )
    }

    suspend fun recusarSolicitacao(
        solicitacao: SolicitacaoInclusaoEntity
    ) {

        solicitacaoRepository.marcarComoRecusada(
            solicitacao
        )
    }

    suspend fun iniciarViagem(
        rotaId: String,
        motoristaId: String,
        direcao: String
    ): ViagemEntity {

        return iniciarViagemUseCase(
            rotaId = rotaId,
            motoristaId = motoristaId,
            direcao = direcao
        )
    }
}