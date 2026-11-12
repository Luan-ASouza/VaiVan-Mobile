package com.example.vaivan.ui.motorista.rotas

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vaivan.data.local.entities.RotaEntity
import com.example.vaivan.data.local.entities.ViagemEntity
import com.example.vaivan.data.repository.RotaRepository
import com.example.vaivan.data.repository.ViagemRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalCoroutinesApi::class)
class MinhasRotasViewModel(
    private val rotaRepository: RotaRepository,
    private val viagemRepository: ViagemRepository
) : ViewModel() {

    private val motoristaId =
        FirebaseAuth
            .getInstance()
            .currentUser
            ?.uid

    init {
        if (motoristaId != null) {
            rotaRepository.iniciarSincronizacaoDasRotasDoMotorista(motoristaId)
        }
    }

    /** Pull-to-refresh: força uma releitura pontual, sem depender só do
     * listener contínuo (que pode não estar ativo nesse momento). */
    suspend fun atualizarRotas() {
        if (motoristaId != null) {
            rotaRepository.sincronizarRotasDoMotoristaUmaVez(motoristaId)
        }
        atualizarViagemAtiva()
    }

    val rotas: StateFlow<List<RotaEntity>> =
        if (motoristaId != null) {

            rotaRepository
                .observarRotasPorMotorista(
                    motoristaId
                )
                .stateIn(
                    scope = viewModelScope,
                    started =
                        SharingStarted.WhileSubscribed(
                            5_000
                        ),
                    initialValue =
                        emptyList()
                )

        } else {

            emptyFlow<List<RotaEntity>>()
                .stateIn(
                    scope = viewModelScope,
                    started =
                        SharingStarted.WhileSubscribed(
                            5_000
                        ),
                    initialValue =
                        emptyList()
                )
        }

    // ---------------------------------------------------------
    // VIAGEM EM ANDAMENTO (card "Retomar viagem")
    // ---------------------------------------------------------

    /** Resultado da última consulta ao Firestore: null = ainda não consultou
     * (vale o Room), senão a viagem ativa que o Firestore devolveu (ou
     * ausência dela). */
    private val _consultaRemota = MutableStateFlow<ConsultaViagemAtiva?>(null)

    private data class ConsultaViagemAtiva(val viagem: ViagemEntity?)

    /** Viagem em andamento/pausada do motorista. Firestore é a fonte da
     * verdade (o Room pode estar vazio, ex.: reinstalou o app, ou desatualizado
     * se a viagem foi encerrada em outro lugar); depois de achar a viagem lá,
     * segue o Room dela pra sumir assim que ela for finalizada. */
    val viagemAtiva: StateFlow<ViagemEntity?> = _consultaRemota
        .flatMapLatest { consulta ->
            when {
                motoristaId == null -> flowOf(null)
                consulta == null -> viagemRepository.observarViagemAtivaDoMotorista(motoristaId)
                consulta.viagem == null -> flowOf(null)
                else -> acompanharLocal(consulta.viagem)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun acompanharLocal(remota: ViagemEntity): Flow<ViagemEntity?> =
        viagemRepository.observarViagem(remota.id).map { local ->
            (local ?: remota).takeIf { ehAtiva(it) }
        }

    private fun ehAtiva(viagem: ViagemEntity) =
        viagem.status == ViagemRepository.STATUS_EM_ANDAMENTO ||
            viagem.status == ViagemRepository.STATUS_PAUSADA

    /** Chamada a cada vez que a lista volta pra tela (ex.: o motorista saiu
     * da navegação sem finalizar) — uma leitura só no Firestore. */
    fun atualizarViagemAtiva() {
        val id = motoristaId ?: return
        viewModelScope.launch {
            try {
                _consultaRemota.value =
                    ConsultaViagemAtiva(viagemRepository.consultarViagemAtivaDoMotorista(id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Sem rede: fica no que o Room já sabe.
                Log.w("MinhasRotasVM", "Falha ao consultar viagem ativa", e)
            }
        }
    }
}
