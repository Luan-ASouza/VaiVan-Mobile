package com.example.vaivan.core.util

import android.net.Uri
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.contract.ActivityResultContracts
import com.example.vaivan.ui.comum.recorte.RecortarImagem

/** Embrulha o fluxo "escolher uma foto de avatar" (sempre a mesma coisa:
 * abrir o picker, recortar em quadrado, devolver a Uri) — usado tanto na foto
 * de perfil quanto na foto do passageiro, pra não duplicar o registro dos
 * contratos em cada tela. A Uri entregue já vem recortada e comprimida
 * (JPEG, ver [ImagemUtil.PERFIL]) — sobe direto, sem comprimir de novo.
 * Precisa ser criado em onCreate/onCreateView (antes da tela chegar em
 * STARTED), igual qualquer uso de registerForActivityResult. */
class SeletorDeFotoHelper(
    caller: ActivityResultCaller,
    private val aoEscolher: (Uri) -> Unit
) {
    private val recorte = caller.registerForActivityResult(RecortarImagem()) { uri ->
        if (uri != null) aoEscolher(uri)
    }

    private val seletor = caller.registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) recorte.launch(RecortarImagem.Pedido.avatar(uri))
    }

    fun abrirSeletor() {
        seletor.launch("image/*")
    }
}
