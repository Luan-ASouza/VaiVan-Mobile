package com.example.vaivan.ui.responsavel.rotas

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.vaivan.R
import com.example.vaivan.core.util.SystemBarUtils.applyTopAndBottomGaps
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.entities.SolicitacaoInclusaoEntity
import com.example.vaivan.data.models.RotaCompativel
import com.example.vaivan.data.remote.routes.GoogleRoutesClient
import com.example.vaivan.data.repository.RotaRepository
import com.example.vaivan.data.repository.SolicitacaoInclusaoRepository
import com.example.vaivan.ui.motorista.rotas.VisualizarRotaActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

/**
 * Tela de resultados da pesquisa de vans.
 * Recebe os filtros da tela de pesquisa, refaz a busca e mostra um card por van.
 */
class ResultadosRotasActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EMBARQUE_ENDERECO = "embarqueEndereco"
        const val EXTRA_EMBARQUE_LAT = "embarqueLat"
        const val EXTRA_EMBARQUE_LNG = "embarqueLng"
        const val EXTRA_DESTINO_ENDERECO = "destinoEndereco"
        const val EXTRA_DESTINO_LAT = "destinoLat"
        const val EXTRA_DESTINO_LNG = "destinoLng"
        const val EXTRA_TURNO = "turno"
        const val EXTRA_DIAS = "dias"
        const val EXTRA_HORARIO = "horario"
        const val EXTRA_PASSAGEIRO_ID = "passageiroId"
        const val EXTRA_NOME_PASSAGEIRO = "nomePassageiro"
        const val EXTRA_LOCAL_ID = "localId"

        const val EXTRA_MODALIDADE = "modalidade"

        private val ORDEM_DIAS = listOf("SEG", "TER", "QUA", "QUI", "SEX", "SAB", "DOM")
        private val ROTULO_DIAS = mapOf(
            "SEG" to "Seg", "TER" to "Ter", "QUA" to "Qua", "QUI" to "Qui",
            "SEX" to "Sex", "SAB" to "Sáb", "DOM" to "Dom"
        )
    }

    private lateinit var containerResultados: LinearLayout
    private lateinit var progressResultados: ProgressBar
    private lateinit var txtSemResultados: TextView
    private lateinit var txtContagemResultados: TextView

    // Filtros recebidos
    private var embarqueEndereco = ""
    private var embarqueLat = 0.0
    private var embarqueLng = 0.0
    private var destinoEndereco = ""
    private var destinoLat = 0.0
    private var destinoLng = 0.0
    private var turno: String? = null
    private var dias: Set<String> = emptySet()
    private var horarioDesejado = ""
    private var passageiroId = ""
    private var nomePassageiro = ""
    private var localId: String? = null
    private var modalidade: String = "IDA"

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    private val db by lazy { VaivanDatabase.getInstance(applicationContext) }

    private val rotaRepository by lazy {
        RotaRepository(
            rotaDao = db.rotaDao(),
            paradaRotaDao = db.paradaRotaDao(),
            routesClient = GoogleRoutesClient(this)
        )
    }

    private val solicitacaoRepository by lazy {
        SolicitacaoInclusaoRepository(
            this,
            db.solicitacaoInclusaoDao()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_resultados_rotas)

        lerFiltros()

        findViewById<ImageButton>(R.id.btnVoltar).setOnClickListener { finish() }

        containerResultados = findViewById(R.id.containerResultados)
        progressResultados = findViewById(R.id.progressResultados)
        txtSemResultados = findViewById(R.id.txtSemResultados)
        txtContagemResultados = findViewById(R.id.txtContagemResultados)

        preencherResumo()

        applyTopAndBottomGaps(findViewById(android.R.id.content))

        buscar()
    }

    // ---------------------------------------------------------
    // FILTROS E RESUMO
    // ---------------------------------------------------------

    private fun lerFiltros() {
        embarqueEndereco = intent.getStringExtra(EXTRA_EMBARQUE_ENDERECO).orEmpty()
        embarqueLat = intent.getDoubleExtra(EXTRA_EMBARQUE_LAT, Double.NaN)
        embarqueLng = intent.getDoubleExtra(EXTRA_EMBARQUE_LNG, Double.NaN)
        destinoEndereco = intent.getStringExtra(EXTRA_DESTINO_ENDERECO).orEmpty()
        destinoLat = intent.getDoubleExtra(EXTRA_DESTINO_LAT, Double.NaN)
        destinoLng = intent.getDoubleExtra(EXTRA_DESTINO_LNG, Double.NaN)
        turno = intent.getStringExtra(EXTRA_TURNO)
        dias = intent.getStringArrayListExtra(EXTRA_DIAS)?.toSet() ?: emptySet()
        horarioDesejado = intent.getStringExtra(EXTRA_HORARIO).orEmpty()
        passageiroId = intent.getStringExtra(EXTRA_PASSAGEIRO_ID).orEmpty()
        nomePassageiro = intent.getStringExtra(EXTRA_NOME_PASSAGEIRO).orEmpty()
        localId = intent.getStringExtra(EXTRA_LOCAL_ID)
        modalidade = intent.getStringExtra(EXTRA_MODALIDADE) ?: "IDA"

        if (embarqueLat.isNaN() || embarqueLng.isNaN() ||
            destinoLat.isNaN() || destinoLng.isNaN()
        ) {
            Toast.makeText(this, "Busca inválida.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun preencherResumo() {
        findViewById<TextView>(R.id.txtResumoEmbarque).text =
            "Embarque: $embarqueEndereco"

        findViewById<TextView>(R.id.txtResumoDestino).text =
            "Destino: $destinoEndereco"

        findViewById<TextView>(R.id.txtResumoTurno).text =
            turno?.let { traduzirTurno(it) } ?: "Qualquer turno"

        findViewById<TextView>(R.id.txtResumoDias).text =
            if (dias.isEmpty()) {
                "Todos os dias"
            } else {
                ORDEM_DIAS
                    .filter { it in dias }
                    .joinToString(", ") { ROTULO_DIAS[it] ?: it }
            }
    }

    // ---------------------------------------------------------
    // BUSCA
    // ---------------------------------------------------------

    private fun buscar() {
        progressResultados.visibility = View.VISIBLE
        txtSemResultados.visibility = View.GONE
        containerResultados.removeAllViews()

        lifecycleScope.launch {
            try {
                val resultados = rotaRepository.buscarRotasCompativeis(
                    embarqueLatitude = embarqueLat,
                    embarqueLongitude = embarqueLng,
                    destinoLatitude = destinoLat,
                    destinoLongitude = destinoLng,
                    turno = turno,
                    dias = dias
                )

                val resultadosFiltrados = resultados.filter { item ->
                    when (modalidade.uppercase()) {
                        "IDA" -> item.rota.tipoTrajeto.equals("IDA", ignoreCase = true) ||
                                item.rota.tipoTrajeto.equals("IDA_E_VOLTA", ignoreCase = true)

                        "VOLTA" -> item.rota.tipoTrajeto.equals("VOLTA", ignoreCase = true) ||
                                item.rota.tipoTrajeto.equals("IDA_E_VOLTA", ignoreCase = true)

                        "IDA_E_VOLTA" -> item.rota.tipoTrajeto.equals("IDA_E_VOLTA", ignoreCase = true)

                        else -> false
                    }
                }

                renderizar(resultadosFiltrados)

            } catch (e: Exception) {
                txtContagemResultados.text = "Não foi possível buscar."

                Toast.makeText(
                    this@ResultadosRotasActivity,
                    "Erro na busca: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                progressResultados.visibility = View.GONE
            }
        }
    }

    private fun renderizar(resultados: List<RotaCompativel>) {
        containerResultados.removeAllViews()

        if (resultados.isEmpty()) {
            txtContagemResultados.text = "Nenhuma van encontrada"
            txtSemResultados.visibility = View.VISIBLE
            return
        }

        txtSemResultados.visibility = View.GONE
        txtContagemResultados.text =
            if (resultados.size == 1) "1 van encontrada"
            else "${resultados.size} vans encontradas"

        resultados.forEach { adicionarCard(it) }
    }

    // ---------------------------------------------------------
    // CARD DE CADA VAN
    // ---------------------------------------------------------

    private fun adicionarCard(item: RotaCompativel) {
        val rota = item.rota

        val card = layoutInflater.inflate(
            R.layout.item_resultado_van, containerResultados, false
        )

        val txtNomeMotorista = card.findViewById<TextView>(R.id.txtNomeMotoristaResultado)
        val txtInicial = card.findViewById<TextView>(R.id.txtInicialMotorista)

        card.findViewById<TextView>(R.id.txtNomeRotaResultado).text = rota.nome
        card.findViewById<TextView>(R.id.txtSentidoResultado).text =
            rotuloSentido(rota.tipoTrajeto)
        card.findViewById<TextView>(R.id.txtTurnoResultado).text =
            traduzirTurno(rota.turno)

        montarDias(
            card.findViewById(R.id.containerDiasResultado),
            rota.diasSemana.split(",").map { it.trim() }.toSet()
        )

        val livres = (rota.capacidadeTotal - rota.vagasOcupadas).coerceAtLeast(0)
        card.findViewById<TextView>(R.id.txtVagasResultado).text =
            if (livres == 1) "1 vaga livre" else "$livres vagas livres"

        card.findViewById<TextView>(R.id.txtDistanciaEmbarqueResultado).text =
            formatarDistancia(item.distanciaEmbarqueMetros)

        card.findViewById<TextView>(R.id.txtEscolaResultado).text =
            "Escola: ${rota.escolaNome.ifBlank { rota.destinoNome }}"

        // Nome do motorista (vem do Firestore)
        lifecycleScope.launch {
            val nome = try {
                firestore.collection("usuarios")
                    .document(rota.motoristaId)
                    .get().await()
                    .getString("nome") ?: "Motorista"
            } catch (e: Exception) {
                "Motorista"
            }
            txtNomeMotorista.text = nome
            txtInicial.text = nome.firstOrNull()?.uppercase() ?: "M"
        }

        card.findViewById<MaterialButton>(R.id.btnTrajetoResultado).setOnClickListener {
            startActivity(
                Intent(this, VisualizarRotaActivity::class.java)
                    .putExtra(VisualizarRotaActivity.EXTRA_ROTA_ID, rota.id)
            )
        }

        val btnReservar = card.findViewById<MaterialButton>(R.id.btnReservarResultado)
        btnReservar.setOnClickListener {
            enviarSolicitacao(item, btnReservar)
        }

        containerResultados.addView(card)
    }

    /** Monta os 7 quadradinhos dos dias: os da rota em laranja, os outros em cinza. */
    private fun montarDias(container: LinearLayout, diasDaRota: Set<String>) {
        container.removeAllViews()

        ORDEM_DIAS.forEach { chave ->
            val ativo = chave in diasDaRota

            val quadrado = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f).apply {
                    marginStart = dp(2)
                    marginEnd = dp(2)
                }
                gravity = android.view.Gravity.CENTER
                text = ROTULO_DIAS[chave]
                textSize = 11f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setBackgroundResource(
                    if (ativo) R.drawable.bg_dia_ativo else R.drawable.bg_dia_inativo
                )
                setTextColor(
                    if (ativo) ContextCompat.getColor(this@ResultadosRotasActivity, android.R.color.white)
                    else ContextCompat.getColor(this@ResultadosRotasActivity, R.color.light_gray)
                )
            }

            container.addView(quadrado)
        }
    }

    // ---------------------------------------------------------
    // SOLICITAÇÃO
    // ---------------------------------------------------------

    private fun enviarSolicitacao(item: RotaCompativel, botao: MaterialButton) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Sessão expirada.", Toast.LENGTH_SHORT).show()
            return
        }

        val rota = item.rota

        val solicitacao = SolicitacaoInclusaoEntity(
            rotaId = rota.id,
            passageiroId = passageiroId,
            nomePassageiro = nomePassageiro,
            responsavelId = uid,
            motoristaId = rota.motoristaId,
            localEmbarqueId = localId.orEmpty(),
            nomeLocalEmbarque = embarqueEndereco,
            enderecoEmbarque = embarqueEndereco,
            latitudeEmbarque = embarqueLat,
            longitudeEmbarque = embarqueLng,
            horarioDesejado = horarioDesejado
        )

        botao.isEnabled = false

        lifecycleScope.launch {
            try {
                solicitacaoRepository.enviarSolicitacao(solicitacao)
                Toast.makeText(this@ResultadosRotasActivity, "Solicitação enviada!", Toast.LENGTH_SHORT).show()

                // Avisa a tela de pesquisa para fechar também
                setResult(Activity.RESULT_OK)
                finish()
            } catch (e: Exception) {
                botao.isEnabled = true
                Toast.makeText(this@ResultadosRotasActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ---------------------------------------------------------
    // AUXILIARES
    // ---------------------------------------------------------

    private fun dp(valor: Int): Int =
        (valor * resources.displayMetrics.density).toInt()

    private fun formatarDistancia(metros: Int): String =
        if (metros < 1000) "$metros m do embarque"
        else String.format(Locale("pt", "BR"), "%.1f km do embarque", metros / 1000.0)

    private fun rotuloSentido(tipoTrajeto: String): String =
        when (tipoTrajeto) {
            "IDA" -> "Ida"
            "VOLTA" -> "Volta"
            "IDA_E_VOLTA" -> "Ida e volta"
            else -> tipoTrajeto
        }

    private fun traduzirTurno(turno: String): String =
        when (turno) {
            "MANHA" -> "Manhã"
            "TARDE" -> "Tarde"
            "NOITE" -> "Noite"
            else -> turno
        }
}