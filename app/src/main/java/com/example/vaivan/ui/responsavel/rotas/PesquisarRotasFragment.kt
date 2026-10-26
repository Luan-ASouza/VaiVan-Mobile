
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.vaivan.R
import com.example.vaivan.ui.responsavel.passageiros.AdicionarLocalActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class PesquisarRotasFragment : Fragment() {

    private lateinit var edtEmbarque: AutoCompleteTextView
    private lateinit var edtDesembarque: AutoCompleteTextView
    private lateinit var btnAdicionarNovoLocal: Button
    private lateinit var txtNomePassageiro: TextView
    private lateinit var btnPesquisar: Button

    private lateinit var btnIda: MaterialButton
    private lateinit var btnVolta: MaterialButton
    private lateinit var btnIdaVolta: MaterialButton

    private var modalidadeSelecionada = "IDA_E_VOLTA"

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

    private var embarque: PontoSelecionado? = null
    private var desembarque: PontoSelecionado? = null

    private val adicionarLocalLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                carregarLocais()
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
        edtDesembarque = view.findViewById(R.id.edtEscola)

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

        atualizarModalidade()

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
    }

    private fun configurarSelecaoDePontos() {
        edtEmbarque.setOnClickListener {
            edtEmbarque.showDropDown()
        }

        edtDesembarque.setOnClickListener {
            edtDesembarque.showDropDown()
        }

        edtEmbarque.setOnItemClickListener { _, _, position, _ ->
            locais.getOrNull(position)?.let { local ->
                embarque = local.paraPonto()
                edtEmbarque.setText(local.nome, false)
            }
        }

        edtDesembarque.setOnItemClickListener { _, _, position, _ ->
            locais.getOrNull(position)?.let { local ->
                desembarque = local.paraPonto()
                edtDesembarque.setText(local.nome, false)
            }
        }
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

        edtEmbarque.setAdapter(adapter)
        edtDesembarque.setAdapter(adapter)
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
        val pontoEmbarque = embarque
        if (pontoEmbarque == null) {
            Toast.makeText(
                requireContext(),
                "Escolha o ponto de embarque.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val pontoDestino = desembarque
        if (pontoDestino == null) {
            Toast.makeText(
                requireContext(),
                "Escolha o destino.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val intent = Intent(
            requireContext(),
            ResultadosRotasActivity::class.java
        ).apply {
            putExtra(
                ResultadosRotasActivity.EXTRA_EMBARQUE_ENDERECO,
                pontoEmbarque.endereco
            )
            putExtra(
                ResultadosRotasActivity.EXTRA_EMBARQUE_LAT,
                pontoEmbarque.latitude
            )
            putExtra(
                ResultadosRotasActivity.EXTRA_EMBARQUE_LNG,
                pontoEmbarque.longitude
            )

            putExtra(
                ResultadosRotasActivity.EXTRA_DESTINO_ENDERECO,
                pontoDestino.endereco
            )
            putExtra(
                ResultadosRotasActivity.EXTRA_DESTINO_LAT,
                pontoDestino.latitude
            )
            putExtra(
                ResultadosRotasActivity.EXTRA_DESTINO_LNG,
                pontoDestino.longitude
            )

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
                modalidadeSelecionada
            )
        }

        resultadosLauncher.launch(intent)
    }

    private fun atualizarModalidade() {
        pintar(btnIda, modalidadeSelecionada == "IDA")
        pintar(btnVolta, modalidadeSelecionada == "VOLTA")
        pintar(btnIdaVolta, modalidadeSelecionada == "IDA_E_VOLTA")
    }
}
