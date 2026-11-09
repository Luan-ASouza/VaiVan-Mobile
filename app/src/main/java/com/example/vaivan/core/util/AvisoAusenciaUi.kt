package com.example.vaivan.core.util

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.vaivan.R
import com.example.vaivan.data.local.entities.AvisoAusenciaEntity
import com.example.vaivan.data.repository.AvisoAusenciaRepository
import com.example.vaivan.data.repository.ViagemRepository
import com.example.vaivan.domain.usecase.ausencia.RegistrarAvisoAusenciaUseCase
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Preenche o bloco "Marcar falta" (bloco_aviso_ausencia.xml) dentro de
 * [container] — reaproveitado tanto nos cards de ListaRotasFragment quanto no
 * bottom sheet de AcompanharViagemActivity, pra não duplicar a lógica de
 * diálogo/estado nos dois lugares.
 *
 * Rota de uma direção só: um clique abre a confirmação simples de sempre.
 * Rota de ida e volta: abre um modal com Ida/Volta/Ida e volta — segmentos
 * que já não fariam diferença hoje (o filho já embarcou, ou a viagem daquela
 * direção já terminou — ver ViagemRepository.aindaDaParaAvisarFaltaHoje)
 * aparecem bloqueados; se nenhuma direção estiver mais livre, o botão nem
 * aparece.
 */
fun configurarBlocoAvisoAusencia(
    container: ViewGroup,
    lifecycleOwner: LifecycleOwner,
    repository: AvisoAusenciaRepository,
    registrarAvisoUseCase: RegistrarAvisoAusenciaUseCase,
    viagemRepository: ViagemRepository,
    rotaId: String,
    passageiroId: String,
    nomePassageiro: String,
    responsavelId: String,
    motoristaId: String,
    rotaIdaEVolta: Boolean
) {
    val context = container.context
    container.removeAllViews()
    LayoutInflater.from(context).inflate(R.layout.bloco_aviso_ausencia, container, true)

    val containerBotoes = container.findViewById<View>(R.id.containerBotoesAvisoAusencia)
    val btnMarcarFalta = container.findViewById<View>(R.id.btnMarcarFalta)
    val containerSemAcao = container.findViewById<View>(R.id.containerSemAcaoAvisoAusencia)
    val containerStatus = container.findViewById<View>(R.id.containerStatusAvisoAusencia)
    val txtStatus = container.findViewById<TextView>(R.id.txtStatusAvisoAusencia)
    val btnCancelar = container.findViewById<View>(R.id.btnCancelarAvisoAusencia)

    fun salvarAviso(tipo: String, pendenteIda: Boolean, pendenteVolta: Boolean, onSalvo: () -> Unit) {
        lifecycleOwner.lifecycleScope.launch {
            registrarAvisoUseCase(
                AvisoAusenciaEntity(
                    rotaId = rotaId,
                    passageiroId = passageiroId,
                    nomePassageiro = nomePassageiro,
                    responsavelId = responsavelId,
                    motoristaId = motoristaId,
                    tipo = tipo,
                    pendenteIda = pendenteIda,
                    pendenteVolta = pendenteVolta
                ),
                rotaIdaEVolta
            )
            onSalvo()
        }
    }

    fun atualizarEstado() {
        lifecycleOwner.lifecycleScope.launch {
            val aviso = repository.observarAvisoAtivo(rotaId, passageiroId).first()

            if (aviso != null) {
                containerBotoes.visibility = View.GONE
                containerSemAcao.visibility = View.GONE
                containerStatus.visibility = View.VISIBLE

                val descricao = when (aviso.tipo) {
                    AvisoAusenciaRepository.TIPO_DIA_INTEIRO -> "Falta avisada hoje"
                    AvisoAusenciaRepository.TIPO_SOMENTE_VOLTA -> "Não vai usar o transporte de volta hoje"
                    else -> "Não vai usar o transporte de ida hoje"
                }

                // Pra TIPO_DIA_INTEIRO numa rota de ida e volta, as duas
                // direções podem ser consumidas em momentos diferentes (ex.:
                // a ida já aconteceu, a volta ainda não) — "intacto" aqui é
                // "ainda tem PELO MENOS UMA direção pendente pra cancelar",
                // não "as duas". Cancelar sempre apaga o aviso inteiro
                // (excluirAviso), o que é seguro mesmo com uma direção já
                // consumida: essa parte já é passado, não depende mais do
                // aviso; cancelar só impede a direção que ainda falta.
                val intacto = when (aviso.tipo) {
                    AvisoAusenciaRepository.TIPO_DIA_INTEIRO -> aviso.pendenteIda || (rotaIdaEVolta && aviso.pendenteVolta)
                    AvisoAusenciaRepository.TIPO_SOMENTE_VOLTA -> aviso.pendenteVolta
                    else -> aviso.pendenteIda
                }

                val parcialmenteConsumido = aviso.tipo == AvisoAusenciaRepository.TIPO_DIA_INTEIRO &&
                    rotaIdaEVolta && aviso.pendenteIda != aviso.pendenteVolta

                txtStatus.text = when {
                    !intacto -> "$descricao — já considerada pelo motorista, não pode mais ser cancelada."
                    parcialmenteConsumido && !aviso.pendenteIda -> "Falta da ida já considerada pelo motorista. A falta da volta ainda pode ser cancelada."
                    parcialmenteConsumido && !aviso.pendenteVolta -> "Falta da volta já considerada pelo motorista. A falta da ida ainda pode ser cancelada."
                    else -> descricao
                }

                if (intacto) {
                    btnCancelar.visibility = View.VISIBLE
                    btnCancelar.setOnClickListener {
                        lifecycleOwner.lifecycleScope.launch {
                            repository.excluirAviso(aviso)
                            atualizarEstado()
                        }
                    }
                } else {
                    btnCancelar.visibility = View.GONE
                }
                return@launch
            }

            containerStatus.visibility = View.GONE

            if (!rotaIdaEVolta) {
                containerBotoes.visibility = View.VISIBLE
                containerSemAcao.visibility = View.GONE
                return@launch
            }

            val idaLivre = viagemRepository.aindaDaParaAvisarFaltaHoje(rotaId, passageiroId, ViagemRepository.DIRECAO_IDA)
            val voltaLivre = viagemRepository.aindaDaParaAvisarFaltaHoje(rotaId, passageiroId, ViagemRepository.DIRECAO_VOLTA)

            if (!idaLivre && !voltaLivre) {
                containerBotoes.visibility = View.GONE
                containerSemAcao.visibility = View.VISIBLE
            } else {
                containerBotoes.visibility = View.VISIBLE
                containerSemAcao.visibility = View.GONE
            }
        }
    }

    btnMarcarFalta.setOnClickListener {
        if (!rotaIdaEVolta) {
            MaterialAlertDialogBuilder(context)
                .setTitle("Marcar falta")
                .setMessage(
                    "Seu filho não vai usar o transporte hoje. O motorista será avisado e " +
                        "essa parada não vai entrar na rota."
                )
                .setPositiveButton("Continuar") { _, _ ->
                    salvarAviso(
                        tipo = AvisoAusenciaRepository.TIPO_DIA_INTEIRO,
                        pendenteIda = true,
                        pendenteVolta = false
                    ) { atualizarEstado() }
                }
                .setNegativeButton("Cancelar", null)
                .show()
            return@setOnClickListener
        }

        lifecycleOwner.lifecycleScope.launch {
            val idaLivre = viagemRepository.aindaDaParaAvisarFaltaHoje(rotaId, passageiroId, ViagemRepository.DIRECAO_IDA)
            val voltaLivre = viagemRepository.aindaDaParaAvisarFaltaHoje(rotaId, passageiroId, ViagemRepository.DIRECAO_VOLTA)

            if (!idaLivre && !voltaLivre) {
                // Estado mudou entre a última atualizarEstado() e o clique —
                // reflete isso na UI em vez de abrir um modal sem opção útil.
                atualizarEstado()
                return@launch
            }

            mostrarModalMarcarFalta(context, idaLivre, voltaLivre) { tipo, pendenteIda, pendenteVolta ->
                salvarAviso(tipo, pendenteIda, pendenteVolta) { atualizarEstado() }
            }
        }
    }

    atualizarEstado()
}

/** Modal com o seletor Ida/Volta/Ida e volta — [idaLivre]/[voltaLivre]
 * decidem quais segmentos ficam habilitados. [onConfirmar] recebe o
 * tipo/pendenteIda/pendenteVolta já no formato que AvisoAusenciaEntity
 * espera. */
private fun mostrarModalMarcarFalta(
    context: android.content.Context,
    idaLivre: Boolean,
    voltaLivre: Boolean,
    onConfirmar: (tipo: String, pendenteIda: Boolean, pendenteVolta: Boolean) -> Unit
) {
    val view = LayoutInflater.from(context).inflate(R.layout.dialog_marcar_falta, null)

    val toggle = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleDirecaoFalta)
    val btnIda = view.findViewById<View>(R.id.btnFaltaIda)
    val btnVolta = view.findViewById<View>(R.id.btnFaltaVolta)
    val btnAmbas = view.findViewById<View>(R.id.btnFaltaAmbas)
    val txtAviso = view.findViewById<TextView>(R.id.txtAvisoBloqueioFalta)
    val btnCancelar = view.findViewById<View>(R.id.btnCancelarMarcarFalta)
    val btnConfirmar = view.findViewById<View>(R.id.btnConfirmarMarcarFalta)

    btnIda.isEnabled = idaLivre
    btnAmbas.isEnabled = idaLivre && voltaLivre
    btnVolta.isEnabled = voltaLivre

    when {
        !idaLivre && voltaLivre -> {
            toggle.check(R.id.btnFaltaVolta)
            txtAviso.visibility = View.VISIBLE
            txtAviso.text = "Seu filho já embarcou na viagem de ida de hoje — só dá pra avisar falta na volta."
        }
        idaLivre && !voltaLivre -> {
            toggle.check(R.id.btnFaltaIda)
            txtAviso.visibility = View.VISIBLE
            txtAviso.text = "Seu filho já embarcou na viagem de volta de hoje — só dá pra avisar falta na ida."
        }
        else -> {
            toggle.check(R.id.btnFaltaAmbas)
            txtAviso.visibility = View.GONE
        }
    }

    val dialog = MaterialAlertDialogBuilder(context)
        .setView(view)
        .create()

    btnCancelar.setOnClickListener { dialog.dismiss() }

    btnConfirmar.setOnClickListener {
        val (tipo, pendenteIda, pendenteVolta) = when (toggle.checkedButtonId) {
            R.id.btnFaltaIda -> Triple(AvisoAusenciaRepository.TIPO_SOMENTE_IDA, true, false)
            R.id.btnFaltaVolta -> Triple(AvisoAusenciaRepository.TIPO_SOMENTE_VOLTA, false, true)
            else -> Triple(AvisoAusenciaRepository.TIPO_DIA_INTEIRO, true, true)
        }

        dialog.dismiss()
        onConfirmar(tipo, pendenteIda, pendenteVolta)
    }

    dialog.show()
}
