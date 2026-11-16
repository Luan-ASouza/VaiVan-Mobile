package com.example.vaivan.ui.motorista.rotas

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.vaivan.R
import com.example.vaivan.data.local.entities.RotaEntity
import com.example.vaivan.data.local.entities.ViagemEntity
import com.example.vaivan.data.repository.AssinaturaBloqueadaException
import com.example.vaivan.data.repository.ViagemRepository
import com.example.vaivan.ui.motorista.assinatura.AssinaturaUi
import com.example.vaivan.ui.motorista.rotas.navegacao.NavegacaoViagemActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch

/**
 * "Iniciar viagem" de uma rota, igual em Minhas rotas e no detalhe da rota:
 * confere se dá pra iniciar, abre o modal de Ida/Volta (só as direções que
 * a rota tem), inicia e leva para a navegação. Os erros (assinatura, rede)
 * viram aviso na tela.
 *
 * [iniciar] é quem de fato cria a viagem (ViewModel da tela), e
 * [aoMudarOcupado] trava o botão da tela enquanto a viagem é criada.
 */
fun abrirIniciarViagem(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    rota: RotaEntity,
    aoMudarOcupado: (Boolean) -> Unit = {},
    iniciar: suspend (direcao: String) -> ViagemEntity
) {
    if (rota.status != "ATIVA") {
        Toast.makeText(context, "Esta rota está arquivada.", Toast.LENGTH_SHORT).show()
        return
    }
    if (rota.vagasOcupadas <= 0) {
        Toast.makeText(context, "A rota ainda não tem alunos confirmados.", Toast.LENGTH_LONG).show()
        return
    }

    val sheet = BottomSheetDialog(context)
    val view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_iniciar_viagem, null)
    sheet.setContentView(view)
    (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)

    view.findViewById<TextView>(R.id.txtRotaIniciarViagem).text = rota.nome

    val temIda = rota.tipoTrajeto == "IDA" || rota.tipoTrajeto == "IDA_E_VOLTA"
    val temVolta = rota.tipoTrajeto == "VOLTA" || rota.tipoTrajeto == "IDA_E_VOLTA"

    view.findViewById<TextView>(R.id.txtDescricaoIdaViagem).text = "Busca os alunos e leva até a escola"
    view.findViewById<TextView>(R.id.txtDescricaoVoltaViagem).text = "Busca os alunos na escola e leva de volta"

    fun configurar(idOpcao: Int, disponivel: Boolean, direcao: String) {
        val opcao = view.findViewById<View>(idOpcao)
        opcao.visibility = if (disponivel) View.VISIBLE else View.GONE
        opcao.setOnClickListener {
            sheet.dismiss()
            executarInicio(context, lifecycleOwner, direcao, aoMudarOcupado, iniciar)
        }
    }

    configurar(R.id.opcaoIdaViagem, temIda, ViagemRepository.DIRECAO_IDA)
    configurar(R.id.opcaoVoltaViagem, temVolta, ViagemRepository.DIRECAO_VOLTA)

    sheet.show()
}

/** Leva para a navegação de uma viagem que já está em andamento. */
fun abrirNavegacaoDaViagem(context: Context, viagemId: String) {
    context.startActivity(
        Intent(context, NavegacaoViagemActivity::class.java)
            .putExtra(NavegacaoViagemActivity.EXTRA_VIAGEM_ID, viagemId)
    )
}

private fun executarInicio(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    direcao: String,
    aoMudarOcupado: (Boolean) -> Unit,
    iniciar: suspend (direcao: String) -> ViagemEntity
) {
    aoMudarOcupado(true)

    lifecycleOwner.lifecycleScope.launch {
        try {
            val viagem = iniciar(direcao)
            abrirNavegacaoDaViagem(context, viagem.id)
        } catch (erro: AssinaturaBloqueadaException) {
            AssinaturaUi.mostrarBloqueio(
                context,
                erro.message ?: "Assinatura necessária para iniciar a viagem."
            )
        } catch (erro: Exception) {
            Toast.makeText(
                context,
                "Não foi possível iniciar a viagem: ${erro.message}",
                Toast.LENGTH_LONG
            ).show()
        } finally {
            aoMudarOcupado(false)
        }
    }
}
