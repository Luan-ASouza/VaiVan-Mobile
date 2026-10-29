package com.example.vaivan.ui.responsavel.rotas

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.vaivan.R
import com.example.vaivan.ui.escolas.SelecionarEscolaActivity
import com.example.vaivan.ui.responsavel.passageiros.AdicionarLocalActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class PesquisarRotasFragment : Fragment() {

    private lateinit var edtEmbarque: AutoCompleteTextView
    private lateinit var edtEscola: AutoCompleteTextView
    private lateinit var layoutEscola: TextInputLayout
    private lateinit var btnAdicionarNovoLocal: Button
    private lateinit var txtNomePassageiro: TextView
    private lateinit var btnPesquisar: Button

    private lateinit var btnIda: MaterialButton
    private lateinit var btnVolta: MaterialButton
    private lateinit var btnIdaVolta: MaterialButton

    // Campos que vão aparecendo conforme a modalidade
    private lateinit var txtAvisoModalidade: TextView
    private lateinit var containerCamposRota: View
    private lateinit var txtTituloLocal: TextView
    private lateinit var txtExplicacaoLocal: TextView

    // Começa SEM modalidade: os campos só aparecem depois que ela for escolhida
    private var modalidadeSelecionada: String? = null

    private lateinit var botoesTurno: Map<String, Button>
    private lateinit var botoesDias: Map<String, Button>

    private var turnoSelecionado: String? = null
    private val diasSelecionados = mutableSetOf<String>()

    private var passageiroId = ""
    private var nomePassageiro = ""
    private var localId: String? = null

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private data class LocalItem(
        val id: String,
        val nome: String,
        val endereco: String,
        val latitude: Double,
        val longitude: Double
    )

    private data class PontoSelecionado(
        val id: String,
        val nome: String,
        val endereco: String,
        val latitude: Double,
        val longitude: Double
    )

    private val locais = mutableListOf<LocalItem>()

    // O local da família (casa, avó...) e a escola da lista oficial
    private var localSelecionado: PontoSelecionado? = null
    private var escolaSelecionada: PontoSelecionado? = null

    private val adicionarLocalLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                carregarLocais()
            }
        }

    // Tela de busca de escolas (lista oficial)
    private val selecaoEscolaLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                return@registerForActivityResult
            }

            SelecionarEscolaActivity.extrairEscola(result.data)?.let { escola ->
                escolaSelecionada = PontoSelecionado(
                    id = escola.id,
                    nome = escola.nome,
                    endereco = escola.endereco,
                    latitude = escola.latitude,
                    longitude = escola.longitude
                )
                edtEscola.setText(escola.nome, false)
            }
        }

    private val resultadosLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                requireActivity()
                    .onBackPressedDispatcher
                    .onBackPressed()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(
            R.layout.activity_pesquisar_rotas_responsavel,
            container,
            false
        )
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        inicializarDados()
        inicializarViews(view)
        configurarTurnos()
        configurarDias()
        configurarBotoes()
        configurarSelecaoDePontos()
        atualizarCores()
        carregarLocais()
    }

    private fun inicializarDados() {
        passageiroId = arguments?.getString("passageiroId").orEmpty()
        nomePassageiro = arguments?.getString("nomePassageiro").orEmpty()
        localId = arguments?.getString("localId")
    }

    private fun inicializarViews(view: View) {
        edtEmbarque = view.findViewById(R.id.edtEmbarque)
        edtEscola = view.findViewById(R.id.edtEscola)
        layoutEscola = view.findViewById(R.id.layoutEscola)

        txtAvisoModalidade = view.findViewById(R.id.txtAvisoModalidade)
        containerCamposRota = view.findViewById(R.id.containerCamposRota)
        txtTituloLocal = view.findViewById(R.id.txtTituloLocal)
        txtExplicacaoLocal = view.findViewById(R.id.txtExplicacaoLocal)

        btnIda = view.findViewById(R.id.btnIda)
        btnVolta = view.findViewById(R.id.btnVolta)
        btnIdaVolta = view.findViewById(R.id.btnIdaVolta)

        btnIda.setOnClickListener {
            modalidadeSelecionada = "IDA"
            atualizarModalidade()
        }

        btnVolta.setOnClickListener {
            modalidadeSelecionada = "VOLTA"
            atualizarModalidade()
        }

        btnIdaVolta.setOnClickListener {
            modalidadeSelecionada = "IDA_E_VOLTA"
            atualizarModalidade()
        }

        btnAdicionarNovoLocal =
            view.findViewById(R.id.btnAdicionarNovoLocal)

        txtNomePassageiro =
            view.findViewById(R.id.txtNomePassageiro)

        btnPesquisar =
            view.findViewById(R.id.btnPesquisarRotas)

        botoesTurno = mapOf(
            "MANHA" to view.findViewById(R.id.btnManha),
            "TARDE" to view.findViewById(R.id.btnTarde),
            "NOITE" to view.findViewById(R.id.btnNoite)
        )

        botoesDias = mapOf(
            "SEG" to view.findViewById(R.id.btnSegunda),
            "TER" to view.findViewById(R.id.btnTerca),
            "QUA" to view.findViewById(R.id.btnQuarta),
            "QUI" to view.findViewById(R.id.btnQuinta),
            "SEX" to view.findViewById(R.id.btnSexta),
            "SAB" to view.findViewById(R.id.btnSabado),
            "DOM" to view.findViewById(R.id.btnDomingo)
        )

        txtNomePassageiro.text =
            nomePassageiro.ifBlank { "Passageiro" }

        // Só depois de ligar todas as views
        atualizarModalidade()
    }

    private fun configurarSelecaoDePontos() {
        // LOCAL: continua sendo a lista de locais salvos do pai
        edtEmbarque.setOnClickListener {
            edtEmbarque.showDropDown()
        }

        edtEmbarque.setOnItemClickListener { _, _, position, _ ->
            locais.getOrNull(position)?.let { local ->
                localSelecionado = local.paraPonto()
                edtEmbarque.setText(local.nome, false)
            }
        }

        // ESCOLA: abre a busca com as escolas oficiais (no campo e na lupa do canto)
        edtEscola.setOnClickListener {
            abrirBuscaEscola()
        }

        layoutEscola.setEndIconDrawable(R.drawable.ic_lupa)
        layoutEscola.setEndIconOnClickListener {
            abrirBuscaEscola()
        }
    }

    private fun abrirBuscaEscola() {
        selecaoEscolaLauncher.launch(
            Intent(requireContext(), SelecionarEscolaActivity::class.java)
        )
    }

    private fun LocalItem.paraPonto() = PontoSelecionado(
        id = id,
        nome = nome,
        endereco = endereco,
        latitude = latitude,
        longitude = longitude
    )

    private fun carregarLocais() {
        val uid = auth.currentUser?.uid

        if (uid == null) {
            Toast.makeText(
                requireContext(),
                "Sessão expirada. Faça login novamente.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        firestore.collection("locais")
            .whereEqualTo("responsavelId", uid)
            .get()
            .addOnSuccessListener { resultado ->
                if (!isAdded || view == null) return@addOnSuccessListener

                locais.clear()

                resultado.documents.forEach { documento ->
                    val latitude = documento.getDouble("latitude")
                    val longitude = documento.getDouble("longitude")

                    if (latitude == null || longitude == null) {
                        return@forEach
                    }

                    locais.add(
                        LocalItem(
                            id = documento.id,
                            nome = documento.getString("nome") ?: "Local",
                            endereco = documento.getString("endereco").orEmpty(),
                            latitude = latitude,
                            longitude = longitude
                        )
                    )
                }

                configurarAdapterLocais()
            }
            .addOnFailureListener {
                if (!isAdded) return@addOnFailureListener

                Toast.makeText(
                    requireContext(),
                    "Não foi possível carregar os locais.",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun configurarAdapterLocais() {
        val adapter = object : ArrayAdapter<LocalItem>(
            requireContext(),
            R.layout.item_dropdown_local,
            locais
        ) {
            override fun getView(
                position: Int,
                convertView: View?,
                parent: ViewGroup
            ): View {
                return criarItem(position, convertView, parent)
            }

            override fun getDropDownView(
                position: Int,
                convertView: View?,
                parent: ViewGroup
            ): View {
                return criarItem(position, convertView, parent)
            }

            private fun criarItem(
                position: Int,
                convertView: View?,
                parent: ViewGroup
            ): View {
                val itemView = convertView ?: LayoutInflater
                    .from(context)
                    .inflate(
                        R.layout.item_dropdown_local,
                        parent,
                        false
                    )

                val local = getItem(position)

                itemView.findViewById<TextView>(R.id.txtNomeLocal).text =
                    local?.nome.orEmpty()

                itemView.findViewById<TextView>(R.id.txtEnderecoLocal).text =
                    local?.endereco.orEmpty()

                return itemView
            }
        }

        // A lista de locais salvos agora serve só para o campo de LOCAL.
        // O campo de escola não tem adapter: ele abre a busca de escolas.
        edtEmbarque.setAdapter(adapter)
    }

    private fun configurarBotoes() {
        btnAdicionarNovoLocal.setOnClickListener {
            val intent = Intent(
                requireContext(),
                AdicionarLocalActivity::class.java
            )
            adicionarLocalLauncher.launch(intent)
        }

        btnPesquisar.setOnClickListener {
            pesquisar()
        }
    }

    private fun configurarTurnos() {
        botoesTurno.forEach { (chave, botao) ->
            botao.setOnClickListener {
                turnoSelecionado =
                    if (turnoSelecionado == chave) null else chave

                atualizarCores()
            }
        }
    }

    private fun configurarDias() {
        botoesDias.forEach { (chave, botao) ->
            botao.setOnClickListener {
                if (!diasSelecionados.add(chave)) {
                    diasSelecionados.remove(chave)
                }

                atualizarCores()
            }
        }
    }

    private fun atualizarCores() {
        botoesTurno.forEach { (chave, botao) ->
            pintar(botao, chave == turnoSelecionado)
        }

        botoesDias.forEach { (chave, botao) ->
            pintar(botao, chave in diasSelecionados)
        }
    }


    private fun pintar(botao: Button, selecionado: Boolean) {
        val corFundo = if (selecionado) {
            android.graphics.Color.BLACK
        } else {
            android.graphics.Color.WHITE
        }

        val corTexto = if (selecionado) {
            android.graphics.Color.WHITE
        } else {
            android.graphics.Color.BLACK
        }

        botao.backgroundTintList = ColorStateList.valueOf(corFundo)
        botao.setTextColor(corTexto)

        if (botao is com.google.android.material.button.MaterialButton) {
            botao.strokeColor = ColorStateList.valueOf(
                android.graphics.Color.BLACK
            )
            botao.strokeWidth = (resources.displayMetrics.density).toInt()
        }
    }


    private fun pesquisar() {
        val escola = escolaSelecionada
        if (escola == null) {
            Toast.makeText(
                requireContext(),
                "Escolha a escola.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val modalidade = modalidadeSelecionada
        if (modalidade == null) {
            Toast.makeText(
                requireContext(),
                "Selecione a modalidade (Ida, Volta ou Ida e Volta).",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val local = localSelecionado
        if (local == null) {
            val qual = if (modalidade == "VOLTA") "desembarque" else "embarque"

            Toast.makeText(
                requireContext(),
                "Escolha o local de $qual.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // A busca precisa do trajeto na ordem REAL:
        //  - Ida e Ida e volta: sai do local da família e vai para a escola
        //  - Volta: sai da escola e vai para o local da família
        val ehVolta = modalidade == "VOLTA"

        val textoLocal = local.endereco.ifBlank { local.nome }

        val embarqueTexto = if (ehVolta) escola.nome else textoLocal
        val embarqueLat = if (ehVolta) escola.latitude else local.latitude
        val embarqueLng = if (ehVolta) escola.longitude else local.longitude

        val desembarqueTexto = if (ehVolta) textoLocal else escola.nome
        val desembarqueLat = if (ehVolta) local.latitude else escola.latitude
        val desembarqueLng = if (ehVolta) local.longitude else escola.longitude

        val intent = Intent(
            requireContext(),
            ResultadosRotasActivity::class.java
        ).apply {
            putExtra(ResultadosRotasActivity.EXTRA_EMBARQUE_ENDERECO, embarqueTexto)
            putExtra(ResultadosRotasActivity.EXTRA_EMBARQUE_LAT, embarqueLat)
            putExtra(ResultadosRotasActivity.EXTRA_EMBARQUE_LNG, embarqueLng)

            putExtra(ResultadosRotasActivity.EXTRA_DESTINO_ENDERECO, desembarqueTexto)
            putExtra(ResultadosRotasActivity.EXTRA_DESTINO_LAT, desembarqueLat)
            putExtra(ResultadosRotasActivity.EXTRA_DESTINO_LNG, desembarqueLng)

            putExtra(ResultadosRotasActivity.EXTRA_TURNO, turnoSelecionado)

            putStringArrayListExtra(
                ResultadosRotasActivity.EXTRA_DIAS,
                ArrayList(diasSelecionados)
            )

            putExtra(
                ResultadosRotasActivity.EXTRA_HORARIO,
                ""
            )

            putExtra(
                ResultadosRotasActivity.EXTRA_PASSAGEIRO_ID,
                passageiroId
            )
            putExtra(
                ResultadosRotasActivity.EXTRA_NOME_PASSAGEIRO,
                nomePassageiro
            )
            putExtra(
                ResultadosRotasActivity.EXTRA_LOCAL_ID,
                localId
            )

            putExtra(
                ResultadosRotasActivity.EXTRA_MODALIDADE,
                modalidade
            )
        }

        resultadosLauncher.launch(intent)
    }

    /**
     * Pinta os botões da modalidade e abre os campos conforme a escolha:
     *  - sem modalidade: só o aviso aparece
     *  - Ida / Ida e volta: "Local de embarque" (a escola é o desembarque)
     *  - Volta: "Local de desembarque" (a escola é o embarque)
     */
    private fun atualizarModalidade() {
        pintar(btnIda, modalidadeSelecionada == "IDA")
        pintar(btnVolta, modalidadeSelecionada == "VOLTA")
        pintar(btnIdaVolta, modalidadeSelecionada == "IDA_E_VOLTA")

        val modalidade = modalidadeSelecionada

        txtAvisoModalidade.visibility =
            if (modalidade == null) View.VISIBLE else View.GONE

        containerCamposRota.visibility =
            if (modalidade == null) View.GONE else View.VISIBLE

        if (modalidade == null) {
            return
        }

        when (modalidade) {
            "VOLTA" -> {
                txtTituloLocal.text = "Local de desembarque"
                txtExplicacaoLocal.text =
                    "Onde o aluno é deixado na volta da escola."
            }

            "IDA" -> {
                txtTituloLocal.text = "Local de embarque"
                txtExplicacaoLocal.text =
                    "Onde o aluno é buscado para ir à escola."
            }

            else -> {
                txtTituloLocal.text = "Local de embarque"
                txtExplicacaoLocal.text =
                    "Onde o aluno é buscado na ida e deixado na volta."
            }
        }
    }
}