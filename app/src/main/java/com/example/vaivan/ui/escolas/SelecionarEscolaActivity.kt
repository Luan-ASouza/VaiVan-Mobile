package com.example.vaivan.ui.escolas

import android.app.Activity
import android.graphics.Color
import android.content.Intent
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.vaivan.R
import com.example.vaivan.core.util.SystemBarUtils.applyTopAndBottomGaps
import com.example.vaivan.data.models.Escola
import com.example.vaivan.data.repository.EscolaRepository
import com.example.vaivan.ui.responsavel.passageiros.SelecaoLocalizacaoActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.util.Locale

/**
 * Tela para escolher uma escola da lista oficial (importada do INEP).
 *
 * Quem chama abre esta tela com um launcher e lê o resultado com
 * [SelecionarEscolaActivity.extrairEscola].
 *
 * A lupa/mapa serve só de ajuda: o usuário escolhe um ponto e o app sugere
 * a escola cadastrada mais próxima. Não é possível cadastrar escola nova.
 */
class SelecionarEscolaActivity : AppCompatActivity() {

    companion object {

        const val EXTRA_ESCOLA_ID = "escolaId"
        const val EXTRA_ESCOLA_NOME = "escolaNome"
        const val EXTRA_ESCOLA_ENDERECO = "escolaEndereco"
        const val EXTRA_ESCOLA_CIDADE = "escolaCidade"
        const val EXTRA_ESCOLA_LATITUDE = "escolaLatitude"
        const val EXTRA_ESCOLA_LONGITUDE = "escolaLongitude"

        /** Escolas só aparecem como sugestão do mapa se estiverem a até este raio. */
        private const val RAIO_SUGESTAO_METROS = 300f

        // Guarda a lista em memória: abrir a tela de novo não lê o Firestore outra vez
        private var cache: List<Escola>? = null

        /** Lê a escola devolvida por esta tela (ou null se o usuário cancelou). */
        fun extrairEscola(data: Intent?): Escola? {
            if (data == null) return null

            val id = data.getStringExtra(EXTRA_ESCOLA_ID) ?: return null

            return Escola(
                id = id,
                nome = data.getStringExtra(EXTRA_ESCOLA_NOME).orEmpty(),
                endereco = data.getStringExtra(EXTRA_ESCOLA_ENDERECO).orEmpty(),
                cidade = data.getStringExtra(EXTRA_ESCOLA_CIDADE).orEmpty(),
                latitude = data.getDoubleExtra(EXTRA_ESCOLA_LATITUDE, 0.0),
                longitude = data.getDoubleExtra(EXTRA_ESCOLA_LONGITUDE, 0.0)
            )
        }
    }

    private data class ItemBusca(
        val escola: Escola,
        val chaveNome: String,
        val chave: String
    )

    private lateinit var edtBusca: EditText
    private lateinit var txtContagem: TextView
    private lateinit var txtSemEscolas: TextView
    private lateinit var progress: ProgressBar
    private lateinit var recycler: RecyclerView

    private val adapter = EscolaAdapter { escola -> devolver(escola) }
    private val escolaRepository = EscolaRepository()

    private var itens: List<ItemBusca> = emptyList()

    // Resultado do mapa (a lupa que já existe)
    private val mapaLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

            if (result.resultCode != Activity.RESULT_OK) {
                return@registerForActivityResult
            }

            val data = result.data ?: return@registerForActivityResult

            val lat = data.getDoubleExtra(SelecaoLocalizacaoActivity.EXTRA_LATITUDE, Double.NaN)
            val lng = data.getDoubleExtra(SelecaoLocalizacaoActivity.EXTRA_LONGITUDE, Double.NaN)

            if (lat.isNaN() || lng.isNaN()) {
                return@registerForActivityResult
            }

            sugerirEscolaProxima(lat, lng)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_selecionar_escola)

        findViewById<ImageButton>(R.id.btnVoltar).setOnClickListener { finish() }

        edtBusca = findViewById(R.id.edtBuscaEscola)
        txtContagem = findViewById(R.id.txtContagemEscolas)
        txtSemEscolas = findViewById(R.id.txtSemEscolas)
        progress = findViewById(R.id.progressEscolas)
        recycler = findViewById(R.id.recyclerEscolas)

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        edtBusca.doAfterTextChanged { aplicarFiltro() }

        findViewById<View>(R.id.txtProcurarNoMapa).setOnClickListener {
            mapaLauncher.launch(
                Intent(this, SelecaoLocalizacaoActivity::class.java)
            )
        }

        applyTopAndBottomGaps(findViewById(android.R.id.content))

        carregarEscolas()
    }

    // ---------------------------------------------------------
    // CARREGAR
    // ---------------------------------------------------------

    private fun carregarEscolas() {

        lifecycleScope.launch {

            progress.visibility = View.VISIBLE

            try {
                val escolas = cache ?: escolaRepository.listarEscolas()
                    // Escolas sem coordenada não servem como destino de rota
                    .filter { it.latitude != 0.0 || it.longitude != 0.0 }
                    .also { cache = it }

                itens = escolas.map { escola ->
                    ItemBusca(
                        escola = escola,
                        chaveNome = normalizar(escola.nome),
                        chave = normalizar(
                            "${escola.nome} ${escola.nomeOriginal} ${escola.endereco} " +
                                    "${escola.dependencia} ${sinonimos(escola.nomeOriginal)}"
                        )
                    )
                }

                aplicarFiltro()

            } catch (e: Exception) {

                txtContagem.text = "Não foi possível carregar as escolas."

                Toast.makeText(
                    this@SelecionarEscolaActivity,
                    "Erro ao carregar escolas: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()

            } finally {
                progress.visibility = View.GONE
            }
        }
    }

    // ---------------------------------------------------------
    // BUSCA
    // ---------------------------------------------------------

    private fun aplicarFiltro() {

        val consulta = normalizar(edtBusca.text?.toString().orEmpty())
        val palavras = consulta.split(" ").filter { it.isNotBlank() }

        val resultado =
            if (palavras.isEmpty()) {
                itens
            } else {
                itens
                    .filter { item -> palavras.all { it in item.chave } }
                    .sortedWith(
                        compareBy<ItemBusca> {
                            when {
                                it.chaveNome.startsWith(consulta) -> 0
                                consulta in it.chaveNome -> 1
                                else -> 2
                            }
                        }.thenBy { it.chaveNome }
                    )
            }

        adapter.atualizar(resultado.map { it.escola })

        txtSemEscolas.visibility =
            if (resultado.isEmpty() && itens.isNotEmpty()) View.VISIBLE else View.GONE

        txtContagem.text =
            when (resultado.size) {
                0 -> "Nenhuma escola"
                1 -> "1 escola"
                else -> "${resultado.size} escolas"
            }
    }

    /** Tira acento, caixa e símbolos, para "Gonçalves" casar com "goncalves". */
    private fun normalizar(texto: String): String {
        val semAcento = Normalizer
            .normalize(texto, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

        return semAcento
            .lowercase(Locale("pt", "BR"))
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }

    /** Palavras extras para quem digita "escola municipal" e a escola é "EMEF ...". */
    private fun sinonimos(nomeOriginal: String): String {
        val nome = nomeOriginal.uppercase(Locale("pt", "BR"))

        return when {
            nome.startsWith("EMEF") -> "escola municipal ensino fundamental"
            nome.startsWith("EMEI") -> "escola municipal educacao infantil"
            nome.startsWith("EEI") -> "escola educacao infantil"
            else -> ""
        }
    }

    // ---------------------------------------------------------
    // MAPA COMO AJUDA
    // ---------------------------------------------------------

    private fun sugerirEscolaProxima(latitude: Double, longitude: Double) {

        if (itens.isEmpty()) {
            Toast.makeText(this, "As escolas ainda estão carregando.", Toast.LENGTH_SHORT).show()
            return
        }

        val resultado = FloatArray(1)

        val maisProxima = itens
            .map { item ->
                Location.distanceBetween(
                    latitude,
                    longitude,
                    item.escola.latitude,
                    item.escola.longitude,
                    resultado
                )
                item.escola to resultado[0]
            }
            .minByOrNull { it.second }

        if (maisProxima == null || maisProxima.second > RAIO_SUGESTAO_METROS) {

            Toast.makeText(
                this,
                "Nenhuma escola cadastrada perto desse ponto. Tente buscar pelo nome.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        mostrarSugestao(maisProxima.first, maisProxima.second.toInt())
    }

    private fun mostrarSugestao(escola: Escola, distanciaMetros: Int) {

        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_confirmar_escola, null)
        sheet.setContentView(view)

        (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)

        view.findViewById<TextView>(R.id.txtSugestaoNome).text = escola.nome
        view.findViewById<TextView>(R.id.txtSugestaoEndereco).text = escola.endereco
        view.findViewById<TextView>(R.id.txtSugestaoDistancia).text =
            "A $distanciaMetros m do ponto escolhido no mapa"

        view.findViewById<View>(R.id.btnSugestaoCancelar).setOnClickListener {
            sheet.dismiss()
        }

        view.findViewById<View>(R.id.btnSugestaoConfirmar).setOnClickListener {
            sheet.dismiss()
            devolver(escola)
        }

        sheet.show()
    }

    // ---------------------------------------------------------
    // DEVOLVER A ESCOLA ESCOLHIDA
    // ---------------------------------------------------------

    private fun devolver(escola: Escola) {

        val resultado = Intent().apply {
            putExtra(EXTRA_ESCOLA_ID, escola.id)
            putExtra(EXTRA_ESCOLA_NOME, escola.nome)
            putExtra(EXTRA_ESCOLA_ENDERECO, escola.endereco)
            putExtra(EXTRA_ESCOLA_CIDADE, escola.cidade)
            putExtra(EXTRA_ESCOLA_LATITUDE, escola.latitude)
            putExtra(EXTRA_ESCOLA_LONGITUDE, escola.longitude)
        }

        setResult(Activity.RESULT_OK, resultado)
        finish()
    }
}

// =============================================================
// LISTA
// =============================================================

private class EscolaAdapter(
    private val aoClicar: (Escola) -> Unit
) : RecyclerView.Adapter<EscolaAdapter.ViewHolder>() {

    private var itens: List<Escola> = emptyList()

    fun atualizar(novos: List<Escola>) {
        itens = novos
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nome: TextView = view.findViewById(R.id.txtNomeEscolaItem)
        val endereco: TextView = view.findViewById(R.id.txtEnderecoEscolaItem)
        val dependencia: TextView = view.findViewById(R.id.txtDependenciaEscolaItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_escola, parent, false)

        return ViewHolder(view)
    }

    override fun getItemCount(): Int = itens.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        val escola = itens[position]

        holder.nome.text = escola.nome
        holder.endereco.text = escola.endereco

        val rotulo = when (escola.dependencia) {
            "MUNICIPAL" -> "Municipal"
            "ESTADUAL" -> "Estadual"
            "FEDERAL" -> "Federal"
            "PRIVADA" -> "Privada"
            else -> ""
        }

        holder.dependencia.text = rotulo
        holder.dependencia.visibility =
            if (rotulo.isEmpty()) View.GONE else View.VISIBLE

        holder.itemView.setOnClickListener { aoClicar(escola) }
    }
}