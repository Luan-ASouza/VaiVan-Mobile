package com.example.vaivan.data.models

import com.example.vaivan.data.local.entities.ParadaRotaEntity
import com.example.vaivan.data.local.entities.RotaEntity
import com.example.vaivan.data.local.entities.UsuarioEntity
import com.example.vaivan.data.local.entities.VeiculoEntity

data class RotasDoResponsavel(
    val rota: RotaEntity,
    val paradas: List<ParadaRotaEntity>,
    val motorista: UsuarioEntity?,
    val veiculo: VeiculoEntity?
)