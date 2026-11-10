package com.example.vaivan.ui.motorista.veiculos

import androidx.annotation.DrawableRes
import com.example.vaivan.R

/** Marca/modelo/foto de van escolar aceitos no cadastro — lista fixa, sem
 * API (substitui uma integração anterior com a tabela FIPE + busca de foto
 * por CarImages/Wikimedia): o projeto atende só a esses 7 veículos, um
 * modelo por marca, então não há motivo pra consultar um catálogo externo
 * nem buscar foto por request — a foto já vem junto, local. */
data class VeiculoEscolar(
    val marca: String,
    val modelo: String,
    @DrawableRes val foto: Int
)

/** [VeiculoEscolar.foto] aponta pra um drawable placeholder (alias pro
 * ic_truck) até a foto real de cada modelo ser adicionada — pra trocar,
 * basta apagar o `foto_<modelo>.xml` correspondente em res/drawable e
 * colocar no lugar uma imagem (.png/.jpg/.webp) com o mesmo nome. */
val VEICULOS_ESCOLARES = listOf(
    VeiculoEscolar("Citroën", "Jumper", R.drawable.foto_citroen_jumper_branco),
    VeiculoEscolar("Fiat", "Ducato", R.drawable.foto_fiat_ducato_branco),
    VeiculoEscolar("Iveco", "Daily", R.drawable.foto_iveco_daily_branco),
    VeiculoEscolar("Mercedes", "Sprinter", R.drawable.foto_mercedes_benz_sprinter_branco),
    VeiculoEscolar("Peugeot", "Boxer", R.drawable.foto_peugeot_boxer_branco),
    VeiculoEscolar("Renault", "Master", R.drawable.foto_renault_master_branco),
    VeiculoEscolar("Volkswagen", "Kombi", R.drawable.foto_volkswagem_kombi_branco)
)
