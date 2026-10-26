package com.example.vaivan.domain.usecase.rota

object HorariosDaRota {

    fun minutosPorParada(
        duracoesPernasSegundos: List<Int>,
        quantidadeParadas: Int,
        primeiraParadaEhASaida: Boolean = false
    ): List<Int> {

        val resultado = ArrayList<Int>(quantidadeParadas)
        var acumuladoSegundos = 0

        for (indice in 0 until quantidadeParadas) {

            val indicePerna = if (primeiraParadaEhASaida) indice - 1 else indice

            if (indicePerna >= 0) {
                acumuladoSegundos += duracoesPernasSegundos.getOrElse(indicePerna) { 0 }
            }

            resultado.add(acumuladoSegundos / 60)
        }

        return resultado
    }
}