package com.example.vaivan.ui.motorista.rotas

import androidx.lifecycle.ViewModel
import com.example.vaivan.data.local.entities.AvisoAusenciaEntity
import com.example.vaivan.data.local.entities.RotaEntity
import com.example.vaivan.data.local.entities.SolicitacaoInclusaoEntity
import com.example.vaivan.data.local.entities.ViagemEntity
import com.example.vaivan.data.repository.AvisoAusenciaRepository
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
    private val iniciarViagemUseCase: IniciarViagemUseCase,
    private val avisoAusenciaRepository: AvisoAusenciaRepository
) : ViewModel() {

    // Traz do Firestore para o Room do motorista as solicitações recebidas
    // e os avisos de ausência registrados pelos responsáveis.
    private var listenerSolicitacoes: ListenerRegistration? = null
    private var listenerAvisosAusencia: ListenerRegistration? = null

    init {
        val motoristaId = FirebaseAuth.getInstance().currentUser?.uid
        if (motoristaId != null) {
            listenerSolicitacoes =
                solicitacaoRepository.iniciarSincronizacaoDoMotorista(motoristaId)
            listenerAvisosAusencia =
                avisoAusenciaRepository.iniciarSincronizacaoDoMotorista(motoristaId)
        }
    }

    override fun onCleared() {
        listenerSolicitacoes?.remove()
        listenerSolicitacoes = null
        listenerAvisosAusencia?.remove()
        listenerAvisosAusencia = null
        super.onCleared()
    }

    fun observarAvisosAusencia(motoristaId: String): Flow<List<AvisoAusenciaEntity>> =
        avisoAusenciaRepository.observarPorMotorista(motoristaId)

    /** Pull-to-refresh: força uma releitura direta do Firestore (rota +
     * paradas) — as solicitações e avisos de ausência já chegam em tempo
     * real pelos listeners iniciados no init, não precisam de ação aqui. */
    suspend fun atualizarTudo(rotaId: String, motoristaId: String) {
        rotaRepository.sincronizarRotasDoMotoristaUmaVez(motoristaId)
        rotaRepository.sincronizarParadasUmaVez(rotaId)
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