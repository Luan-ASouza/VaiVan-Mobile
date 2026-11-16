package com.example.vaivan.ui.motorista.rotas

import androidx.lifecycle.ViewModel
import com.example.vaivan.data.local.entities.AvisoAusenciaEntity
import com.example.vaivan.data.local.entities.RotaEntity
import com.example.vaivan.data.local.entities.SolicitacaoInclusaoEntity
import com.example.vaivan.data.local.entities.ViagemEntity
import com.example.vaivan.data.repository.AvisoAusenciaRepository
import com.example.vaivan.data.repository.RotaRepository
import com.example.vaivan.data.repository.SolicitacaoInclusaoRepository
import com.example.vaivan.domain.usecase.rota.AtualizarTrajetoDaRotaUseCase
import com.example.vaivan.domain.usecase.solicitacao.AceitarSolicitacaoUseCase
import com.example.vaivan.domain.usecase.solicitacao.ConfirmarReservasPagasUseCase
import com.example.vaivan.domain.usecase.viagem.IniciarViagemUseCase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.Flow

class RotaDetalheViewModel(
    private val rotaRepository: RotaRepository,
    private val solicitacaoRepository: SolicitacaoInclusaoRepository,
    private val aceitarSolicitacaoUseCase: AceitarSolicitacaoUseCase,
    private val iniciarViagemUseCase: IniciarViagemUseCase,
    private val avisoAusenciaRepository: AvisoAusenciaRepository,
    private val confirmarReservasPagasUseCase: ConfirmarReservasPagasUseCase,
    private val atualizarTrajetoUseCase: AtualizarTrajetoDaRotaUseCase
) : ViewModel() {

    /** Botão "Recalcular trajeto" da aba Percurso. */
    suspend fun recalcularTrajeto(rotaId: String): Boolean = atualizarTrajetoUseCase(rotaId)

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
        processarReservas(motoristaId)
        rotaRepository.sincronizarRotasDoMotoristaUmaVez(motoristaId)
        rotaRepository.sincronizarParadasUmaVez(rotaId)
    }

    /** Traz as paradas (alunos confirmados) do Firestore para o Room. */
    suspend fun sincronizarParadas(rotaId: String) {
        rotaRepository.sincronizarParadasUmaVez(rotaId)
    }

    suspend fun excluirRota(rotaId: String) {
        rotaRepository.excluirRota(rotaId)
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

    /**
     * Coloca no trajeto quem já pagou a 1ª mensalidade e expira reservas vencidas.
     * Também recalcula o trajeto das rotas que perderam um aluno (cancelamento
     * do responsável). Roda ao abrir a tela, no puxar pra atualizar e antes
     * de iniciar viagem.
     * Devolve quantos alunos entraram na rota. Nunca falha (erro = tenta na próxima vez).
     */
    suspend fun processarReservas(motoristaId: String): Int {
        val ativados = try {
            confirmarReservasPagasUseCase(motoristaId)
        } catch (e: Exception) {
            0
        }
        atualizarTrajetoUseCase.atualizarPendentes(motoristaId)
        return ativados
    }

    suspend fun iniciarViagem(
        rotaId: String,
        motoristaId: String,
        direcao: String
    ): ViagemEntity {

        // Garante que quem já pagou a 1ª mensalidade entre nesta viagem
        processarReservas(motoristaId)

        return iniciarViagemUseCase(
            rotaId = rotaId,
            motoristaId = motoristaId,
            direcao = direcao
        )
    }
}