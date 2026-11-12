package com.example.vaivan.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.vaivan.domain.usecase.preco.TabelaPreco

@Entity(
    tableName = "rotas",
    indices = [
        Index(value = ["motoristaId"]),
        Index(value = ["destinoNome"]),
        Index(value = ["turno"]),
        Index(value = ["tipoTrajeto"])
    ]
)
data class RotaEntity(
    @PrimaryKey val id: String = "",
    val motoristaId: String = "",
    val veiculoId: String? = null,
    val nome: String = "", // ex: "La Salle Carmo - Manhã"

    val tipoTrajeto: String = "IDA", // IDA | VOLTA | IDA_E_VOLTA

    // Ponto A (bairro / saída do motorista)
    val origemNome: String = "",
    val origemEndereco: String = "",
    val origemLatitude: Double = 0.0,
    val origemLongitude: Double = 0.0,

    // Ponto B (escola)
    val destinoNome: String = "",
    val destinoEndereco: String = "",
    val destinoLatitude: Double = 0.0,
    val destinoLongitude: Double = 0.0,

    val turno: String = "", // MANHA | TARDE | NOITE
    val diasSemana: String = "", // ex: "SEG,TER,QUA,QUI,SEX"
    val escolaId: String = "",
    val escolaNome: String = "",
    val bairrosAtendidos: List<String> = emptyList(),

    val capacidadeTotal: Int = 0,
    val vagasOcupadas: Int = 0,

    // Mensalidade cobrada por passageiro (copiada para o contrato no aceite)
    val valorMensalCentavos: Long = 0L,
    val diaVencimento: Int = 10, // 1 a 28

    // Tabela de preços: valores de IDA E VOLTA por quantidade de dias na semana
    val preco1DiaCentavos: Long = 0L,
    val preco2DiasCentavos: Long = 0L,
    val preco3DiasCentavos: Long = 0L,
    val preco4DiasCentavos: Long = 0L,
    val preco5DiasCentavos: Long = 0L,
    val percentualUmTrecho: Int = 60,     // só ida ou só volta = 60% do valor
    val kmIncluidos: Int = 5,             // distância casa → escola já incluída
    val valorKmExtraCentavos: Long = 0L,  // por km acima do incluído

    val distanciaMetros: Int = 0,
    val duracaoSegundos: Int = 0,
    val duracaoSemTrafegoSegundos: Int = 0,
    val polylineEncoded: String = "",

    val status: String = "ATIVA", // ATIVA | ARQUIVADA
    val calculadoEm: Long = 0L,
    // Valor de calculadoEm quando a ordem da volta (ParadaRotaEntity.ordemVolta)
    // foi calculada. Diferente de calculadoEm = a rota mudou depois e a volta
    // precisa ser recalculada. 0 = nunca calculada.
    val voltaCalculadaEm: Long = 0L,
    val lastUpdated: Long = 0L
)

/** A tabela de preços desta rota, ou null se o motorista ainda não preencheu. */
fun RotaEntity.tabelaPreco(): TabelaPreco? =
    TabelaPreco(
        precoPorDiasCentavos = listOf(
            preco1DiaCentavos, preco2DiasCentavos, preco3DiasCentavos,
            preco4DiasCentavos, preco5DiasCentavos
        ),
        percentualUmTrecho = percentualUmTrecho,
        kmIncluidos = kmIncluidos,
        valorKmExtraCentavos = valorKmExtraCentavos
    ).takeIf { it.completa }

/** Cópia da rota com a tabela aplicada (null = mantém a que já tinha). */
fun RotaEntity.comTabela(tabela: TabelaPreco?): RotaEntity =
    if (tabela == null) this else copy(
        preco1DiaCentavos = tabela.precoPorDiasCentavos[0],
        preco2DiasCentavos = tabela.precoPorDiasCentavos[1],
        preco3DiasCentavos = tabela.precoPorDiasCentavos[2],
        preco4DiasCentavos = tabela.precoPorDiasCentavos[3],
        preco5DiasCentavos = tabela.precoPorDiasCentavos[4],
        percentualUmTrecho = tabela.percentualUmTrecho,
        kmIncluidos = tabela.kmIncluidos,
        valorKmExtraCentavos = tabela.valorKmExtraCentavos
    )
