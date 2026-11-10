package com.example.vaivan.ui.motorista.veiculos

import androidx.lifecycle.ViewModel
import com.example.vaivan.data.local.entities.VeiculoEntity
import com.example.vaivan.data.repository.VeiculoRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow

class ListaVeiculosViewModel(
    private val repository: VeiculoRepository
) : ViewModel() {

    private val motoristaId: String =
        FirebaseAuth
            .getInstance()
            .currentUser
            ?.uid
            ?: ""

    init {
        repository.iniciarSincronizacao(motoristaId)
    }

    val veiculos: Flow<List<VeiculoEntity>>
        get() =
            repository.observarVeiculosPorMotorista(
                motoristaId
            )

    /** Bloqueio de exclusão com veículo vinculado a rota já vem do
     * VeiculoRepository.excluirVeiculo (VeiculoVinculadoARotaException) —
     * aqui é só a ponte pra UI. */
    suspend fun excluirVeiculo(id: String) {
        repository.excluirVeiculo(id)
    }

    /** Pull-to-refresh: força uma releitura pontual, sem depender só do
     * listener contínuo (que pode não estar ativo nesse momento). */
    suspend fun atualizarVeiculos() {
        if (motoristaId.isNotBlank()) {
            repository.sincronizarVeiculosDoMotoristaUmaVez(motoristaId)
        }
    }
}
