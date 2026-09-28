package com.example.vaivan.ui.responsavel.rotas

import android.content.res.ColorStateList
import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.vaivan.data.models.RotaCompativel
import com.example.vaivan.ui.responsavel.passageiros.SelecaoLocalizacaoActivity
import java.util.Locale
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.vaivan.R
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.entities.RotaEntity
import com.example.vaivan.data.local.entities.SolicitacaoInclusaoEntity
import com.example.vaivan.data.remote.routes.GoogleRoutesClient
import com.example.vaivan.data.repository.RotaRepository
import com.example.vaivan.data.repository.SolicitacaoInclusaoRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class PesquisarRotasFragment : Fragment() {

    private lateinit var edtDesembarque: EditText
    private lateinit var edtHoraInicio: EditText
    private lateinit var containerResultados: LinearLayout
    private lateinit var txtSemResultados: TextView

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
                limparResultados()
            }
        }

    private val selecaoDestinoLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            extrairPonto(result)?.let { ponto ->
                desembarque = ponto
                edtDesembarque.setText(ponto.endereco)
                limparResultados()
            }
        }

    private var passageiroId: String = ""
    private var nomePassageiro: String = ""
    private var localId: String? = null

    private lateinit var rotaRepository: RotaRepository
    private lateinit var solicitacaoRepository: SolicitacaoInclusaoRepository

    private val firestore =
        FirebaseFirestore.getInstance()

    private val auth =
        FirebaseAuth.getInstance()


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

        inicializarDependencias()
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

    private fun inicializarDependencias() {

        val db =
            VaivanDatabase.getInstance(
                requireContext().applicationContext
            )

        rotaRepository =
            RotaRepository(
                rotaDao = db.rotaDao(),
                paradaRotaDao = db.paradaRotaDao(),
                routesClient =
                    GoogleRoutesClient(
                        requireContext()
                    )
            )

        solicitacaoRepository =
            SolicitacaoInclusaoRepository(
                requireContext(),
                db.solicitacaoInclusaoDao()
            )
    }


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

        containerResultados =
            view.findViewById(
                R.id.containerResultadosRotas
            )

        txtSemResultados =
            view.findViewById(
                R.id.txtSemResultados
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
                limparResultados()
            }
        }
    }

    private fun configurarDias() {
        botoesDias.forEach { (chave, botao) ->
            botao.setOnClickListener {
                // add devolve false se já existia: funciona como um interruptor
                if (!diasSelecionados.add(chave)) diasSelecionados.remove(chave)
                atualizarCores()
                limparResultados()
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

    private fun limparResultados() {
        containerResultados.removeAllViews()
        txtSemResultados.visibility = View.GONE
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

        btnPesquisar.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val resultados = rotaRepository.buscarRotasCompativeis(
                    embarqueLatitude = pontoEmbarque.latitude,
                    embarqueLongitude = pontoEmbarque.longitude,
                    destinoLatitude = pontoDestino.latitude,
                    destinoLongitude = pontoDestino.longitude,
                    turno = turnoSelecionado,
                    dias = diasSelecionados.toSet()
                )
                renderizarResultados(resultados)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Erro na busca: ${e.message}", Toast.LENGTH_LONG)
                    .show()
            } finally {
                btnPesquisar.isEnabled = true
            }
        }
    }


    // =========================================================
    // RESULTADOS
    // =========================================================

    private fun renderizarResultados(resultados: List<RotaCompativel>) {
        containerResultados.removeAllViews()

        if (resultados.isEmpty()) {
            txtSemResultados.visibility = View.VISIBLE
            return
        }
        txtSemResultados.visibility = View.GONE

        resultados.forEach { adicionarItemRota(it) }
    }

    private fun formatarDistancia(metros: Int): String =
        if (metros < 1000) "$metros m"
        else String.format(Locale("pt", "BR"), "%.1f km", metros / 1000.0)

    private fun adicionarItemRota(item: RotaCompativel) {
        val rota = item.rota

        val itemView = layoutInflater.inflate(
            R.layout.item_resultado_rota, containerResultados, false
        )

        val txtNomeMotorista = itemView.findViewById<TextView>(R.id.txtNomeMotoristaResultado)
        val txtNomeRota = itemView.findViewById<TextView>(R.id.txtNomeRotaResultado)
        val txtVagas = itemView.findViewById<TextView>(R.id.txtVagasResultado)
        val btnReservar = itemView.findViewById<Button>(R.id.btnReservarResultado)

        txtNomeRota.text = "${rota.nome} · Destino: ${rota.destinoEndereco}"

        txtVagas.text =
            "Vagas: ${rota.capacidadeTotal - rota.vagasOcupadas} disponíveis\n" +
                    "Passa a ${formatarDistancia(item.distanciaEmbarqueMetros)} do embarque"

        txtNomeMotorista.text = "Carregando motorista..."
        viewLifecycleOwner.lifecycleScope.launch {
            txtNomeMotorista.text = try {
                firestore.collection("usuarios")
                    .document(rota.motoristaId)
                    .get().await()
                    .getString("nome") ?: "Motorista"
            } catch (e: Exception) {
                "Motorista"
            }
        }

        btnReservar.setOnClickListener { enviarSolicitacao(item) }

        containerResultados.addView(itemView)
    }

    // =========================================================
    // SOLICITAÇÃO
    // =========================================================

    private fun enviarSolicitacao(item: RotaCompativel) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(requireContext(), "Sessão expirada.", Toast.LENGTH_SHORT).show()
            return
        }
        val pontoEmbarque = embarque ?: return
        val rota = item.rota

        val solicitacao = SolicitacaoInclusaoEntity(
            rotaId = rota.id,
            passageiroId = passageiroId,
            nomePassageiro = nomePassageiro,
            responsavelId = uid,
            motoristaId = rota.motoristaId,
            localEmbarqueId = localId.orEmpty(),
            nomeLocalEmbarque = pontoEmbarque.endereco,
            enderecoEmbarque = pontoEmbarque.endereco,
            latitudeEmbarque = pontoEmbarque.latitude,
            longitudeEmbarque = pontoEmbarque.longitude,
            horarioDesejado = edtHoraInicio.text?.toString()?.trim().orEmpty()
        )

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                solicitacaoRepository.enviarSolicitacao(solicitacao)
                Toast.makeText(requireContext(), "Solicitação enviada!", Toast.LENGTH_SHORT).show()
                requireActivity().onBackPressedDispatcher.onBackPressed()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}