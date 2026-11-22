package com.example.vaivan.ui.motorista.rotas

import com.example.vaivan.data.local.entities.RotaEntity
import com.example.vaivan.data.repository.RotaRepository
import com.example.vaivan.domain.usecase.rota.AssociarVeiculoARotaUseCase
import com.example.vaivan.domain.usecase.rota.AtualizarTrajetoDaRotaUseCase
import com.example.vaivan.ui.comum.perfil.EdicaoViewModel

/**
 * Edição de um campo da rota (aberta pelas linhas da aba Informações do
 * detalhe). Mesmo modelo das edições do perfil: lê a rota do Firestore,
 * a tela aplica a mudança e grava a rota inteira.
 *
 * O [RotaRepository.atualizarRota] só recalcula o trajeto (Google Routes)
 * se o ponto ou a escola mudaram; os demais campos são só metadados.
 */
class EditarRotaViewModel(
    private val rotaRepository: RotaRepository,
    private val atualizarTrajetoUseCase: AtualizarTrajetoDaRotaUseCase,
    private val associarVeiculoUseCase: AssociarVeiculoARotaUseCase,
    private val rotaId: String
) : EdicaoViewModel<RotaEntity>() {

    override val mensagemNaoEncontrado = "Rota não encontrada."

    override suspend fun buscar(): RotaEntity? = rotaRepository.consultarRota(rotaId)

    override suspend fun gravar(item: RotaEntity) {
        // Lido antes de gravar: aqui item.value ainda é a rota sem a mudança
        val anterior = this.item.value

        // Troca de veículo: só aprovado (o use case valida e grava só esse campo)
        if (anterior != null && anterior.veiculoId.orEmpty() != item.veiculoId.orEmpty()) {
            associarVeiculoUseCase(item.id, item.motoristaId, item.veiculoId)
            return
        }

        val trajetoMudou = anterior != null && (
            anterior.origemLatitude != item.origemLatitude ||
                anterior.origemLongitude != item.origemLongitude ||
                anterior.destinoLatitude != item.destinoLatitude ||
                anterior.destinoLongitude != item.destinoLongitude
            )

        rotaRepository.atualizarRota(
            rotaId = item.id,
            nome = item.nome,
            tipoTrajeto = item.tipoTrajeto,
            origemNome = item.origemNome,
            origemEndereco = item.origemEndereco,
            origemLatitude = item.origemLatitude,
            origemLongitude = item.origemLongitude,
            destinoNome = item.destinoNome,
            destinoEndereco = item.destinoEndereco,
            destinoLatitude = item.destinoLatitude,
            destinoLongitude = item.destinoLongitude,
            turno = item.turno,
            diasSemana = item.diasSemana.split(",").filter { it.isNotBlank() },
            capacidadeTotal = item.capacidadeTotal,
            escolaId = item.escolaId,
            escolaNome = item.escolaNome,
            bairrosAtendidos = item.bairrosAtendidos
        )

        // O atualizarRota traça só ponto → escola; com alunos na rota, o
        // trajeto precisa passar por eles de novo (e a ordem pode mudar).
        if (trajetoMudou && item.vagasOcupadas > 0) {
            atualizarTrajetoUseCase(item.id)
        }
    }
}
