package com.example.vaivan.data.models

import com.example.vaivan.data.local.entities.RotaEntity

data class RotaCompativel(
    val rota: RotaEntity,
    val distanciaEmbarqueMetros: Int,
    val distanciaDestinoMetros: Int
)