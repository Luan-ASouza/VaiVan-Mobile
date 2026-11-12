package com.example.vaivan.ui.responsavel.rotas

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vaivan.data.local.entities.EventoViagemEntity
import com.example.vaivan.data.local.entities.LocalizacaoViagemEntity
import com.example.vaivan.data.local.entities.ParadaViagemEntity
import com.example.vaivan.data.local.entities.ViagemEntity
import com.example.vaivan.data.repository.LocalizacaoViagemRepository
import com.example.vaivan.data.repository.ViagemRepository
import com.example.vaivan.domain.StatusPassageiro
import com.example.vaivan.domain.statusDoPassageiro
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Estado da tela "Acompanhar Viagem" do responsável: localiza a viagem ativa
 * da rota (se houver) e a partir daí reusa o mesmo [ViagemRepository] que o
 * motorista usa — nenhum acesso a Firestore aqui, só Repository/Room, como
 * no resto do projeto.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AcompanharViagemViewModel(
    private val rotaId: String,
    private val passageiroId: String,
    private val responsavelId: String,
    private val viagemRepository: ViagemRepository,
    private val localizacaoViagemRepository: LocalizacaoViagemRepository
) : ViewModel() {

    private val _viagemId = MutableStateFlow<String?>(null)

    val viagem: StateFlow<ViagemEntity?> = _viagemId
        .filterNotNull()
        .flatMapLatest { viagemRepository.observarViagem(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Todos os passageiros da viagem — usado só pra desenhar as outras
     * paradas da rota de forma anônima no mapa do responsável (nenhum nome
     * ou foto de outros passageiros, só o ponto geográfico). */
    val todasParadas: StateFlow<List<ParadaViagemEntity>> = _viagemId
        .filterNotNull()
        .flatMapLatest { viagemRepository.observarParadas(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val paradasDoFilho: StateFlow<List<ParadaViagemEntity>> = todasParadas
        .map { paradas -> paradas.filter { it.passageiroId == passageiroId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val statusAtual: StateFlow<StatusPassageiro?> = paradasDoFilho
        .map { grupo ->
            val embarque = grupo.firstOrNull { it.tipo == ViagemRepository.TIPO_EMBARQUE }
            val desembarque = grupo.firstOrNull { it.tipo == ViagemRepository.TIPO_DESEMBARQUE }
            if (embarque != null && desembarque != null) statusDoPassageiro(embarque, desembarque) else null
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val localizacaoMotorista: StateFlow<LocalizacaoViagemEntity?> = _viagemId
        .filterNotNull()
        .flatMapLatest { localizacaoViagemRepository.observarLocalizacao(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Trilha de eventos da viagem (gancho pra notificação — ver
     * AcompanharViagemActivity.atualizarNotificacoes). */
    val eventos: StateFlow<List<EventoViagemEntity>> = _viagemId
        .filterNotNull()
        .flatMapLatest { viagemRepository.observarEventos(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val viagemAtiva = try {
                viagemRepository.consultarViagemAtivaParaRota(rotaId, responsavelId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("AcompanharViagemVM", "Falha ao buscar a viagem ativa da rota $rotaId", e)
                null
            }

            viagemAtiva?.let {
                viagemRepository.iniciarSincronizacao(it.id, responsavelId)
                localizacaoViagemRepository.iniciarSincronizacao(it.id)
                _viagemId.value = it.id
            }
        }
    }

    override fun onCleared() {
        _viagemId.value?.let { viagemRepository.pararSincronizacao(it) }
        localizacaoViagemRepository.pararSincronizacao()
        super.onCleared()
    }
}
