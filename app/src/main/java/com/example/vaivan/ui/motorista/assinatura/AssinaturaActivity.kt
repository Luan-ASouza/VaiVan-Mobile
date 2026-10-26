package com.example.vaivan.ui.motorista.assinatura

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.vaivan.R
import com.example.vaivan.data.models.Assinatura
import com.example.vaivan.data.models.Pagamento
import com.example.vaivan.data.models.Plano
import com.example.vaivan.data.models.StatusPagamento
import com.example.vaivan.data.models.TipoPagamento
import com.example.vaivan.data.repository.AssinaturaBloqueadaException
import com.example.vaivan.data.repository.AssinaturaRepository
import com.example.vaivan.domain.usecase.assinatura.AssinaturaPolicy
import com.example.vaivan.domain.usecase.assinatura.AssinaturaPolicy.Estado
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/**
 * Tela "Planos de assinatura" do motorista: situação da assinatura, planos disponíveis
 * (contratar, upgrade, downgrade), cancelamento e cobranças (pagamento simulado).
 */
class AssinaturaActivity : AppCompatActivity() {

    private val repository by lazy { AssinaturaRepository() }

    private var motoristaId: String = ""

    private var assinatura: Assinatura? = null
    private var planos: List<Plano> = emptyList()
    private var pagamentos: List<Pagamento> = emptyList()

    private lateinit var txtStatus: TextView
    private lateinit var txtPlanoAtual: TextView
    private lateinit var txtVigencia: TextView
    private lateinit var txtAviso: TextView
    private lateinit var btnCancelar: Button
    private lateinit var btnDesfazer: Button
    private lateinit var containerPlanos: LinearLayout
    private lateinit var containerCobrancas: LinearLayout
    private lateinit var txtSemCobrancas: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_assinatura)

        AssinaturaUi.aplicarInsets(findViewById(R.id.rootAssinatura))

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            finish()
            return
        }
        motoristaId = uid

        txtStatus = findViewById(R.id.txtStatusAssinatura)
        txtPlanoAtual = findViewById(R.id.txtPlanoAtual)
        txtVigencia = findViewById(R.id.txtVigenciaAssinatura)
        txtAviso = findViewById(R.id.txtAvisoAssinatura)
        btnCancelar = findViewById(R.id.btnCancelarAssinatura)
        btnDesfazer = findViewById(R.id.btnDesfazerCancelamento)
        containerPlanos = findViewById(R.id.containerPlanos)
        containerCobrancas = findViewById(R.id.containerCobrancas)
        txtSemCobrancas = findViewById(R.id.txtSemCobrancas)

        findViewById<ImageButton>(R.id.btnVoltarAssinatura).setOnClickListener { finish() }
        btnCancelar.setOnClickListener { confirmarCancelamento() }
        btnDesfazer.setOnClickListener { desfazerCancelamento() }

        observar()
    }

    // ---------------------------------------------------------
    // DADOS EM TEMPO REAL
    // ---------------------------------------------------------

    private fun observar() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    repository.observarAssinatura(motoristaId).collect {
                        assinatura = it
                        renderizar()
                    }
                }

                launch {
                    repository.observarPlanos().collect {
                        planos = it
                        renderizar()
                    }
                }

                launch {
                    repository.observarPagamentos(motoristaId).collect {
                        pagamentos = it
                        renderizar()
                    }
                }

                launch {
                    try {
                        repository.gerarCobrancaDeRenovacaoSeNecessario(motoristaId)
                    } catch (e: Exception) {
                        Log.w(TAG, "Não foi possível emitir a cobrança de renovação", e)
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------
    // TELA
    // ---------------------------------------------------------

    private fun renderizar() {
        val agora = System.currentTimeMillis()
        val a = assinatura
        val estado = AssinaturaPolicy.estado(a, agora)

        // Selo de status
        val (rotulo, cor) = AssinaturaUi.rotuloDoEstado(estado, a?.cancelamentoAgendado == true)
        txtStatus.text = rotulo
        txtStatus.backgroundTintList = ColorStateList.valueOf(cor)

        // Plano atual
        txtPlanoAtual.text = when {
            a == null || estado == Estado.SEM_PLANO -> "Nenhum plano contratado"
            estado == Estado.EXPIRADA || estado == Estado.CANCELADA ->
                "Último plano: ${a.planoNome}"
            else ->
                "${a.planoNome} · ${AssinaturaUi.formatarValor(a.valorCentavos)}/mês"
        }

        txtVigencia.text = textoDeVigencia(estado, a)

        // Aviso de mudança de plano agendada
        val agendado = a?.planoAgendadoId
        if (a != null && agendado != null && estado == Estado.ATIVA) {
            val nomeAgendado = planos.firstOrNull { it.id == agendado }?.nome ?: agendado
            txtAviso.text = "Mudança para o $nomeAgendado na próxima renovação " +
                    "(${AssinaturaUi.formatarData(a.proximaCobranca)})."
            txtAviso.visibility = View.VISIBLE
        } else {
            txtAviso.visibility = View.GONE
        }

        // Botões de cancelamento
        val ativaSemCancelamento = estado == Estado.ATIVA && a?.cancelamentoAgendado == false
        btnCancelar.visibility =
            if (ativaSemCancelamento ||
                estado == Estado.AGUARDANDO_PAGAMENTO ||
                estado == Estado.EM_CARENCIA ||
                estado == Estado.SUSPENSA
            ) View.VISIBLE else View.GONE

        btnDesfazer.visibility =
            if (estado == Estado.ATIVA && a?.cancelamentoAgendado == true) View.VISIBLE else View.GONE

        renderizarPlanos(estado, a)
        renderizarCobrancas()
    }

    private fun textoDeVigencia(estado: Estado, a: Assinatura?): String = when (estado) {
        Estado.SEM_PLANO ->
            "Escolha um plano abaixo para criar rotas e receber passageiros."
        Estado.AGUARDANDO_PAGAMENTO ->
            "Conclua o pagamento para ativar o seu plano."
        Estado.ATIVA ->
            if (a != null && a.cancelamentoAgendado) {
                "Seu acesso vai até ${AssinaturaUi.formatarData(a.proximaCobranca)}."
            } else {
                "Próxima cobrança em ${a?.let { AssinaturaUi.formatarData(it.proximaCobranca) }}."
            }
        Estado.EM_CARENCIA ->
            "Venceu em ${AssinaturaUi.formatarData(a!!.proximaCobranca)}. " +
                    "Pague até ${AssinaturaUi.formatarData(AssinaturaPolicy.dataLimiteDaCarencia(a))} " +
                    "para evitar a suspensão."
        Estado.SUSPENSA ->
            if (a != null && a.status == "SUSPENSA") {
                "Assinatura suspensa pelo suporte. Entre em contato para regularizar."
            } else {
                "Venceu e o prazo de carência terminou. Pague a cobrança pendente para voltar a " +
                        "criar rotas e adicionar passageiros."
            }
        Estado.EXPIRADA, Estado.CANCELADA ->
            "Assine um plano para voltar a criar rotas e receber passageiros."
    }

    // ---------------------------------------------------------
    // PLANOS
    // ---------------------------------------------------------

    private fun renderizarPlanos(estado: Estado, a: Assinatura?) {
        containerPlanos.removeAllViews()

        planos.forEach { plano ->
            val item = layoutInflater.inflate(R.layout.item_plano, containerPlanos, false)

            // Cores do Figma: plano limitado = laranja (texto escuro); ilimitado = azul (texto branco)
            item.findViewById<View>(R.id.faixaPlano).setBackgroundColor(
                ContextCompat.getColor(
                    this, if (plano.ilimitado) R.color.light_blue else R.color.orange
                )
            )
            val nomeDoPlano = item.findViewById<TextView>(R.id.txtNomePlano)
            nomeDoPlano.text = plano.nome
            nomeDoPlano.setTextColor(
                ContextCompat.getColor(
                    this, if (plano.ilimitado) R.color.white else R.color.dark_gray
                )
            )
            item.findViewById<TextView>(R.id.txtValorPlano).text =
                "${AssinaturaUi.formatarValor(plano.valorCentavos)}/mês"
            item.findViewById<TextView>(R.id.txtBeneficiosPlano).text =
                plano.beneficios.joinToString("\n") { "• $it" }

            val btnAcao = item.findViewById<Button>(R.id.btnAcaoPlano)
            val txtAviso = item.findViewById<TextView>(R.id.txtAvisoPlano)

            configurarBotaoDoPlano(plano, estado, a, btnAcao, txtAviso)

            containerPlanos.addView(item)
        }
    }

    private fun configurarBotaoDoPlano(
        plano: Plano,
        estado: Estado,
        a: Assinatura?,
        botao: Button,
        aviso: TextView
    ) {
        // Estilo do botão (Figma): plano atual = contorno laranja; ação disponível = cor do plano;
        // indisponível = contorno cinza esmaecido.
        fun aplicarEstilo(texto: String, acao: (() -> Unit)?, planoAtual: Boolean) {
            botao.text = texto
            botao.isEnabled = acao != null
            botao.setOnClickListener { acao?.invoke() }

            when {
                planoAtual -> {
                    botao.setBackgroundResource(R.drawable.bg_hollow_orange)
                    botao.setTextColor(ContextCompat.getColor(this, R.color.dark_gray))
                    botao.alpha = 1f
                }
                acao == null -> {
                    botao.setBackgroundResource(R.drawable.bg_hollow_darkgray)
                    botao.setTextColor(ContextCompat.getColor(this, R.color.dark_gray))
                    botao.alpha = 0.5f
                }
                plano.ilimitado -> {
                    botao.setBackgroundResource(R.drawable.bg_button_light_blue)
                    botao.setTextColor(ContextCompat.getColor(this, R.color.white))
                    botao.alpha = 1f
                }
                else -> {
                    botao.setBackgroundResource(R.drawable.bg_button_orange)
                    botao.setTextColor(ContextCompat.getColor(this, R.color.dark_gray))
                    botao.alpha = 1f
                }
            }
        }

        // Atalho usado na maioria dos casos (o último parâmetro precisa ser a ação).
        fun definir(texto: String, acao: (() -> Unit)?) = aplicarEstilo(texto, acao, false)

        when (estado) {

            Estado.SEM_PLANO, Estado.EXPIRADA, Estado.CANCELADA ->
                definir("Assinar") { contratar(plano) }

            Estado.AGUARDANDO_PAGAMENTO ->
                if (a?.planoId == plano.id) {
                    definir("Pagar agora") { pagarCobrancaPendenteDaAssinatura() }
                } else {
                    definir("Trocar para este plano") { contratar(plano) }
                }

            Estado.ATIVA -> when {
                a == null -> definir("Indisponível", null)

                a.cancelamentoAgendado -> definir("Desfaça o cancelamento", null)

                plano.id == a.planoId ->
                    if (a.planoAgendadoId != null) {
                        definir("Manter este plano") { trocarDePlano(plano) }
                    } else {
                        aplicarEstilo("Plano atual", null, true)
                    }

                plano.id == a.planoAgendadoId ->
                    definir("Agendado para a renovação", null)

                plano.valorCentavos > a.valorCentavos -> {
                    val proRata = AssinaturaPolicy.proRataCentavos(
                        a.valorCentavos, plano.valorCentavos,
                        a.proximaCobranca, System.currentTimeMillis()
                    )
                    aviso.text = "Vale na hora. Você paga só a diferença proporcional " +
                            "ao tempo restante: ${AssinaturaUi.formatarValor(proRata)}."
                    aviso.visibility = View.VISIBLE
                    definir("Fazer upgrade") { trocarDePlano(plano) }
                }

                else -> {
                    aviso.text = "Vale a partir da próxima renovação, " +
                            "em ${AssinaturaUi.formatarData(a.proximaCobranca)}."
                    aviso.visibility = View.VISIBLE
                    definir("Mudar na próxima renovação") { trocarDePlano(plano) }
                }
            }

            Estado.EM_CARENCIA, Estado.SUSPENSA ->
                definir("Regularize o pagamento", null)
        }
    }

    // ---------------------------------------------------------
    // COBRANÇAS
    // ---------------------------------------------------------

    private fun renderizarCobrancas() {
        containerCobrancas.removeAllViews()

        val visiveis = pagamentos.filter { it.status != StatusPagamento.CANCELADO }
        txtSemCobrancas.visibility = if (visiveis.isEmpty()) View.VISIBLE else View.GONE

        visiveis.forEach { pagamento ->
            val item = layoutInflater.inflate(R.layout.item_cobranca, containerCobrancas, false)

            item.findViewById<TextView>(R.id.txtDescricaoCobranca).text = pagamento.descricao

            val (rotulo, cor) = when (pagamento.status) {
                StatusPagamento.PAGO -> Pair("Pago", Color.parseColor("#2CA144"))
                StatusPagamento.RECUSADO -> Pair("Recusado", Color.parseColor("#E53935"))
                else -> Pair("Pendente", Color.parseColor("#F6B12F"))
            }
            val chip = item.findViewById<TextView>(R.id.txtStatusCobranca)
            chip.text = rotulo
            chip.backgroundTintList = ColorStateList.valueOf(cor)

            val valor = AssinaturaUi.formatarValor(pagamento.valorCentavos)
            val pagoEm = pagamento.pagoEm
            item.findViewById<TextView>(R.id.txtDetalheCobranca).text =
                if (pagamento.status == StatusPagamento.PAGO && pagoEm != null) {
                    "Pago em ${AssinaturaUi.formatarData(pagoEm)} · $valor"
                } else {
                    "Vencimento: ${AssinaturaUi.formatarData(pagamento.vencimento)} · $valor"
                }

            val btnPagar = item.findViewById<Button>(R.id.btnPagarCobranca)
            if (pagamento.status == StatusPagamento.PENDENTE) {
                btnPagar.visibility = View.VISIBLE
                btnPagar.setOnClickListener { abrirPix(pagamento) }
            }

            containerCobrancas.addView(item)
        }
    }

    // ---------------------------------------------------------
    // AÇÕES
    // ---------------------------------------------------------

    private fun contratar(plano: Plano) {
        executar {
            val pagamento = repository.contratarPlano(motoristaId, plano)
            abrirPix(pagamento)
        }
    }

    private fun pagarCobrancaPendenteDaAssinatura() {
        val pendente = pagamentos.firstOrNull {
            it.status == StatusPagamento.PENDENTE && it.tipo == TipoPagamento.MENSALIDADE
        }
        if (pendente == null) {
            Toast.makeText(this, "Nenhuma cobrança pendente.", Toast.LENGTH_SHORT).show()
            return
        }
        abrirPix(pendente)
    }

    private fun trocarDePlano(plano: Plano) {
        executar {
            when (val resultado = repository.solicitarTrocaDePlano(motoristaId, plano)) {
                is AssinaturaRepository.ResultadoTroca.Aplicada ->
                    Toast.makeText(this, "Plano alterado.", Toast.LENGTH_SHORT).show()

                is AssinaturaRepository.ResultadoTroca.CobrancaCriada -> {
                    Toast.makeText(
                        this, "Pague a diferença para concluir o upgrade.", Toast.LENGTH_SHORT
                    ).show()
                    abrirPix(resultado.pagamento)
                }

                is AssinaturaRepository.ResultadoTroca.Agendada ->
                    Toast.makeText(
                        this,
                        "Mudança agendada para ${AssinaturaUi.formatarData(resultado.paraQuando)}.",
                        Toast.LENGTH_LONG
                    ).show()

                is AssinaturaRepository.ResultadoTroca.AgendamentoRemovido ->
                    Toast.makeText(this, "Mudança de plano cancelada.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmarCancelamento() {
        val a = assinatura ?: return
        val estado = AssinaturaPolicy.estado(a, System.currentTimeMillis())

        val mensagem = if (estado == Estado.ATIVA) {
            "Você continua com acesso até ${AssinaturaUi.formatarData(a.proximaCobranca)}. " +
                    "Depois dessa data, não será possível criar novas rotas nem adicionar " +
                    "passageiros. Os alunos que já estão nas suas rotas não são removidos."
        } else {
            "A assinatura será cancelada agora."
        }

        AlertDialog.Builder(this)
            .setTitle("Cancelar assinatura?")
            .setMessage(mensagem)
            .setPositiveButton("Cancelar assinatura") { _, _ ->
                executar {
                    val fim = repository.cancelarAssinatura(motoristaId)
                    Toast.makeText(
                        this,
                        if (fim != null) {
                            "Cancelamento agendado para ${AssinaturaUi.formatarData(fim)}."
                        } else {
                            "Assinatura cancelada."
                        },
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton("Voltar", null)
            .show()
    }

    private fun desfazerCancelamento() {
        executar {
            repository.desfazerCancelamento(motoristaId)
            Toast.makeText(this, "Cancelamento desfeito.", Toast.LENGTH_SHORT).show()
        }
    }

    /** Executa uma ação e mostra o erro (se houver) de forma amigável. */
    private fun executar(bloco: suspend () -> Unit) {
        lifecycleScope.launch {
            try {
                bloco()
            } catch (e: AssinaturaBloqueadaException) {
                AlertDialog.Builder(this@AssinaturaActivity)
                    .setMessage(e.message)
                    .setPositiveButton("OK", null)
                    .show()
            } catch (e: Exception) {
                Log.e(TAG, "Erro na operação de assinatura", e)
                Toast.makeText(
                    this@AssinaturaActivity, "Erro: ${e.message}", Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ---------------------------------------------------------
    // PIX (SIMULADO)
    // ---------------------------------------------------------

    private fun abrirPix(pagamento: Pagamento) {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_pagamento_pix, null)
        sheet.setContentView(view)

        (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)

        view.findViewById<TextView>(R.id.txtPixDescricao).text = pagamento.descricao
        view.findViewById<TextView>(R.id.txtPixValor).text =
            AssinaturaUi.formatarValor(pagamento.valorCentavos)

        val btnConfirmar = view.findViewById<Button>(R.id.btnConfirmarPix)
        btnConfirmar.setOnClickListener {
            btnConfirmar.isEnabled = false

            lifecycleScope.launch {
                try {
                    repository.confirmarPagamentoSimulado(pagamento)
                    sheet.dismiss()
                    Toast.makeText(
                        this@AssinaturaActivity, "Pagamento confirmado!", Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    btnConfirmar.isEnabled = true
                    Log.e(TAG, "Erro ao confirmar pagamento", e)
                    Toast.makeText(
                        this@AssinaturaActivity, "Erro: ${e.message}", Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        view.findViewById<Button>(R.id.btnCancelarPix).setOnClickListener { sheet.dismiss() }

        sheet.show()
    }

    private companion object {
        const val TAG = "AssinaturaActivity"
    }
}