package com.example.vaivan.ui.motorista.assinatura

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
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
import com.google.android.material.imageview.ShapeableImageView
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.repository.PassageiroRepository
import com.example.vaivan.ui.comum.foto.carregarFotoPassageiro
import com.example.vaivan.data.models.Contrato
import com.example.vaivan.data.models.FormaPagamentoMensalidade
import com.example.vaivan.data.models.Mensalidade
import com.example.vaivan.data.models.RegistradoPor
import com.example.vaivan.data.models.StatusContrato
import com.example.vaivan.data.models.StatusMensalidade
import com.example.vaivan.data.repository.AssinaturaRepository
import com.example.vaivan.data.repository.MensalidadeRepository
import com.example.vaivan.domain.usecase.assinatura.AssinaturaPolicy
import com.example.vaivan.domain.usecase.mensalidade.MensalidadePolicy
import com.example.vaivan.domain.usecase.mensalidade.MensalidadePolicy.SituacaoContrato
import com.example.vaivan.domain.usecase.mensalidade.MensalidadePolicy.SituacaoMensalidade
import com.example.vaivan.ui.comum.mensalidade.MensalidadeUi
import com.example.vaivan.core.util.ConfirmacaoExclusaoHelper
import com.example.vaivan.ui.comum.tema.Folha
import com.example.vaivan.ui.comum.tema.TemaPerfil
import com.example.vaivan.ui.comum.tema.TomSelo
import com.example.vaivan.ui.comum.tema.aplicarSelo
import com.example.vaivan.ui.comum.tema.marcarOpcao
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Gestão das mensalidades que os responsáveis pagam ao motorista (RF15).
 *
 * Mostra, por passageiro: valor, situação (em dia / atrasado / inadimplente), próxima
 * cobrança e histórico. O motorista registra recebimentos feitos fora do app (PIX direto,
 * dinheiro...) e pode reajustar o valor de cada contrato.
 *
 * Só abre com a assinatura do motorista em dia; sem ela, mostra o bloqueio.
 */
class PagamentosMotoristaActivity : BaseActivity() {

    private enum class Filtro { TODOS, EM_DIA, PENDENTES }

    private val repository = MensalidadeRepository()

    // Nome e foto dos alunos (cache no Room; ver carregarFotoPassageiro)
    private val passageiroRepository by lazy {
        PassageiroRepository(VaivanDatabase.getInstance(this).passageiroDao())
    }

    private var contratos: List<Contrato> = emptyList()
    private var mensalidades: List<Mensalidade> = emptyList()
    private var filtro = Filtro.TODOS
    private var dadosCarregados = false
    private var liberado = false
    private var gerando = false

    /** Histórico aberto no momento, para atualizar em tempo real. */
    private var historicoAberto: Pair<String, View>? = null

    private lateinit var progress: View
    private lateinit var conteudo: View
    private lateinit var bloqueio: View
    private lateinit var containerContratos: LinearLayout
    private lateinit var txtVazio: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pagamentos_motorista)

        aplicarGapsDeSistema()

        findViewById<ImageButton>(R.id.btnVoltarPagamentos).setOnClickListener { finish() }

        findViewById<Button>(R.id.btnIrParaAssinatura).setOnClickListener {
            startActivity(Intent(this, AssinaturaActivity::class.java))
            finish()
        }

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            finish()
            return
        }

        progress = findViewById(R.id.progressPagamentos)
        conteudo = findViewById(R.id.conteudoPagamentos)
        bloqueio = findViewById(R.id.blocoBloqueado)
        containerContratos = findViewById(R.id.containerContratos)
        txtVazio = findViewById(R.id.txtVazioPagamentos)

        findViewById<MaterialButtonToggleGroup>(R.id.grupoFiltroPagamentos)
            .addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                filtro = when (checkedId) {
                    R.id.btnFiltroEmDia -> Filtro.EM_DIA
                    R.id.btnFiltroPendentes -> Filtro.PENDENTES
                    else -> Filtro.TODOS
                }
                renderizar()
            }

        // Passageiros aceitos antes desta funcionalidade ainda não têm contrato.
        lifecycleScope.launch {
            try {
                repository.sincronizarContratosDoMotorista(uid)
            } catch (e: Exception) {
                Log.w(TAG, "Não foi possível sincronizar contratos", e)
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    AssinaturaRepository().observarAssinatura(uid).collect { assinatura ->
                        liberado = AssinaturaPolicy.podeVerPagamentos(
                            assinatura, System.currentTimeMillis()
                        )
                        atualizarVisibilidade()
                    }
                }

                launch {
                    combine(
                        repository.observarContratosDoMotorista(uid),
                        repository.observarMensalidadesDoMotorista(uid)
                    ) { c, m -> c to m }.collect { (c, m) ->
                        contratos = c
                        mensalidades = m
                        dadosCarregados = true
                        atualizarVisibilidade()
                        renderizar()
                        gerarMensalidadesFaltando()
                    }
                }
            }
        }
    }

    private fun atualizarVisibilidade() {
        bloqueio.visibility = if (!liberado) View.VISIBLE else View.GONE
        conteudo.visibility = if (liberado && dadosCarregados) View.VISIBLE else View.GONE
        progress.visibility = if (liberado && !dadosCarregados) View.VISIBLE else View.GONE
    }

    /** Cria as cobranças do mês que ainda não existem (idempotente). */
    private fun gerarMensalidadesFaltando() {
        if (gerando) return
        gerando = true
        val ativos = contratos
        val existentes = mensalidades
        lifecycleScope.launch {
            try {
                repository.gerarMensalidadesFaltando(ativos, existentes)
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao gerar mensalidades", e)
            } finally {
                gerando = false
            }
        }
    }

    // =========================================================
    // LISTA
    // =========================================================

    private fun renderizar() {
        val agora = System.currentTimeMillis()
        val porContrato = mensalidades.groupBy { it.contratoId }

        // Encerrados só aparecem enquanto ainda devem alguma coisa.
        val itens = contratos
            .map { it to MensalidadePolicy.situacaoDoContrato(it, porContrato[it.id].orEmpty(), agora) }
            .filter { (c, s) -> c.status == StatusContrato.ATIVO || s != SituacaoContrato.ENCERRADO }

        // RESUMO
        val resumo = MensalidadePolicy.resumo(itens.map { it.first }, mensalidades, agora)
        findViewById<TextView>(R.id.txtLabelRecebido).text = "Recebido em ${MensalidadeUi.mesAtual(agora)}"
        findViewById<TextView>(R.id.txtRecebidoMes).text = AssinaturaUi.formatarValor(resumo.recebidoNoMesCentavos)
        findViewById<TextView>(R.id.txtAReceberMes).text = AssinaturaUi.formatarValor(resumo.aReceberNoMesCentavos)
        findViewById<TextView>(R.id.txtEmAtraso).text = AssinaturaUi.formatarValor(resumo.emAtrasoCentavos)
        findViewById<TextView>(R.id.txtQtdEmDia).text = "${resumo.qtdEmDia} em dia"
        findViewById<TextView>(R.id.txtQtdAtrasados).text =
            "${resumo.qtdAtrasados} ${if (resumo.qtdAtrasados == 1) "atrasado" else "atrasados"}"
        findViewById<TextView>(R.id.txtQtdInadimplentes).text =
            "${resumo.qtdInadimplentes} ${if (resumo.qtdInadimplentes == 1) "inadimplente" else "inadimplentes"}"

        // FILTRO + ORDEM (quem deve aparece primeiro)
        val visiveis = itens
            .filter { (_, s) ->
                when (filtro) {
                    Filtro.TODOS -> true
                    Filtro.EM_DIA -> s == SituacaoContrato.EM_DIA
                    Filtro.PENDENTES -> s == SituacaoContrato.ATRASADO || s == SituacaoContrato.INADIMPLENTE
                }
            }
            .sortedWith(compareBy({ ordem(it.second) }, { it.first.nomePassageiro.lowercase() }))

        containerContratos.removeAllViews()
        visiveis.forEach { (contrato, situacao) ->
            containerContratos.addView(
                montarCard(contrato, situacao, porContrato[contrato.id].orEmpty(), agora)
            )
        }

        txtVazio.visibility = if (visiveis.isEmpty()) View.VISIBLE else View.GONE
        txtVazio.text = when {
            itens.isEmpty() -> "Você ainda não tem alunos. Quando aceitar uma solicitação, " +
                "a mensalidade do aluno aparece aqui."
            filtro == Filtro.PENDENTES -> "Nenhum pagamento pendente. Está tudo em dia."
            else -> "Nenhum aluno neste filtro."
        }

        // Histórico aberto acompanha as mudanças em tempo real
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
        val card = layoutInflater.inflate(R.layout.item_contrato_mensalidade, containerContratos, false)

        val nome = contrato.nomePassageiro.ifBlank { "Aluno" }
        card.findViewById<TextView>(R.id.txtNomePassageiroContrato).text = nome
        carregarFotoPassageiro(this, card.findViewById(R.id.imgFotoContrato), contrato.passageiroId, passageiroRepository)

        card.findViewById<TextView>(R.id.txtResponsavelContrato).apply {
            text = "Responsável: ${contrato.nomeResponsavel}"
            visibility = if (contrato.nomeResponsavel.isBlank()) View.GONE else View.VISIBLE
        }
        card.findViewById<TextView>(R.id.txtRotaContrato).apply {
            text = contrato.nomeRota
            visibility = if (contrato.nomeRota.isBlank()) View.GONE else View.VISIBLE
        }

        card.findViewById<TextView>(R.id.txtVencimentoContrato).text =
            "Todo dia ${contrato.diaVencimento}"

        val badge = card.findViewById<TextView>(R.id.txtSituacaoContrato)
        val txtValor = card.findViewById<TextView>(R.id.txtValorContrato)
        val txtDetalhe = card.findViewById<TextView>(R.id.txtDetalheContrato)
        val btnAcao = card.findViewById<MaterialButton>(R.id.btnAcaoContrato)

        card.findViewById<View>(R.id.btnHistoricoContrato).setOnClickListener { abrirHistorico(contrato) }

        // Rota criada antes do campo de preço: o motorista precisa definir o valor.
        if (contrato.valorMensalCentavos <= 0) {
            pintarBadge(badge, "Sem valor", TomSelo.NEUTRO)
            txtValor.text = "Não definido"
            txtDetalhe.text = "Defina o valor para começar a acompanhar os pagamentos."
            btnAcao.text = "Definir valor"
            btnAcao.setOnClickListener { abrirReajuste(contrato) }
            return card
        }

        val (rotulo, cor) = MensalidadeUi.rotuloDoContrato(situacao)
        pintarBadge(badge, rotulo, cor)

        txtValor.text = AssinaturaUi.formatarValor(contrato.valorMensalCentavos)

        val emAberto = MensalidadePolicy.proximaEmAberto(doContrato)

        if (emAberto == null) {
            val proximo = MensalidadePolicy.proximoVencimento(contrato, doContrato, agora)
            txtDetalhe.text = if (proximo != null) {
                "Tudo pago. Próxima cobrança em ${AssinaturaUi.formatarData(proximo)}."
            } else {
                "Tudo pago."
            }
            pintarDetalhe(txtDetalhe, FAIXA_VERDE, R.color.green)
            btnAcao.visibility = View.GONE
            return card
        }

        val mes = MensalidadeUi.nomeDoMes(emAberto.competencia)
        val vencidas = doContrato.count { MensalidadePolicy.situacao(it, agora) == SituacaoMensalidade.ATRASADA }

        txtDetalhe.text = when (MensalidadePolicy.situacao(emAberto, agora)) {
            SituacaoMensalidade.ATRASADA -> {
                val dias = MensalidadePolicy.diasDeAtraso(emAberto, agora)
                val extra = if (vencidas > 1) " ($vencidas meses em aberto)" else ""
                pintarDetalhe(txtDetalhe, FAIXA_VERMELHA, R.color.red)
                "$mes em atraso há $dias ${if (dias == 1L) "dia" else "dias"}$extra"
            }
            SituacaoMensalidade.VENCE_HOJE -> {
                pintarDetalhe(txtDetalhe, FAIXA_LARANJA, R.color.dark_gray)
                "$mes vence hoje"
            }
            else -> "Próxima cobrança: ${AssinaturaUi.formatarData(emAberto.vencimento)} ($mes)"
        }

        if (contrato.status == StatusContrato.AGUARDANDO_PAGAMENTO) {
            pintarDetalhe(txtDetalhe, FAIXA_LARANJA, R.color.dark_gray)
            txtDetalhe.text = "Vaga reservada. O aluno entra na rota quando a 1ª mensalidade " +
                "(${AssinaturaUi.formatarValor(emAberto.valorCentavos)}) for paga, até ${AssinaturaUi.formatarData(emAberto.vencimento)}."
        }

        btnAcao.text = "Registrar recebimento"
        btnAcao.setOnClickListener { confirmarRecebimento(emAberto) }
        return card
    }

    /** Pinta a faixa de situação do cartão (fundo claro + texto da cor da situação). */
    private fun pintarDetalhe(txt: TextView, fundo: Int, corTexto: Int) {
        txt.backgroundTintList = ColorStateList.valueOf(fundo)
        txt.setTextColor(ContextCompat.getColor(this, corTexto))
    }

    private fun pintarBadge(badge: TextView, texto: String, tom: TomSelo) {
        badge.aplicarSelo(texto, tom)
    }

    // =========================================================
    // AÇÕES
    // =========================================================

    private fun confirmarRecebimento(mensalidade: Mensalidade) {
        val (sheet, view) = Folha.criar(this, R.layout.bottom_sheet_registrar_recebimento)

        view.findViewById<TextView>(R.id.txtDescricaoRecebimento).text =
            listOf(MensalidadeUi.nomeDoMes(mensalidade.competencia), mensalidade.nomePassageiro)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
        view.findViewById<TextView>(R.id.txtValorRecebimento).text =
            AssinaturaUi.formatarValor(mensalidade.valorCentavos)

        // Mesma ordem de FormaPagamentoMensalidade.manuais (PIX, dinheiro, transferência, outro)
        val formas = FormaPagamentoMensalidade.manuais
        val opcoes = listOf(
            R.id.opcaoFormaPix, R.id.opcaoFormaDinheiro,
            R.id.opcaoFormaTransferencia, R.id.opcaoFormaOutro
        ).map { view.findViewById<TextView>(it) }

        var escolhida = 0
        marcarOpcao(opcoes, opcoes[escolhida], TemaPerfil.MOTORISTA)
        opcoes.forEachIndexed { indice, opcao ->
            opcao.setOnClickListener {
                escolhida = indice
                marcarOpcao(opcoes, opcao, TemaPerfil.MOTORISTA)
            }
        }

        view.findViewById<View>(R.id.btnConfirmarRecebimento).setOnClickListener {
            sheet.dismiss()
            executar("Recebimento registrado.") {
                repository.registrarRecebimento(mensalidade.id, formas[escolhida])
            }
        }
        view.findViewById<View>(R.id.btnCancelarRecebimento).setOnClickListener { sheet.dismiss() }

        sheet.show()
    }

    private fun confirmarDesfazer(mensalidade: Mensalidade) {
        ConfirmacaoExclusaoHelper.confirmar(
            contexto = this,
            titulo = "Desfazer recebimento?",
            mensagem = "${MensalidadeUi.nomeDoMes(mensalidade.competencia)} voltará a ficar em aberto. " +
                "Use isso apenas se o recebimento foi registrado por engano.",
            textoConfirmar = "Desfazer recebimento"
        ) {
            executar("Recebimento desfeito.") { repository.desfazerRecebimento(mensalidade.id) }
        }
    }

    private fun abrirReajuste(contrato: Contrato) {
        val (sheet, view) = Folha.criar(this, R.layout.bottom_sheet_reajuste_mensalidade)

        val edtValor = view.findViewById<EditText>(R.id.edtValorReajuste)
        val edtDia = view.findViewById<EditText>(R.id.edtDiaReajuste)
        val caixaValor = view.findViewById<View>(R.id.caixaValorReajuste)
        val caixaDia = view.findViewById<View>(R.id.caixaDiaReajuste)
        val txtErro = view.findViewById<TextView>(R.id.txtErroReajuste)

        view.findViewById<TextView>(R.id.txtTituloReajuste).text =
            contrato.nomePassageiro.takeIf { it.isNotBlank() }?.let { "Mensalidade de $it" } ?: "Mensalidade"

        if (contrato.valorMensalCentavos > 0) {
            edtValor.setText(MensalidadePolicy.centavosParaTexto(contrato.valorMensalCentavos))
        }
        edtDia.setText(contrato.diaVencimento.toString())

        fun mostrarErro(mensagem: String?, campoComErro: View?) {
            txtErro.text = mensagem
            txtErro.visibility = if (mensagem == null) View.GONE else View.VISIBLE
            listOf(caixaValor, caixaDia).forEach { caixa ->
                caixa.setBackgroundResource(
                    if (caixa === campoComErro) R.drawable.bg_input_white_red else R.drawable.bg_input_white
                )
            }
        }
        edtValor.doAfterTextChanged { mostrarErro(null, null) }
        edtDia.doAfterTextChanged { mostrarErro(null, null) }

        view.findViewById<View>(R.id.btnSalvarReajuste).setOnClickListener {
            val valor = MensalidadePolicy.textoParaCentavos(edtValor.text.toString())
            val dia = edtDia.text.toString().trim().toIntOrNull()
            when {
                valor == null || valor <= 0 -> mostrarErro("*Informe um valor válido.", caixaValor)
                dia == null || dia !in 1..28 -> mostrarErro("*O dia deve ser entre 1 e 28.", caixaDia)
                else -> {
                    sheet.dismiss()
                    executar("Mensalidade atualizada.") {
                        repository.reajustarContrato(contrato.id, valor, dia)
                    }
                }
            }
        }
        view.findViewById<View>(R.id.btnCancelarReajuste).setOnClickListener { sheet.dismiss() }

        sheet.show()
    }

    private fun executar(sucesso: String, acao: suspend () -> Unit) {
        lifecycleScope.launch {
            try {
                acao()
                Toast.makeText(this@PagamentosMotoristaActivity, sucesso, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Falha na operação", e)
                Toast.makeText(
                    this@PagamentosMotoristaActivity,
                    e.message ?: "Não foi possível concluir. Tente novamente.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // =========================================================
    // HISTÓRICO
    // =========================================================

    private fun abrirHistorico(contrato: Contrato) {
        val (dialog, view) = Folha.criar(this, R.layout.bottom_sheet_historico_mensalidades)
        // No motorista, o anel da foto é cinza (o layout vem laranja)
        view.findViewById<ShapeableImageView>(R.id.imgFotoHistorico).strokeColor =
            ColorStateList.valueOf(ContextCompat.getColor(this, R.color.rota_vagas_fundo))

        view.findViewById<View>(R.id.btnReajustarContrato).setOnClickListener {
            abrirReajuste(contratos.firstOrNull { it.id == contrato.id } ?: contrato)
        }

        historicoAberto = contrato.id to view
        dialog.setOnDismissListener { historicoAberto = null }

        preencherHistorico(view, contrato)
        dialog.show()
    }

    private fun preencherHistorico(view: View, contrato: Contrato) {
        val agora = System.currentTimeMillis()

        view.findViewById<TextView>(R.id.txtTituloHistorico).text =
            contrato.nomePassageiro.ifBlank { "Passageiro" }
        carregarFotoPassageiro(this, view.findViewById(R.id.imgFotoHistorico), contrato.passageiroId, passageiroRepository)
        view.findViewById<TextView>(R.id.txtSubtituloHistorico).text =
            if (contrato.valorMensalCentavos > 0) {
                "${AssinaturaUi.formatarValor(contrato.valorMensalCentavos)} / mês · vence dia ${contrato.diaVencimento}"
            } else {
                "Valor da mensalidade não definido"
            }

        val container = view.findViewById<LinearLayout>(R.id.containerHistorico)
        container.removeAllViews()

        val lista = mensalidades
            .filter { it.contratoId == contrato.id }
            .sortedByDescending { it.competencia }

        if (lista.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "Nenhuma mensalidade gerada ainda."
                setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
                gravity = android.view.Gravity.CENTER
                setPadding(0, 24, 0, 24)
            })
            return
        }

        lista.forEach { m ->
            val linha = layoutInflater.inflate(R.layout.item_mensalidade_historico, container, false)

            linha.findViewById<TextView>(R.id.txtMesHistorico).text = MensalidadeUi.nomeDoMes(m.competencia)

            val (rotulo, tom) = MensalidadeUi.rotuloDaMensalidade(m, agora)
            linha.findViewById<TextView>(R.id.txtStatusHistorico).aplicarSelo(rotulo, tom)

            linha.findViewById<TextView>(R.id.txtDetalheHistorico).text =
                "${AssinaturaUi.formatarValor(m.valorCentavos)} · " +
                    MensalidadeUi.detalheDaMensalidade(m, paraMotorista = true)

            val acao = linha.findViewById<TextView>(R.id.btnAcaoHistorico)
            when {
                m.status == StatusMensalidade.PENDENTE -> {
                    acao.visibility = View.VISIBLE
                    acao.text = "Registrar recebimento"
                    acao.setOnClickListener { confirmarRecebimento(m) }
                }
                m.status == StatusMensalidade.PAGA && m.registradoPor == RegistradoPor.MOTORISTA -> {
                    acao.visibility = View.VISIBLE
                    acao.text = "Desfazer recebimento"
                    acao.setBackgroundResource(R.drawable.bg_botao_contorno_escuro)
                    acao.setTextColor(ContextCompat.getColor(this, R.color.rota_destaque))
                    acao.setOnClickListener { confirmarDesfazer(m) }
                }
                else -> acao.visibility = View.GONE
            }

            container.addView(linha)
        }
    }

    companion object {
        private const val TAG = "PagamentosMotorista"

        // Fundos claros da faixa de situação no cartão do aluno
        private const val FAIXA_VERDE = 0xFFE8F5E9.toInt()
        private const val FAIXA_VERMELHA = 0xFFFDECEA.toInt()
        private const val FAIXA_LARANJA = 0xFFFFF3E0.toInt()
    }
}
