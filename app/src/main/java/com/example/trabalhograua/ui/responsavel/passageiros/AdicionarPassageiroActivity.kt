package com.example.trabalhograua.ui.responsavel.passageiros

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.trabalhograua.R
import com.example.trabalhograua.cadastro.MascaraUtil
import com.example.trabalhograua.data.local.VaivanDatabase
import com.example.trabalhograua.data.local.entities.PassageiroEntity
import com.example.trabalhograua.data.repository.PassageiroRepository
import com.example.trabalhograua.util.DataUtil
import com.example.trabalhograua.util.SystemBarUtils.applyTopAndBottomGaps
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AdicionarPassageiroActivity : AppCompatActivity() {

    // ---------------------------------------------------------
    // VIEWS
    // ---------------------------------------------------------

    private lateinit var edtNome: EditText
    private lateinit var edtNascimento: EditText

    private lateinit var layoutNecessidades:
            TextInputLayout

    private lateinit var edtNecessidades: EditText
    private lateinit var edtObservacoes: EditText

    private lateinit var btnSim: Button
    private lateinit var btnNao: Button
    private lateinit var btnSalvar: Button
    private lateinit var btnAdicionarNovoLocal: Button

    private lateinit var layoutLocal:
            TextInputLayout

    private lateinit var edtLocal:
            AutoCompleteTextView

    // ---------------------------------------------------------
    // NECESSIDADES ESPECIAIS
    // ---------------------------------------------------------

    private var temNecessidadeEspecial = false

    // ---------------------------------------------------------
    // LOCAL SELECIONADO
    // ---------------------------------------------------------

    private var localSelecionadoId: String? = null
    private var localSelecionadoNome: String? = null
    private var localSelecionadoEndereco: String? = null
    private var localSelecionadoLatitude: Double? = null
    private var localSelecionadoLongitude: Double? = null

    // ---------------------------------------------------------
    // FIREBASE
    // ---------------------------------------------------------

    private val auth =
        FirebaseAuth.getInstance()

    private val firestore =
        FirebaseFirestore.getInstance()

    // ---------------------------------------------------------
    // REPOSITORY
    // ---------------------------------------------------------

    private val repository by lazy {

        PassageiroRepository(
            VaivanDatabase
                .getInstance(this)
                .passageiroDao()
        )
    }

    // ---------------------------------------------------------
    // LOCAIS
    // ---------------------------------------------------------

    private data class LocalItem(
        val id: String,
        val nome: String,
        val endereco: String,
        val latitude: Double,
        val longitude: Double
    )

    private val locais =
        mutableListOf<LocalItem>()

    // ---------------------------------------------------------
    // RESULTADO DE ADICIONAR LOCAL
    // ---------------------------------------------------------

    private lateinit var adicionarLocalLauncher:
            ActivityResultLauncher<Intent>

    // ---------------------------------------------------------
    // ON CREATE
    // ---------------------------------------------------------

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        setContentView(
            R.layout.activity_adicionar_passageiro
        )

        inicializarViews()

        configurarResultadoAdicionarLocal()

        configurarDataNascimento()

        configurarToggleNecessidades()

        configurarDropdownLocal()

        configurarBotoes()

        carregarLocais()

        applyTopAndBottomGaps(
            findViewById(android.R.id.content)
        )
    }

    // ---------------------------------------------------------
    // VIEWS
    // ---------------------------------------------------------

    private fun inicializarViews() {

        val btnVoltar =
            findViewById<ImageView>(
                R.id.btnVoltar
            )

        btnSalvar =
            findViewById(
                R.id.btnSalvar
            )

        edtNome =
            findViewById(
                R.id.edtNomePassageiro
            )

        edtNascimento =
            findViewById(
                R.id.edtNascimento
            )

        edtNecessidades =
            findViewById(
                R.id.edtNecessidades
            )

        layoutNecessidades =
            findViewById(
                R.id.layoutNecessidades
            )

        edtObservacoes =
            findViewById(
                R.id.edtObservacoes
            )

        btnSim =
            findViewById(
                R.id.btnSimNecessidade
            )

        btnNao =
            findViewById(
                R.id.btnNaoNecessidade
            )

        layoutLocal =
            findViewById(
                R.id.layoutLocal
            )

        edtLocal =
            findViewById(
                R.id.edtLocal
            )

        btnAdicionarNovoLocal =
            findViewById(
                R.id.btnAdicionarNovoLocal
            )

        btnVoltar.setOnClickListener {
            finish()
        }
    }

    // ---------------------------------------------------------
    // DATA DE NASCIMENTO
    // ---------------------------------------------------------

    private fun configurarDataNascimento() {

        edtNascimento.addTextChangedListener(
            MascaraUtil.inserir(
                "##/##/####",
                edtNascimento
            )
        )
    }

    // ---------------------------------------------------------
    // RESULTADO DE ADICIONAR LOCAL
    // ---------------------------------------------------------

    private fun configurarResultadoAdicionarLocal() {

        adicionarLocalLauncher =
            registerForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->

                if (
                    result.resultCode !=
                    Activity.RESULT_OK
                ) {
                    return@registerForActivityResult
                }

                // Voltou da tela de adicionar local.
                // Atualiza os locais.
                carregarLocais()
            }
    }

    // ---------------------------------------------------------
    // DROPDOWN DE LOCAL
    // ---------------------------------------------------------

    private fun configurarDropdownLocal() {

        edtLocal.setOnItemClickListener {
                _, _, position, _ ->

            if (
                position < 0 ||
                position >= locais.size
            ) {
                return@setOnItemClickListener
            }

            val local =
                locais[position]

            selecionarLocal(
                local
            )
        }

        edtLocal.setOnClickListener {
            edtLocal.showDropDown()
        }
    }

    // ---------------------------------------------------------
    // SELECIONAR LOCAL
    // ---------------------------------------------------------

    private fun selecionarLocal(
        local: LocalItem
    ) {

        localSelecionadoId =
            local.id

        localSelecionadoNome =
            local.nome

        localSelecionadoEndereco =
            local.endereco

        localSelecionadoLatitude =
            local.latitude

        localSelecionadoLongitude =
            local.longitude

        edtLocal.setText(
            local.nome,
            false
        )

        edtLocal.clearFocus()
    }

    // ---------------------------------------------------------
    // LIMPAR LOCAL
    // ---------------------------------------------------------

    private fun limparLocalSelecionado() {

        localSelecionadoId = null
        localSelecionadoNome = null
        localSelecionadoEndereco = null
        localSelecionadoLatitude = null
        localSelecionadoLongitude = null
    }

    // ---------------------------------------------------------
    // CARREGAR LOCAIS
    // ---------------------------------------------------------

    private fun carregarLocais() {

        val uid =
            auth.currentUser?.uid

        if (uid == null) {

            Toast.makeText(
                this,
                "Sessão expirada. Faça login novamente.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        firestore
            .collection("locais")
            .whereEqualTo("responsavelId", uid)
            .get()
            .addOnSuccessListener { resultado ->

                locais.clear()

                resultado.documents.forEach { documento ->

                    val nome =
                        documento.getString(
                            "nome"
                        ) ?: "Local"

                    val endereco =
                        documento.getString(
                            "endereco"
                        ) ?: ""

                    val latitude =
                        documento.getDouble(
                            "latitude"
                        )

                    val longitude =
                        documento.getDouble(
                            "longitude"
                        )

                    if (
                        latitude == null ||
                        longitude == null
                    ) {
                        return@forEach
                    }

                    locais.add(
                        LocalItem(
                            id = documento.id,
                            nome = nome,
                            endereco = endereco,
                            latitude = latitude,
                            longitude = longitude
                        )
                    )
                }

                configurarAdapterLocais()
            }
            .addOnFailureListener {

                Toast.makeText(
                    this,
                    "Não foi possível carregar os locais.",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    // ---------------------------------------------------------
    // ADAPTER
    // ---------------------------------------------------------

    private fun configurarAdapterLocais() {

        if (locais.isEmpty()) {

            edtLocal.setText(
                "",
                false
            )

            limparLocalSelecionado()

            return
        }

        val adapter =
            object : ArrayAdapter<LocalItem>(
                this,
                R.layout.item_dropdown_local,
                locais
            ) {

                override fun getView(
                    position: Int,
                    convertView: View?,
                    parent: ViewGroup
                ): View {

                    val view =
                        convertView
                            ?: LayoutInflater
                                .from(context)
                                .inflate(
                                    R.layout.item_dropdown_local,
                                    parent,
                                    false
                                )

                    val local =
                        getItem(position)

                    val txtNome =
                        view.findViewById<TextView>(
                            R.id.txtNomeLocal
                        )

                    val txtEndereco =
                        view.findViewById<TextView>(
                            R.id.txtEnderecoLocal
                        )

                    txtNome.text =
                        local?.nome ?: ""

                    txtEndereco.text =
                        local?.endereco ?: ""

                    return view
                }
            }

        edtLocal.setAdapter(
            adapter
        )
    }

    // ---------------------------------------------------------
    // ABRIR ADICIONAR LOCAL
    // ---------------------------------------------------------

    private fun abrirAdicionarLocal() {

        val intent =
            Intent(
                this,
                AdicionarLocalActivity::class.java
            )

        adicionarLocalLauncher.launch(
            intent
        )
    }

    // ---------------------------------------------------------
    // NECESSIDADES ESPECIAIS
    // ---------------------------------------------------------

    private fun configurarToggleNecessidades() {

        atualizarBotoesNecessidade(
            selecionado = false
        )

        btnSim.setOnClickListener {

            atualizarBotoesNecessidade(
                selecionado = true
            )
        }

        btnNao.setOnClickListener {

            atualizarBotoesNecessidade(
                selecionado = false
            )
        }
    }

    private fun atualizarBotoesNecessidade(
        selecionado: Boolean
    ) {

        temNecessidadeEspecial =
            selecionado

        btnSim.backgroundTintList =
            android.content.res.ColorStateList.valueOf(
                if (selecionado)
                    0xFFF6B12F.toInt()
                else
                    0xFFAFAFAF.toInt()
            )

        btnNao.backgroundTintList =
            android.content.res.ColorStateList.valueOf(
                if (selecionado)
                    0xFFAFAFAF.toInt()
                else
                    0xFFF6B12F.toInt()
            )

        layoutNecessidades.visibility =
            if (selecionado) {

                View.VISIBLE

            } else {

                edtNecessidades.setText("")

                View.GONE
            }
    }

    // ---------------------------------------------------------
    // BOTÕES
    // ---------------------------------------------------------

    private fun configurarBotoes() {

        btnSalvar.setOnClickListener {

            validarESalvar()
        }

        btnAdicionarNovoLocal.setOnClickListener {
            abrirAdicionarLocal()
        }
    }

    // ---------------------------------------------------------
    // VALIDAÇÃO
    // ---------------------------------------------------------

    private fun validarESalvar() {

        val nome =
            edtNome.text
                ?.toString()
                ?.trim()
                ?: ""

        val nascimentoDigitado =
            edtNascimento.text
                ?.toString()
                ?.trim()
                ?: ""

        // -----------------------------------------------------
        // NOME
        // -----------------------------------------------------

        if (nome.length < 3) {

            Toast.makeText(
                this,
                "Informe o nome completo do passageiro.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        // -----------------------------------------------------
        // DATA
        // -----------------------------------------------------

        if (
            nascimentoDigitado.length < 10
        ) {

            Toast.makeText(
                this,
                "Informe a data de nascimento completa (DD/MM/AAAA).",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val nascimentoIso =
            DataUtil.paraIso(
                nascimentoDigitado
            )

        if (nascimentoIso == null) {

            Toast.makeText(
                this,
                "Data de nascimento inválida.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        // -----------------------------------------------------
        // IDADE
        // -----------------------------------------------------

        val idade =
            DataUtil.calcularIdade(
                nascimentoIso
            )

        if (idade < 0) {

            Toast.makeText(
                this,
                "Não foi possível calcular a idade informada.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        // -----------------------------------------------------
        // MAIOR DE IDADE
        // -----------------------------------------------------

        if (idade >= 18) {

            Toast.makeText(
                this,
                "Este passageiro já é maior de idade. Peça para ele criar a própria conta.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        // -----------------------------------------------------
        // USUÁRIO
        // -----------------------------------------------------

        val uid =
            auth.currentUser?.uid

        if (uid == null) {

            Toast.makeText(
                this,
                "Sessão expirada. Faça login novamente.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        // -----------------------------------------------------
        // LOCAL
        // -----------------------------------------------------

        if (
            localSelecionadoId == null
        ) {

            Toast.makeText(
                this,
                "Selecione um local de embarque.",
                Toast.LENGTH_SHORT
            ).show()

            edtLocal.requestFocus()

            return
        }

        // -----------------------------------------------------
        // NECESSIDADES
        // -----------------------------------------------------

        val descricaoNecessidades =
            if (temNecessidadeEspecial) {

                edtNecessidades.text
                    ?.toString()
                    ?.trim()
                    ?: ""

            } else {

                ""
            }

        val observacoes =
            edtObservacoes.text
                ?.toString()
                ?.trim()
                ?: ""

        // -----------------------------------------------------
        // PASSAGEIRO
        // -----------------------------------------------------

        val novoPassageiro =
            PassageiroEntity(
                id = "",
                nome = nome,
                dataNascimento = nascimentoIso,
                status = "ATIVO",
                maiorIdade = false,
                necessidadesEspeciais =
                    temNecessidadeEspecial,
                descricaoNecessidades =
                    descricaoNecessidades,
                observacoes =
                    observacoes,
                responsavelId =
                    uid,
                localId = localSelecionadoId
            )

        btnSalvar.isEnabled = false

        repository.salvarAsync(
            novoPassageiro,

            onSuccess = {

                Toast.makeText(
                    this,
                    "Passageiro cadastrado com sucesso!",
                    Toast.LENGTH_SHORT
                ).show()

                finish()
            },

            onError = { erro ->

                btnSalvar.isEnabled = true

                Toast.makeText(
                    this,
                    "Erro ao cadastrar passageiro: ${erro.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }
}