package com.example.vaivan.domain

import androidx.annotation.ColorRes
import com.example.vaivan.R
import com.example.vaivan.data.local.entities.ParadaViagemEntity
import com.example.vaivan.data.repository.ViagemRepository

/** Status único de um passageiro, combinando a parada de embarque e a de
 * desembarque dele num só rótulo — usado tanto no roteiro do motorista
 * quanto no acompanhamento do responsável, pra não duplicar a regra. */
data class StatusPassageiro(val texto: String, @ColorRes val cor: Int)

fun statusDoPassageiro(embarque: ParadaViagemEntity, desembarque: ParadaViagemEntity): StatusPassageiro = when {
    desembarque.status == ViagemRepository.PARADA_DESEMBARCADO ->
        StatusPassageiro("Desceu", R.color.status_success)
    embarque.status == ViagemRepository.PARADA_NAO_EMBARCOU ->
        StatusPassageiro("Não embarcou", R.color.status_danger)
    embarque.status == ViagemRepository.PARADA_EMBARCADO ->
        StatusPassageiro("Na van", R.color.status_success)
    else -> StatusPassageiro("Aguardando", R.color.light_gray)
}
