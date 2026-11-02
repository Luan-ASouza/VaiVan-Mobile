package com.example.vaivan.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Uma parada dentro de uma [ViagemEntity] (snapshot da sequência planejada no
 * momento em que a viagem começou). Pode ser embarque (ida: casa do aluno;
 * volta: a escola) ou desembarque (ida: a escola; volta: casa do aluno).
 * Quando mais de um passageiro desembarca/embarca no mesmo ponto (ex.: a
 * escola), existe uma linha por passageiro, todas com a mesma [ordem].
 */
@Entity(
    tableName = "paradas_viagem",
    indices = [Index(value = ["viagemId"]), Index(value = ["passageiroId"])]
)
data class ParadaViagemEntity(
    @PrimaryKey val id: String = "",
    val viagemId: String = "",

    val passageiroId: String = "",
    val nomePassageiro: String = "",
    val fotoPassageiroUrl: String? = null,

    // Denormalizado na criação da viagem: permite que as regras do Firestore
    // restrinjam a leitura por responsável sem precisar de consulta cruzada.
    val responsavelId: String = "",

    val localId: String = "",
    val nomeLocal: String = "",
    val endereco: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,

    val tipo: String = "EMBARQUE", // EMBARQUE | DESEMBARQUE
    val ordem: Int = 0,

    val status: String = "AGUARDANDO", // AGUARDANDO | EMBARCADO | NAO_EMBARCOU | AGUARDANDO_DESEMBARQUE | DESEMBARCADO
    val motivoAusencia: String? = null,

    val horarioPrevistoMinutos: Int = 0,
    val horarioEstimadoMinutos: Int = 0,
    val horarioRealMillis: Long? = null,

    val lastUpdated: Long = 0L
)
