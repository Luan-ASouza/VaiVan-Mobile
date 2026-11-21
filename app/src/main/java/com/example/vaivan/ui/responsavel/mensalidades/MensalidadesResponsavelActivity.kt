package com.example.vaivan.ui.responsavel.mensalidades

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
import com.example.vaivan.core.base.BaseActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.vaivan.R
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.repository.PassageiroRepository
import com.example.vaivan.ui.comum.foto.carregarFotoPassageiro
import com.example.vaivan.data.models.Contrato
import com.example.vaivan.data.models.Mensalidade
import com.example.vaivan.data.models.StatusContrato
import com.example.vaivan.data.models.StatusMensalidade
import com.example.vaivan.data.repository.MensalidadeRepository
import com.example.vaivan.domain.usecase.mensalidade.MensalidadePolicy
import com.example.vaivan.domain.usecase.mensalidade.MensalidadePolicy.SituacaoContrato
import com.example.vaivan.domain.usecase.mensalidade.MensalidadePolicy.SituacaoMensalidade
import com.example.vaivan.ui.comum.mensalidade.MensalidadeUi
import com.example.vaivan.ui.comum.tema.Folha
import com.example.vaivan.ui.comum.tema.TemaPerfil
import com.example.vaivan.ui.comum.tema.TomSelo
import com.example.vaivan.ui.comum.tema.aplicarSelo
import com.example.vaivan.ui.motorista.assinatura.AssinaturaUi
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Mensalidades que o responsável paga aos motoristas dos filhos.
 * Mostra o total em aberto e um cartão por filho (contrato), com o botão para pagar
 * a mensalidade mais antiga em aberto e o histórico de cada um.
 * O pagamento pelo app é SIMULADO, como na assinatura do motorista.
 */
class MensalidadesResponsavelActivity : BaseActivity() {

    private val repository = MensalidadeRepository()

    // Nome e foto dos alunos (cache no Room; ver carregarFotoPassageiro)
    private val passageiroRepository by lazy {
        PassageiroRepository(VaivanDatabase.getInstance(this).passageiroDao())
    }

    private var contratos: List<Contrato> = emptyList()
    private var mensalidades: List<Mensalidade> = emptyList()
    private var gerando = false

    /** Histórico aberto no momento, para atualizar em tempo real. */
    private var historicoAberto: Pair<String, View>? = null

    private lateinit var progress: View
    private lateinit var conteudo: View
    private lateinit var container: LinearLayout
    private lateinit var txtVazio: TextView

    /**
     * Vindo do cartão do filho ("Pagar e ativar a vaga"): id da reserva/contrato cuja
     * 1ª mensalidade deve abrir direto no PIX assim que estiver carregada.
     */
    private var contratoParaPagar: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mensalidades_responsavel)

        aplicarGapsDeSistema()
        findViewById<ImageButton>(R.id.btnVoltarMensalidades).setOnClickListener { finish() }

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            finish()
            return
        }

        contratoParaPagar = intent.getStringExtra(EXTRA_PAGAR_CONTRATO_ID)
        contratoParaPagar?.let { garantirCobrancaDaReserva(it) }

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
                    abrirPagamentoPedido()
                }
            }
        }
    }

    /**
     * Se o contrato/1ª mensalidade da reserva não foram criados no aceite (ex.: o
     * celular do motorista estava sem internet), o próprio responsável cria.
     */
    private fun garantirCobrancaDaReserva(solicitacaoId: String) {
        lifecycleScope.launch {
            try {
                repository.garantirContrato(solicitacaoId, inicioEm = System.currentTimeMillis())
            } catch (e: Exception) {
                Log.w(TAG, "Não foi possível criar a cobrança da reserva", e)
                Toast.makeText(
                    this@MensalidadesResponsavelActivity,
                    "Não foi possível carregar a cobrança agora. Verifique a internet e tente de novo.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /** Abre o PIX da 1ª mensalidade pedida, uma vez, quando ela aparecer na lista. */
    private fun abrirPagamentoPedido() {
        val contratoId = contratoParaPagar ?: return
        val primeira = mensalidades
            .filter { it.contratoId == contratoId && it.status == StatusMensalidade.PENDENTE }
            .minByOrNull { it.vencimento }
            ?: return // ainda sendo criada: tenta de novo na próxima atualização

        contratoParaPagar = null
        abrirPix(primeira)
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

    // =========================================================
    // TELA
    // =========================================================

    private fun renderizar() {
        progress.visibility = View.GONE
        conteudo.visibility = View.VISIBLE

        val agora = System.currentTimeMillis()
        val porContrato = mensalidades.groupBy { it.contratoId }

        // RESUMO (cartão escuro)
        val emAberto = mensalidades
            .filter { it.status == StatusMensalidade.PENDENTE }
            .sortedBy { it.vencimento }
        val atrasadas = emAberto.count { MensalidadePolicy.situacao(it, agora) == SituacaoMensalidade.ATRASADA }

        findViewById<TextView>(R.id.txtTotalAberto).text =
            AssinaturaUi.formatarValor(emAberto.sumOf { it.valorCentavos })

        findViewById<TextView>(R.id.txtResumoAberto).apply {
            text = when {
                emAberto.isEmpty() -> "Nenhuma mensalidade em aberto"
                atrasadas == 1 -> "1 mensalidade em atraso"
                atrasadas > 1 -> "$atrasadas mensalidades em atraso"
                else -> "Próximo vencimento: ${AssinaturaUi.formatarData(emAberto.first().vencimento)}"
            }
            // Sobre o cartão laranja: atraso vira uma pílula vermelha; senão, a translúcida
            if (atrasadas > 0) {
                setBackgroundResource(R.drawable.bg_pilula_branca)
                backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.status_danger))
            } else {
                setBackgroundResource(R.drawable.bg_chip_hero)
                backgroundTintList = null
            }
            setTextColor(Color.WHITE)
        }

        // UM CARTÃO POR FILHO (encerrados só enquanto ainda devem algo)
        val itens = contratos
            .map { it to MensalidadePolicy.situacaoDoContrato(it, porContrato[it.id].orEmpty(), agora) }
            .filter { (c, s) -> c.status == StatusContrato.ATIVO || s != SituacaoContrato.ENCERRADO }
            .sortedWith(compareBy({ ordem(it.second) }, { it.first.nomePassageiro.lowercase() }))

        container.removeAllViews()
        itens.forEach { (contrato, situacao) ->
            container.addView(montarCard(contrato, situacao, porContrato[contrato.id].orEmpty(), agora))
        }

        txtVazio.visibility = if (itens.isEmpty()) View.VISIBLE else View.GONE
        txtVazio.text = "Quando um motorista aceitar seu filho em uma rota, a mensalidade aparece aqui."

        historicoAberto?.let { (contratoId, view) ->
            contratos.firstOrNull { it.id == contratoId }?.let { preencherHistorico(view, it) }
        }
    }

    private fun ordem(s: SituacaoContrato) = when (s) {
        SituacaoContrato.INADIMPLENTE -> 0
        SituacaoContrato.ATRASADO -> 1
        SituacaoContrato.RESERVADO -> 2
        SituacaoContrato.EM_DIA -> 3
        SituacaoContrato.ENCERRADO -> 4
    }

    private fun montarCard(
        contrato: Contrato,
        situacao: SituacaoContrato,
        doContrato: List<Mensalidade>,
        agora: Long
    ): View {
        val card = layoutInflater.inflate(R.layout.item_mensalidade_responsavel, container, false)

        val nome = contrato.nomePassageiro.ifBlank { "Seu filho" }
        card.findViewById<TextView>(R.id.txtNomeFilho).text = nome
        carregarFotoPassageiro(this, card.findViewById(R.id.imgFotoFilho), contrato.passageiroId, passageiroRepository)
        card.findViewById<TextView>(R.id.txtRotaFilho).apply {
            text = contrato.nomeRota
            visibility = if (contrato.nomeRota.isBlank()) View.GONE else View.VISIBLE
        }
        card.findViewById<TextView>(R.id.txtVencimentoFilho).text = "Todo dia ${contrato.diaVencimento}"

        val badge = card.findViewById<TextView>(R.id.txtSituacaoFilho)
        val txtValor = card.findViewById<TextView>(R.id.txtValorFilho)
        val txtDetalhe = card.findViewById<TextView>(R.id.txtDetalheFilho)
        val btnPagar = card.findViewById<MaterialButton>(R.id.btnPagarMensalidade)

        card.findViewById<View>(R.id.btnHistoricoFilho).setOnClickListener { abrirHistorico(contrato) }

        // O motorista ainda não definiu o valor
        if (contrato.valorMensalCentavos <= 0) {
            pintarBadge(badge, "Aguardando", TomSelo.NEUTRO)
            txtValor.text = "A definir"
            txtDetalhe.text = "O motorista ainda não definiu o valor da mensalidade."
            btnPagar.visibility = View.GONE
            return card
        }

        val (rotulo, cor) = MensalidadeUi.rotuloDoContrato(situacao)
        pintarBadge(badge, rotulo, cor)
        txtValor.text = AssinaturaUi.formatarValor(contrato.valorMensalCentavos)

        val aPagar = MensalidadePolicy.proximaEmAberto(doContrato)

        if (aPagar == null) {
            val proximo = MensalidadePolicy.proximoVencimento(contrato, doContrato, agora)
            txtDetalhe.text = if (proximo != null) {
                "Tudo pago. Próxima cobrança em ${AssinaturaUi.formatarData(proximo)}."
            } else {
                "Tudo pago."
            }
            pintarDetalhe(txtDetalhe, FAIXA_VERDE, R.color.green)
            btnPagar.visibility = View.GONE
            return card
        }

        val mes = MensalidadeUi.nomeDoMes(aPagar.competencia)
        val vencidas = doContrato.count { MensalidadePolicy.situacao(it, agora) == SituacaoMensalidade.ATRASADA }

        txtDetalhe.text = when (MensalidadePolicy.situacao(aPagar, agora)) {
            SituacaoMensalidade.ATRASADA -> {
                val dias = MensalidadePolicy.diasDeAtraso(aPagar, agora)
                val extra = if (vencidas > 1) " ($vencidas meses em aberto)" else ""
                pintarDetalhe(txtDetalhe, FAIXA_VERMELHA, R.color.red)
                "$mes em atraso há $dias ${if (dias == 1L) "dia" else "dias"}$extra"
            }
            SituacaoMensalidade.VENCE_HOJE -> {
                pintarDetalhe(txtDetalhe, FAIXA_LARANJA, R.color.dark_gray)
                "$mes vence hoje"
            }
            else -> "$mes vence em ${AssinaturaUi.formatarData(aPagar.vencimento)}"
        }

        // Vaga reservada: a 1ª mensalidade é o que ativa a vaga
        if (contrato.status == StatusContrato.AGUARDANDO_PAGAMENTO) {
            pintarDetalhe(txtDetalhe, FAIXA_LARANJA, R.color.dark_gray)
            txtDetalhe.text = "Vaga reservada! Pague a 1ª mensalidade (proporcional aos dias que faltam no mês) " +
                "até ${AssinaturaUi.formatarData(aPagar.vencimento)} para ativar a vaga."
        }

        // Sempre paga a mais antiga primeiro
        btnPagar.text = "Pagar ${AssinaturaUi.formatarValor(aPagar.valorCentavos)}"
        btnPagar.setOnClickListener { abrirPix(aPagar) }
        return card
    }

    private fun pintarDetalhe(txt: TextView, fundo: Int, corTexto: Int) {
        txt.backgroundTintList = ColorStateList.valueOf(fundo)
        txt.setTextColor(ContextCompat.getColor(this, corTexto))
    }

    private fun pintarBadge(badge: TextView, texto: String, tom: TomSelo) {
        badge.aplicarSelo(texto, tom)
    }

    // =========================================================
    // HISTÓRICO (mesma janela do motorista, sem "alterar valor")
    // =========================================================

    private fun abrirHistorico(contrato: Contrato) {
        val (dialog, view) = Folha.criar(this, R.layout.bottom_sheet_historico_mensalidades)

        // Alterar valor ou vencimento é só do motorista
        view.findViewById<View>(R.id.btnReajustarContrato).visibility = View.GONE

        historicoAberto = contrato.id to view
        dialog.setOnDismissListener { historicoAberto = null }

        preencherHistorico(view, contrato)
        dialog.show()
    }

    private fun preencherHistorico(view: View, contrato: Contrato) {
        val agora = System.currentTimeMillis()

        view.findViewById<TextView>(R.id.txtTituloHistorico).text =
            contrato.nomePassageiro.ifBlank { "Seu filho" }
        carregarFotoPassageiro(this, view.findViewById(R.id.imgFotoHistorico), contrato.passageiroId, passageiroRepository)
        view.findViewById<TextView>(R.id.txtSubtituloHistorico).text =
            if (contrato.valorMensalCentavos > 0) {
                "${AssinaturaUi.formatarValor(contrato.valorMensalCentavos)} por mês, vence todo dia ${contrato.diaVencimento}"
            } else {
                "Valor da mensalidade ainda não definido"
            }

        val lista = view.findViewById<LinearLayout>(R.id.containerHistorico)
        lista.removeAllViews()

        val doContrato = mensalidades
            .filter { it.contratoId == contrato.id && it.status != StatusMensalidade.CANCELADA }
            .sortedByDescending { it.competencia }

        if (doContrato.isEmpty()) {
            lista.addView(TextView(this).apply {
                text = "Nenhuma mensalidade ainda."
                setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
                gravity = android.view.Gravity.CENTER
                setPadding(0, 24, 0, 24)
            })
            return
        }

        doContrato.forEach { m ->
            val linha = layoutInflater.inflate(R.layout.item_mensalidade_historico, lista, false)

            linha.findViewById<TextView>(R.id.txtMesHistorico).text = MensalidadeUi.nomeDoMes(m.competencia)

            val (rotulo, tom) = MensalidadeUi.rotuloDaMensalidade(m, agora)
            linha.findViewById<TextView>(R.id.txtStatusHistorico).aplicarSelo(rotulo, tom)

            linha.findViewById<TextView>(R.id.txtDetalheHistorico).text =
                "${AssinaturaUi.formatarValor(m.valorCentavos)}. " +
                    MensalidadeUi.detalheDaMensalidade(m, paraMotorista = false)

            linha.findViewById<TextView>(R.id.btnAcaoHistorico).apply {
                if (m.status == StatusMensalidade.PENDENTE) {
                    visibility = View.VISIBLE
                    text = "Pagar com PIX"
                    setBackgroundResource(TemaPerfil.RESPONSAVEL.botao)
                    setOnClickListener { abrirPix(m) }
                } else {
                    visibility = View.GONE
                }
            }

            lista.addView(linha)
        }
    }

    // =========================================================
    // PAGAMENTO (PIX SIMULADO)
    // =========================================================

    // Reaproveita o mesmo bottom sheet de PIX simulado da assinatura do motorista.
    private fun abrirPix(m: Mensalidade) {
        // Layout já vem na cor do responsável (laranja)
        val (sheet, view) = Folha.criar(this, R.layout.bottom_sheet_pagamento_pix)

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
                    Toast.makeText(this@MensalidadesResponsavelActivity, "Pagamento confirmado.", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    btnConfirmar.isEnabled = true
                    Log.e(TAG, "Erro ao pagar mensalidade", e)
                    Toast.makeText(this@MensalidadesResponsavelActivity, "Não foi possível pagar: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        view.findViewById<Button>(R.id.btnCancelarPix).setOnClickListener { sheet.dismiss() }

        sheet.show()
    }

    companion object {
        /** Id da reserva (= id do contrato) cuja 1ª mensalidade abre direto no PIX. */
        const val EXTRA_PAGAR_CONTRATO_ID = "pagarContratoId"

        private const val TAG = "MensalidadesResponsavel"

        // Fundos claros da faixa de situação no cartão do filho
        private const val FAIXA_VERDE = 0xFFE8F5E9.toInt()
        private const val FAIXA_VERMELHA = 0xFFFDECEA.toInt()
        private const val FAIXA_LARANJA = 0xFFFFF3E0.toInt()

    }
}
