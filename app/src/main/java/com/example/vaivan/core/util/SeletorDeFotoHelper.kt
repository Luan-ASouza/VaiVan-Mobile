package com.example.vaivan.core.util

import android.net.Uri
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.contract.ActivityResultContracts

/** Embrulha o fluxo "escolher uma imagem da galeria" (sempre a mesma coisa:
 * abrir o picker, devolver a Uri escolhida) — usado tanto na foto de perfil
 * quanto na foto do passageiro, pra não duplicar o registro do contrato em
 * cada tela. Precisa ser criado em onCreate/onCreateView (antes da tela
 * chegar em STARTED), igual qualquer uso de registerForActivityResult. */
class SeletorDeFotoHelper(
    caller: ActivityResultCaller,
    private val aoEscolher: (Uri) -> Unit
) {
    private val launcher = caller.registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) aoEscolher(uri)
    }

    fun abrirSeletor() {
        launcher.launch("image/*")
    }
}
