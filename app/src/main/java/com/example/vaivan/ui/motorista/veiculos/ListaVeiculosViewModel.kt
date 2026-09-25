package com.example.vaivan.ui.motorista.veiculos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.entities.VeiculoEntity
import com.example.vaivan.data.repository.VeiculoRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow

class ListaVeiculosViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository: VeiculoRepository

    private val motoristaId: String

    init {

        motoristaId =
            FirebaseAuth
                .getInstance()
                .currentUser
                ?.uid
                ?: ""

        val database =
            VaivanDatabase.getInstance(application)

        repository =
            VeiculoRepository(
                database.veiculoDao()
            )

        repository.iniciarSincronizacao(motoristaId)
    }

    val veiculos: Flow<List<VeiculoEntity>>
        get() =
            repository.observarVeiculosPorMotorista(
                motoristaId
            )
}