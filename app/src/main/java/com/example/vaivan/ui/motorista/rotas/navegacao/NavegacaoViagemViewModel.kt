package com.example.vaivan.ui.motorista.rotas.navegacao

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vaivan.data.local.entities.ParadaViagemEntity
import com.example.vaivan.data.local.entities.ViagemEntity
import com.example.vaivan.data.repository.ViagemRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Estado da tela de navegação: a Activity só desenha o que vier daqui e só
 * chama os métodos abaixo; o acesso ao Firebase fica inteiramente no
 * ViagemRepository, como no resto do projeto.
 *
 * O painel de embarque/desembarque é representado pela ORDEM da parada ativa
 * ([ordemPainelAtivo]), nunca por uma cópia congelada da lista de passageiros
 * — [paradasDoPainel] é sempre recalculado a partir do estado atual de
 * [paradas], então nunca mostra um botão "disponível" para algo que já foi
 * confirmado.
 */
class NavegacaoViagemViewModel(
    private val viagemId: String,
    private val viagemRepository: ViagemRepository
) : ViewModel() {

    val viagem: StateFlow<ViagemEntity?> =
        viagemRepository.observarViagem(viagemId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null
            )

    val paradas: StateFlow<List<ParadaViagemEntity>> =
        viagemRepository.observarParadas(viagemId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    private val _ordemPainelAtivo = MutableStateFlow<Int?>(null)

    /** Null = painel recolhido/sem parada ativa. */
    val ordemPainelAtivo: StateFlow<Int?> = _ordemPainelAtivo.asStateFlow()

    val paradasDoPainel: StateFlow<List<ParadaViagemEntity>> =
        combine(paradas, ordemPainelAtivo) { todas, ordem ->
            if (ordem == null) emptyList() else todas.filter { it.ordem == ordem }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** True quando a parada ativa no painel é a última da viagem — nesse caso
     * a tela mostra um card único de "chegada ao destino final", sem pedir
     * confirmação de desembarque aluno por aluno. */
    val ehParadaFinal: StateFlow<Boolean> =
        combine(paradas, ordemPainelAtivo) { todas, ordem ->
            ordem != null && ordem == todas.maxOfOrNull { it.ordem }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    val proximaParadaPendente: StateFlow<ParadaViagemEntity?> =
        combine(paradas, ordemPainelAtivo) { todas, _ ->
            todas
                .filter {
                    it.status == ViagemRepository.PARADA_AGUARDANDO ||
                        it.status == ViagemRepository.PARADA_AGUARDANDO_DESEMBARQUE
                }
                .minByOrNull { it.ordem }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    init {
        viagemRepository.iniciarSincronizacao(viagemId)
    }

    override fun onCleared() {
        viagemRepository.pararSincronizacao(viagemId)
        super.onCleared()
    }

    /** Chamado pelo ArrivalListener do Navigation SDK. Ignora se aquela
     * parada já estiver toda resolvida (evita reabrir o painel à toa se o
     * SDK disparar a chegada de novo com o veículo parado no lugar). */
    fun exibirPainelParaOrdem(ordem: Int) {
        val grupo = paradas.value.filter { it.ordem == ordem }
        val aindaPendente = grupo.any {
            it.status == ViagemRepository.PARADA_AGUARDANDO ||
                it.status == ViagemRepository.PARADA_AGUARDANDO_DESEMBARQUE
        }
        Log.d(TAG_DEBUG, "exibirPainelParaOrdem: ordem=$ordem grupo=${grupo.size} aindaPendente=$aindaPendente")
        if (aindaPendente) {
            _ordemPainelAtivo.value = ordem
        }
    }

    fun ocultarPainel() {
        _ordemPainelAtivo.value = null
    }

    /** Registra o evento de chegada (gancho pra notificação do responsável —
     * ver AcompanharViagemActivity.atualizarNotificacoes) exatamente no
     * mesmo trigger que abre o painel de confirmar embarque/desembarque
     * pro motorista — só pras paradas do grupo que ainda estiverem
     * pendentes (uma já resolvida, ex. falta avisada, não gera evento). */
    fun registrarChegadaParaOrdem(ordem: Int) = viewModelScope.launch {
        paradas.value
            .filter { it.ordem == ordem &&
                (it.status == ViagemRepository.PARADA_AGUARDANDO ||
                    it.status == ViagemRepository.PARADA_AGUARDANDO_DESEMBARQUE) }
            .forEach { viagemRepository.registrarChegada(it.viagemId, it.id) }
    }

    fun confirmarEmbarque(parada: ParadaViagemEntity) = viewModelScope.launch {
        Log.d(TAG_DEBUG, "confirmarEmbarque: ${parada.nomePassageiro} (ordem=${parada.ordem})")
        viagemRepository.confirmarEmbarque(parada)
        verificarGrupoResolvidoEAvancar(parada.copy(status = ViagemRepository.PARADA_EMBARCADO))
    }

    fun marcarNaoEmbarcou(parada: ParadaViagemEntity) = viewModelScope.launch {
        Log.d(TAG_DEBUG, "marcarNaoEmbarcou: ${parada.nomePassageiro} (ordem=${parada.ordem})")
        viagemRepository.marcarNaoEmbarcou(parada, motivo = null)
        verificarGrupoResolvidoEAvancar(parada.copy(status = ViagemRepository.PARADA_NAO_EMBARCOU))
    }

    fun confirmarDesembarque(parada: ParadaViagemEntity) = viewModelScope.launch {
        Log.d(TAG_DEBUG, "confirmarDesembarque: ${parada.nomePassageiro} (ordem=${parada.ordem})")
        viagemRepository.confirmarDesembarque(parada)
        verificarGrupoResolvidoEAvancar(parada.copy(status = ViagemRepository.PARADA_DESEMBARCADO))
    }

    fun alternarPausa() = viewModelScope.launch {
        if (viagem.value?.status == ViagemRepository.STATUS_PAUSADA) {
            viagemRepository.retomarViagem(viagemId)
        } else {
            viagemRepository.pausarViagem(viagemId)
        }
    }

    fun finalizarViagem() = viewModelScope.launch {
        viagemRepository.finalizarViagem(viagemId)
    }

    /** Chamado pelo botão único "Finalizar viagem" do card da parada final —
     * confirma o desembarque de todo mundo que ainda estiver pendente naquele
     * grupo de uma vez, sem pedir aluno por aluno, e encerra a viagem. */
    fun finalizarParadaFinal() = viewModelScope.launch {
        val grupo = paradasDoPainel.value
        Log.d(TAG_DEBUG, "finalizarParadaFinal: grupo=${grupo.size}")

        val pendentes = grupo.filter {
            it.status == ViagemRepository.PARADA_AGUARDANDO ||
                it.status == ViagemRepository.PARADA_AGUARDANDO_DESEMBARQUE
        }

        if (pendentes.isNotEmpty()) {
            viagemRepository.confirmarDesembarqueColetivo(pendentes)
        }

        ocultarPainel()
        viagemRepository.finalizarViagem(viagemId)
    }

    /** Recebe a parada já com o status novo (não confia no StateFlow [paradas]
     * pra ela: o Room pode levar um instante pra re-emitir depois do upsert,
     * e checar o valor "ao vivo" bem na hora certo depois de escrever pode
     * ler um valor desatualizado e achar que ainda está tudo pendente). */
    private fun verificarGrupoResolvidoEAvancar(paradaAtualizada: ParadaViagemEntity) {
        val ordem = paradaAtualizada.ordem

        val grupo = paradas.value
            .filter { it.ordem == ordem }
            .map { if (it.id == paradaAtualizada.id) paradaAtualizada else it }

        val todosResolvidos = grupo.isNotEmpty() && grupo.all {
            it.status != ViagemRepository.PARADA_AGUARDANDO &&
                it.status != ViagemRepository.PARADA_AGUARDANDO_DESEMBARQUE
        }

        Log.d(
            TAG_DEBUG,
            "verificarGrupoResolvidoEAvancar: ordem=$ordem grupo=${grupo.size} todosResolvidos=$todosResolvidos"
        )

        if (!todosResolvidos) return

        ocultarPainel()

        viewModelScope.launch {
            val proxima = paradas.value.filter { it.ordem > ordem }.minByOrNull { it.ordem }
            Log.d(TAG_DEBUG, "verificarGrupoResolvidoEAvancar: avançando para ordem=${proxima?.ordem}")
            viagemRepository.avancarParaProximaParada(viagemId, proxima?.id)

            if (proxima == null) {
                Log.d(TAG_DEBUG, "verificarGrupoResolvidoEAvancar: sem próxima parada, finalizando viagem.")
                viagemRepository.finalizarViagem(viagemId)
            }
        }
    }

    private companion object {
        const val TAG_DEBUG = "NavegacaoViagemDebug"
    }
}
