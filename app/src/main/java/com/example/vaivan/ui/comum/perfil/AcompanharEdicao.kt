package com.example.vaivan.ui.comum.perfil

import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.vaivan.R
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Comportamento comum das telas de edição (perfil do usuário e do passageiro),
 * pra não repetir em cada Activity: botão voltar, preencher a tela uma vez
 * quando o item chega, e reagir aos estados do [EdicaoViewModel]
 * (ocupado, toast, fechar).
 */
internal fun <T : Any> AppCompatActivity.acompanharEdicao(
    viewModel: EdicaoViewModel<T>,
    aoMudarOcupado: (ocupado: Boolean) -> Unit,
    aoCarregar: (T) -> Unit,
    aoFalharSalvar: (mensagem: String) -> Unit = { mensagem ->
        Toast.makeText(this, mensagem, Toast.LENGTH_LONG).show()
    },
    aoSalvarSemFechar: () -> Unit = {}
) {
    findViewById<ImageButton>(R.id.btnVoltarEdicaoPerfil).setOnClickListener { finish() }

    // Preenche só na primeira vez: depois disso o que vale é o que o
    // usuário digitou.
    lifecycleScope.launch {
        aoCarregar(viewModel.item.filterNotNull().first())
    }

    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.estado.collect { estado ->
                aoMudarOcupado(
                    estado == EstadoEdicao.Carregando || estado == EstadoEdicao.Salvando
                )

                when (estado) {
                    is EstadoEdicao.FalhaCarregar -> {
                        Toast.makeText(this@acompanharEdicao, estado.mensagem, Toast.LENGTH_LONG).show()
                        finish()
                    }

                    is EstadoEdicao.FalhaSalvar -> {
                        aoFalharSalvar(estado.mensagem)
                        viewModel.resultadoTratado()
                    }

                    is EstadoEdicao.Salvo -> {
                        Toast.makeText(this@acompanharEdicao, estado.mensagem, Toast.LENGTH_SHORT).show()
                        if (estado.fecharTela) {
                            finish()
                        } else {
                            aoSalvarSemFechar()
                            viewModel.resultadoTratado()
                        }
                    }

                    else -> Unit
                }
            }
        }
    }

    viewModel.carregar()
}
