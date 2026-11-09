package com.example.vaivan.ui.responsavel.mensalidades

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.vaivan.R
import com.example.vaivan.data.models.Contrato
import com.example.vaivan.data.models.Mensalidade
import com.example.vaivan.data.models.StatusMensalidade
import com.example.vaivan.data.repository.MensalidadeRepository
import com.example.vaivan.domain.usecase.mensalidade.MensalidadePolicy
import com.example.vaivan.domain.usecase.mensalidade.MensalidadePolicy.SituacaoMensalidade
import com.example.vaivan.ui.comum.mensalidade.MensalidadeUi
import com.example.vaivan.ui.motorista.assinatura.AssinaturaUi
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Mensalidades que o responsável paga aos motoristas dos filhos.
 * Mostra o que está em aberto (mais antigo primeiro) e o histórico do que já foi pago.
 * O pagamento pelo app é SIMULADO, como na assinatura do motorista.
 */
class MensalidadesResponsavelActivity : AppCompatActivity() {

    private val repository = MensalidadeRepository()

    private var contratos: List<Contrato> = emptyList()
    private var mensalidades: List<Mensalidade> = emptyList()
    private var gerando = false

    private lateinit var progress: View
    private lateinit var conteudo: View
    private lateinit var container: LinearLayout
    private lateinit var txtVazio: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mensalidades_responsavel)

        AssinaturaUi.aplicarInsets(findViewById(R.id.rootMensalidades))
        findViewById<ImageButton>(R.id.btnVoltarMensalidades).setOnClickListener { finish() }

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            finish()
            return
        }

        progress = findViewById(R.id.progressMensalidades)
        conteudo = findViewById(R.id.conteudoMensalidades)
        container = findViewById(R.id.containerMensalidades)
        txtVazio = findViewById(R.id.txtVazioMensalidades)

        // Escuta contratos e mensalidades em tempo real: se o motorista registrar
        // um recebimento, a tela do responsável atualiza sozinha.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    repository.observarContratosDoResponsavel(uid),
                    repository.observarMensalidadesDoResponsavel(uid)
                ) { c, m -> c to m }.collect { (c, m) ->
                    contratos = c
                    mensalidades = m
                    renderizar()
                    gerarMensalidadesFaltando()
                }
            }
        }
    }

    /** Se o motorista ainda não abriu o app neste mês, quem cria a cobrança é o responsável. */
    private fun gerarMensalidadesFaltando() {
        if (gerando) return
        gerando = true
        val c = contratos
        val m = mensalidades
        lifecycleScope.launch {
            try {
                repository.gerarMensalidadesFaltando(c, m)
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao gerar mensalidades", e)
            } finally {
                gerando = false
            }
        }
    }

    private fun renderizar() {
        progress.visibility = View.GONE
        conteudo.visibility = View.VISIBLE

        val agora = System.currentTimeMillis()
        val validas = mensalidades.filter { it.status != StatusMensalidade.CANCELADA }
        val emAberto = validas.filter { it.status == StatusMensalidade.PENDENTE }.sortedBy { it.vencimento }
        val pagas = validas.filter { it.status == StatusMensalidade.PAGA }
            .sortedByDescending { it.competencia }
            .take(12)

        // RESUMO
        findViewById<TextView>(R.id.txtTotalAberto).text =
            AssinaturaUi.formatarValor(emAberto.sumOf { it.valorCentavos })

        val atrasadas = emAberto.count { MensalidadePolicy.situacao(it, agora) == SituacaoMensalidade.ATRASADA }
        findViewById<TextView>(R.id.txtResumoAberto).apply {
            text = when {
                emAberto.isEmpty() -> "Nenhuma mensalidade em aberto."
                atrasadas == 1 -> "1 mensalidade em atraso"
                atrasadas > 1 -> "$atrasadas mensalidades em atraso"
                else -> "Próximo vencimento: ${AssinaturaUi.formatarData(emAberto.first().vencimento)}"
            }
            setTextColor(ContextCompat.getColor(context, if (atrasadas > 0) R.color.red else R.color.medium_gray))
        }

        // LISTA
        container.removeAllViews()
        if (emAberto.isNotEmpty()) {
            adicionarTitulo("Em aberto")
            emAberto.forEach { container.addView(montarItem(it, agora)) }
        }
        if (pagas.isNotEmpty()) {
            adicionarTitulo("Pagas")
            pagas.forEach { container.addView(montarItem(it, agora)) }
        }

        txtVazio.visibility = if (validas.isEmpty()) View.VISIBLE else View.GONE
        txtVazio.text = if (contratos.isEmpty()) {
            "Quando um motorista aceitar seu filho em uma rota, as mensalidades aparecem aqui."
        } else {
            "O motorista ainda não definiu o valor da mensalidade."
        }
    }

    private fun adicionarTitulo(texto: String) {
        container.addView(TextView(this).apply {
            text = texto
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.dark_gray))
            setPadding(0, (18 * resources.displayMetrics.density).toInt(), 0, 0)
        })
    }

    private fun montarItem(m: Mensalidade, agora: Long): View {
        val item = layoutInflater.inflate(R.layout.item_mensalidade_responsavel, container, false)

        item.findViewById<TextView>(R.id.txtTituloMensalidade).text =
            "${m.nomePassageiro} · ${MensalidadeUi.nomeDoMes(m.competencia)}"

        val (rotulo, cor) = MensalidadeUi.rotuloDaMensalidade(m, agora)
        item.findViewById<TextView>(R.id.txtStatusMensalidade).apply {
            text = rotulo
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, cor))
        }

        item.findViewById<TextView>(R.id.txtValorMensalidade).text = AssinaturaUi.formatarValor(m.valorCentavos)

        item.findViewById<TextView>(R.id.txtDetalheMensalidade).text = listOf(
            MensalidadeUi.detalheDaMensalidade(m, paraMotorista = false),
            m.nomeRota
        ).filter { it.isNotBlank() }.joinToString(" · ")

        val btnPagar = item.findViewById<Button>(R.id.btnPagarMensalidade)
        if (m.status == StatusMensalidade.PENDENTE) {
            btnPagar.visibility = View.VISIBLE
            btnPagar.setOnClickListener { abrirPix(m) }
        } else {
            btnPagar.visibility = View.GONE
        }
        return item
    }

    // Reaproveita o mesmo bottom sheet de PIX simulado da assinatura do motorista.
    private fun abrirPix(m: Mensalidade) {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_pagamento_pix, null)
        sheet.setContentView(view)
        (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)

        view.findViewById<TextView>(R.id.txtPixDescricao).text =
            "Mensalidade de ${MensalidadeUi.nomeDoMes(m.competencia)} – ${m.nomePassageiro}"
        view.findViewById<TextView>(R.id.txtPixValor).text = AssinaturaUi.formatarValor(m.valorCentavos)

        val btnConfirmar = view.findViewById<Button>(R.id.btnConfirmarPix)
        btnConfirmar.setOnClickListener {
            btnConfirmar.isEnabled = false
            lifecycleScope.launch {
                try {
                    repository.pagarPeloApp(m.id)
                    sheet.dismiss()
                    Toast.makeText(this@MensalidadesResponsavelActivity, "Pagamento confirmado!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    btnConfirmar.isEnabled = true
                    Log.e(TAG, "Erro ao pagar mensalidade", e)
                    Toast.makeText(this@MensalidadesResponsavelActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        view.findViewById<Button>(R.id.btnCancelarPix).setOnClickListener { sheet.dismiss() }

        sheet.show()
    }

    private companion object {
        const val TAG = "MensalidadesResponsavel"
    }
}