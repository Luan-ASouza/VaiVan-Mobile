package com.example.vaivan.ui.responsavel.rotas

import android.content.res.ColorStateList
import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.vaivan.ui.responsavel.passageiros.SelecaoLocalizacaoActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.vaivan.R

class PesquisarRotasFragment : Fragment() {

    private lateinit var edtDesembarque: EditText
    private lateinit var edtHoraInicio: EditText

    private lateinit var botoesTurno: Map<String, Button>

    private var turnoSelecionado: String? = null
    private lateinit var edtEmbarque: EditText
    private lateinit var txtNomePassageiro: TextView
    private lateinit var btnPesquisar: Button
    private lateinit var botoesDias: Map<String, Button>

    private val diasSelecionados = mutableSetOf<String>()

    private data class PontoSelecionado(
        val endereco: String,
        val latitude: Double,
        val longitude: Double
    )

    private var embarque: PontoSelecionado? = null
    private var desembarque: PontoSelecionado? = null

    private val selecaoEmbarqueLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            extrairPonto(result)?.let { ponto ->
                embarque = ponto
                edtEmbarque.setText(ponto.endereco)
            }
        }

    private val selecaoDestinoLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            extrairPonto(result)?.let { ponto ->
                desembarque = ponto
                edtDesembarque.setText(ponto.endereco)
            }
        }

    // Tela de resultados: se a solicitação foi enviada, fecha esta tela também
    private val resultadosLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }

    private var passageiroId: String = ""
    private var nomePassageiro: String = ""
    private var localId: String? = null


    // =========================================================
    // CICLO DE VIDA
    // =========================================================

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

        super.onViewCreated(
            view,
            savedInstanceState
        )

        inicializarDados()
        inicializarViews(view)
        configurarSelecaoDePontos()
        configurarTurnos()
        configurarDias()
        atualizarCores()
        configurarBotoes()
    }


    // =========================================================
    // INICIALIZAÇÃO
    // =========================================================

    private fun inicializarDados() {

        passageiroId =
            arguments?.getString(
                "passageiroId"
            ) ?: ""

        nomePassageiro =
            arguments?.getString(
                "nomePassageiro"
            ) ?: ""

        localId =
            arguments?.getString(
                "localId"
            )
    }


    private fun inicializarViews(
        view: View

    ) {

        edtDesembarque =
            view.findViewById(
                R.id.edtDesembarque
            )

        edtHoraInicio =
            view.findViewById(
                R.id.edtHoraInicio
            )

        botoesTurno =
            mapOf(

                "MANHA" to
                        view.findViewById(
                            R.id.btnManha
                        ),

                "TARDE" to
                        view.findViewById(
                            R.id.btnTarde
                        ),

                "NOITE" to
                        view.findViewById(
                            R.id.btnNoite
                        )
            )
        txtNomePassageiro = view.findViewById(R.id.txtNomePassageiro)
        edtEmbarque = view.findViewById(R.id.edtEmbarque)
        btnPesquisar = view.findViewById(R.id.btnPesquisarRotas)

        txtNomePassageiro.text = nomePassageiro.ifBlank { "Passageiro" }

        botoesDias = mapOf(
            "SEG" to view.findViewById<Button>(R.id.btnSegunda),
            "TER" to view.findViewById<Button>(R.id.btnTerca),
            "QUA" to view.findViewById<Button>(R.id.btnQuarta),
            "QUI" to view.findViewById<Button>(R.id.btnQuinta),
            "SEX" to view.findViewById<Button>(R.id.btnSexta),
            "SAB" to view.findViewById<Button>(R.id.btnSabado),
            "DOM" to view.findViewById<Button>(R.id.btnDomingo)
        )
    }


    private fun configurarTurnos() {
        botoesTurno.forEach { (chave, botao) ->
            botao.setOnClickListener {
                // tocar de novo no mesmo turno remove o filtro
                turnoSelecionado = if (turnoSelecionado == chave) null else chave
                atualizarCores()
            }
        }
    }

    private fun configurarDias() {
        botoesDias.forEach { (chave, botao) ->
            botao.setOnClickListener {
                // add devolve false se já existia: funciona como um interruptor
                if (!diasSelecionados.add(chave)) diasSelecionados.remove(chave)
                atualizarCores()
            }
        }
    }

    private fun configurarBotoes() {
        btnPesquisar.setOnClickListener {
            pesquisar()
        }
    }

    private fun atualizarCores() {
        botoesTurno.forEach { (chave, b) -> pintar(b, chave == turnoSelecionado) }
        botoesDias.forEach { (chave, b) -> pintar(b, chave in diasSelecionados) }
    }

    private fun pintar(botao: Button, selecionado: Boolean) {
        val cor = if (selecionado) R.color.orange else R.color.light_gray
        botao.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(requireContext(), cor))
    }

    private fun extrairPonto(result: ActivityResult): PontoSelecionado? {
        if (result.resultCode != Activity.RESULT_OK) return null
        val data = result.data ?: return null

        val endereco = data.getStringExtra(SelecaoLocalizacaoActivity.EXTRA_ENDERECO)
        val lat = data.getDoubleExtra(SelecaoLocalizacaoActivity.EXTRA_LATITUDE, Double.NaN)
        val lng = data.getDoubleExtra(SelecaoLocalizacaoActivity.EXTRA_LONGITUDE, Double.NaN)

        if (endereco.isNullOrBlank() || lat.isNaN() || lng.isNaN()) return null
        return PontoSelecionado(endereco, lat, lng)
    }

    private fun configurarSelecaoDePontos() {
        edtEmbarque.setOnClickListener {
            selecaoEmbarqueLauncher.launch(
                Intent(requireContext(), SelecaoLocalizacaoActivity::class.java)
            )
        }
        edtDesembarque.setOnClickListener {
            selecaoDestinoLauncher.launch(
                Intent(requireContext(), SelecaoLocalizacaoActivity::class.java)
            )
        }
    }


    // =========================================================
    // PESQUISAR ROTAS
    // =========================================================

    private fun pesquisar() {
        val pontoEmbarque = embarque
        if (pontoEmbarque == null) {
            Toast.makeText(requireContext(), "Escolha o ponto de embarque.", Toast.LENGTH_SHORT)
                .show()
            return
        }
        val pontoDestino = desembarque
        if (pontoDestino == null) {
            Toast.makeText(requireContext(), "Escolha o destino.", Toast.LENGTH_SHORT).show()
            return
        }

        // Abre a tela de resultados com todos os filtros escolhidos
        val intent = Intent(requireContext(), ResultadosRotasActivity::class.java).apply {
            putExtra(ResultadosRotasActivity.EXTRA_EMBARQUE_ENDERECO, pontoEmbarque.endereco)
            putExtra(ResultadosRotasActivity.EXTRA_EMBARQUE_LAT, pontoEmbarque.latitude)
            putExtra(ResultadosRotasActivity.EXTRA_EMBARQUE_LNG, pontoEmbarque.longitude)
            putExtra(ResultadosRotasActivity.EXTRA_DESTINO_ENDERECO, pontoDestino.endereco)
            putExtra(ResultadosRotasActivity.EXTRA_DESTINO_LAT, pontoDestino.latitude)
            putExtra(ResultadosRotasActivity.EXTRA_DESTINO_LNG, pontoDestino.longitude)
            putExtra(ResultadosRotasActivity.EXTRA_TURNO, turnoSelecionado)
            putStringArrayListExtra(
                ResultadosRotasActivity.EXTRA_DIAS,
                ArrayList(diasSelecionados)
            )
            putExtra(
                ResultadosRotasActivity.EXTRA_HORARIO,
                edtHoraInicio.text?.toString()?.trim().orEmpty()
            )
            putExtra(ResultadosRotasActivity.EXTRA_PASSAGEIRO_ID, passageiroId)
            putExtra(ResultadosRotasActivity.EXTRA_NOME_PASSAGEIRO, nomePassageiro)
            putExtra(ResultadosRotasActivity.EXTRA_LOCAL_ID, localId)
        }

        resultadosLauncher.launch(intent)
    }
}