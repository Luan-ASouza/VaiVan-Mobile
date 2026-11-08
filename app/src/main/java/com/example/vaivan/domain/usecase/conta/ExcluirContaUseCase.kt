package com.example.vaivan.domain.usecase.conta

import com.example.vaivan.data.repository.AssinaturaRepository
import com.example.vaivan.data.repository.DocumentoRepository
import com.example.vaivan.data.repository.PassageiroRepository
import com.example.vaivan.data.repository.RotaRepository
import com.example.vaivan.data.repository.UsuarioRepository
import com.example.vaivan.data.repository.VeiculoRepository

/**
 * Orquestra a exclusão de conta — é o único caso que cruza vários
 * repositories (por isso vira use case em vez de ficar em um só). Cada
 * exclusão individual (rota/veículo/passageiro) já cascateia pros seus
 * próprios vínculos (ver RotaRepository.excluirRota, VeiculoRepository.excluirVeiculo,
 * PassageiroRepository.excluirPassageiro); aqui só entra a ordem certa
 * entre repositories — filho antes do pai — pra uma exclusão de veículo
 * nunca ser bloqueada por uma rota que já devia ter sido apagada antes.
 *
 * Nunca toca em ViagemEntity/ParadaViagemEntity/EventoViagemEntity
 * (histórico, protegido por regra no Firestore e preservado de propósito).
 * A assinatura do motorista também não é apagada: é ENCERRADA (cancelada),
 * e o histórico de cobranças é mantido para registro.
 *
 * Só cuida dos dados (Firestore + Room + fotos no Storage). A exclusão da
 * conta em si no FirebaseAuth é responsabilidade de quem chama, depois que
 * isto terminar com sucesso — apagar a conta antes derrubaria a autenticação
 * no meio da limpeza e os deletes no Firestore passariam a falhar.
 */
class ExcluirContaUseCase(
    private val usuarioRepository: UsuarioRepository,
    private val rotaRepository: RotaRepository,
    private val veiculoRepository: VeiculoRepository,
    private val documentoRepository: DocumentoRepository,
    private val passageiroRepository: PassageiroRepository,
    private val assinaturaRepository: AssinaturaRepository = AssinaturaRepository()
) {

    suspend fun excluirContaMotorista(motoristaId: String) {
        rotaRepository.consultarRotasDoMotorista(motoristaId).forEach { rota ->
            rotaRepository.excluirRota(rota.id)
        }

        // Nesse ponto nenhuma rota mais referencia os veículos deste
        // motorista (todas foram apagadas acima), então o bloqueio de
        // VeiculoRepository.excluirVeiculo nunca dispara aqui.
        veiculoRepository.consultarVeiculosDoMotorista(motoristaId).forEach { veiculo ->
            veiculoRepository.excluirVeiculo(veiculo.id)
        }

        // Documentos que não são de nenhum veículo específico (CNH,
        // comprovante, selfie) — os de veículo já foram removidos junto do
        // excluirVeiculo acima.
        documentoRepository.consultarPorMotorista(motoristaId).forEach { documento ->
            documentoRepository.excluir(documento.id)
        }

        // Encerra a assinatura (sem apagar o histórico de cobranças) antes de
        // remover o perfil, para não deixar cobrança futura de uma conta que não existe mais.
        assinaturaRepository.encerrarPorExclusaoDeConta(motoristaId)

        usuarioRepository.excluirUsuario(motoristaId)
    }

    suspend fun excluirContaResponsavel(responsavelId: String) {
        passageiroRepository.consultarPassageirosDoResponsavel(responsavelId).forEach { passageiro ->
            passageiroRepository.excluirPassageiro(passageiro.id)
        }

        usuarioRepository.excluirUsuario(responsavelId)
    }
}