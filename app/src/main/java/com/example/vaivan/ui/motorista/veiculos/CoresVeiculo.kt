package com.example.vaivan.ui.motorista.veiculos

import androidx.annotation.ColorInt

/** As 6 cores fixas que a CarImages aceita (`color=...`, ver CarImageClient)
 * — exibidas no dropdown de Cor com nome em português + uma bolinha de
 * amostra ([corAmostra]). [valorApi] é exatamente o que vai pro parâmetro
 * `color=` da API; como a escolha agora é restrita a essa lista, não há
 * mais normalização de texto livre no meio (ver CarImageClient). */
data class CorVeiculo(
    val nomeExibicao: String,
    val valorApi: String,
    @ColorInt val corAmostra: Int
) {
    override fun toString(): String = nomeExibicao
}

val CORES_VEICULO = listOf(
    CorVeiculo("Branco", "white", 0xFFFFFFFF.toInt()),
    CorVeiculo("Prata", "silver", 0xFFC0C0C0.toInt()),
    CorVeiculo("Cinza", "grey", 0xFF808080.toInt()),
    CorVeiculo("Preto", "black", 0xFF000000.toInt()),
    CorVeiculo("Azul", "blue", 0xFF1E88E5.toInt()),
    CorVeiculo("Vermelho", "red", 0xFFE53935.toInt())
)
