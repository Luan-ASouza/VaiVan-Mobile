package com.example.vaivan.domain.usecase.veiculo

import com.example.vaivan.data.local.entities.VeiculoEntity
import com.example.vaivan.data.models.StatusDocumento
import com.example.vaivan.data.repository.DocumentoRepository
import com.example.vaivan.data.repository.VeiculoRepository

/**
 * Regras do ciclo de validação do veículo (Kotlin puro, testado em VeiculoPolicyTest).
 * As regras do Firestore (match /veiculos e /rotas) espelham estas: se mudar uma, mude a outra.
 *
 *  - PENDENTE   : falta documento, algum foi rejeitado e reenviado só em parte,
 *                 ou um dado crítico mudou e o veículo aguarda nova validação;
 *  - EM_ANALISE : CRLV e autorização enviados, aguardando o painel;
 *  - APROVADO   : o painel aprovou os dois documentos (só o admin grava);
 *  - REJEITADO  : o painel recusou um dos documentos (só o admin grava).
 */
object VeiculoPolicy {

    val DOCUMENTOS_OBRIGATORIOS = listOf(
        DocumentoRepository.TIPO_CRLV,
        DocumentoRepository.TIPO_AUTORIZACAO
    )

    /** Placa no formato gravado: maiúsculas, sem hífen nem espaços. */
    fun normalizarPlaca(placa: String): String =
        placa.uppercase().replace("-", "").replace(" ", "").trim()

    /** Dados que constam no CRLV/autorização: mudar qualquer um invalida a aprovação. */
    fun alterouDadosCriticos(antes: VeiculoEntity, depois: VeiculoEntity): Boolean =
        normalizarPlaca(antes.placa) != normalizarPlaca(depois.placa) ||
            !antes.marca.equals(depois.marca, ignoreCase = true) ||
            !antes.modelo.equals(depois.modelo, ignoreCase = true) ||
            !antes.cor.equals(depois.cor, ignoreCase = true) ||
            antes.anoFabricacao != depois.anoFabricacao ||
            antes.capacidadePassageiros != depois.capacidadePassageiros

    /**
     * Status que o APP grava depois de um envio de documento (tipo -> status).
     * Retorna null quando o app não deve mexer: os dois aprovados é decisão do painel.
     */
    fun statusAposEnvio(documentos: Map<String, StatusDocumento>): String? {
        val status = DOCUMENTOS_OBRIGATORIOS.map { documentos[it] ?: StatusDocumento.NAO_ENVIADO }
        return when {
            status.all { it == StatusDocumento.APROVADO } -> null
            status.all { it == StatusDocumento.EM_ANALISE || it == StatusDocumento.APROVADO } ->
                VeiculoRepository.STATUS_EM_ANALISE
            else -> VeiculoRepository.STATUS_PENDENTE
        }
    }

    /** Só veículo aprovado pode ser associado a uma rota. */
    fun podeSerAssociadoARota(veiculo: VeiculoEntity): Boolean =
        veiculo.status.equals(VeiculoRepository.STATUS_APROVADO, ignoreCase = true) ||
            veiculo.status.equals("ATIVO", ignoreCase = true) // valor legado
}
