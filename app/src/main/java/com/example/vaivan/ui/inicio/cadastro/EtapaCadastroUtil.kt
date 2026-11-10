package com.example.vaivan.ui.inicio.cadastro

import android.widget.ImageButton
import androidx.fragment.app.Fragment
import com.example.vaivan.R
import com.google.android.material.progressindicator.LinearProgressIndicator

/** Total de telas reais do cadastro, na ordem em que o usuário passa por
 * elas — ver CadastroActivity.abrir*(). Cada tela conta como seu próprio
 * incremento (ex.: informações pessoais e data de nascimento são telas
 * diferentes, cada uma avança a barra, mesmo sem número de "etapa" exibido). */
private const val TOTAL_TELAS = 7

/** Configura o indicador de progresso + botão de voltar compartilhados
 * (`stepper_cadastro.xml`, incluído no topo do layout de cada etapa). [atual]
 * é a posição sequencial da tela (1 a [TOTAL_TELAS]). Só a barra é exibida —
 * sem número de etapa pro usuário. O botão de voltar aciona o mesmo back
 * stack do botão físico/gesto (CadastroActivity), então as duas formas de
 * voltar se comportam igual. */
fun Fragment.configurarEtapaCadastro(atual: Int) {
    val view = requireView()

    view.findViewById<LinearProgressIndicator>(R.id.progressEtapaCadastro).apply {
        max = TOTAL_TELAS
        progress = atual
    }
    view.findViewById<ImageButton>(R.id.btnVoltarEtapa).setOnClickListener {
        requireActivity().onBackPressedDispatcher.onBackPressed()
    }
}
