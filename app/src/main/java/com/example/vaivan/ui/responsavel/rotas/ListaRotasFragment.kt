package com.example.vaivan.ui.responsavel.rotas

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.vaivan.R
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.models.RotasDoResponsavel
import com.example.vaivan.data.remote.routes.GoogleRoutesClient
import com.example.vaivan.data.repository.PassageiroRepository
import com.example.vaivan.data.repository.RotaRepository
import com.example.vaivan.data.repository.UsuarioRepository
import com.example.vaivan.data.repository.VeiculoRepository
import com.example.vaivan.domain.usecase.rota.BuscarRotasDoResponsavelUseCase
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ListaRotasFragment : Fragment() {

    companion object {
        private val ORDEM_DIAS = listOf("SEG", "TER", "QUA", "QUI", "SEX", "SAB", "DOM")
        private val ROTULO_DIAS = mapOf(
            "SEG" to "Seg", "TER" to "Ter", "QUA" to "Qua", "QUI" to "Qui",
            "SEX" to "Sex", "SAB" to "Sáb", "DOM" to "Dom"
        )
    }

    private lateinit var containerRotas: LinearLayout

    private val viewModel: ListaRotasViewModel by lazy {

        val db =
            VaivanDatabase.getInstance(
                requireContext()
            )

        val passageiroRepository =
            PassageiroRepository(
                db.passageiroDao()
            )

        val rotaRepository =
            RotaRepository(
                db.rotaDao(),
                db.paradaRotaDao(),
                GoogleRoutesClient(
                    requireContext()
                )
            )

        val usuarioRepository =
            UsuarioRepository(
                db.usuarioDao()
            )

        val veiculoRepository =
            VeiculoRepository(
                db.veiculoDao()
            )

        val useCase =
            BuscarRotasDoResponsavelUseCase(
                passageiroRepository = passageiroRepository,
                rotaRepository = rotaRepository,
                usuarioRepository = usuarioRepository,
                veiculoRepository = veiculoRepository
            )

        ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {

                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>
                ): T {

                    return ListaRotasViewModel(
                        useCase
                    ) as T
                }
            }
        )[ListaRotasViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view =
            inflater.inflate(
                R.layout.fragment_lista_rotas,
                container,
                false
            )

        containerRotas =
            view.findViewById(
                R.id.containerListaRotas
            )

        return view
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        observarRotas()

        carregarRotas()
    }

    private fun carregarRotas() {

        val responsavelId =
            FirebaseAuth
                .getInstance()
                .currentUser
                ?.uid
                ?: return

        viewModel.carregarRotas(
            responsavelId
        )
    }

    private fun observarRotas() {

        viewLifecycleOwner.lifecycleScope.launch {

            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {

                viewModel.rotas.collect { rotas ->

                    mostrarRotas(
                        rotas
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------
    // LISTA DE CARDS (um por filho e por viagem)
    // ---------------------------------------------------------

    private fun mostrarRotas(
        rotas: List<RotasDoResponsavel>
    ) {

        containerRotas.removeAllViews()

        if (rotas.isEmpty()) {
            containerRotas.addView(criarAvisoVazio())
            return
        }

        rotas.forEach { item ->

            val card =
                layoutInflater.inflate(
                    R.layout.item_rota_responsavel,
                    containerRotas,
                    false
                )

            preencherCard(
                card,
                item
            )

            containerRotas.addView(
                card
            )
        }
    }

    private fun criarAvisoVazio(): TextView {

        return TextView(requireContext()).apply {
            text =
                "Nenhum transporte contratado ainda.\n" +
                        "Procure uma rota na aba Passageiros."
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.light_gray))
            setPadding(dp(16), dp(32), dp(16), dp(32))
        }
    }

    private fun preencherCard(
        card: View,
        item: RotasDoResponsavel
    ) {

        val rota = item.rota
        val parada = item.parada

        // Aluno
        val nomeAluno =
            parada?.nomePassageiro
                .orEmpty()
                .ifBlank { "Aluno" }

        card.findViewById<TextView>(R.id.txtNomeAlunoCard).text = nomeAluno
        card.findViewById<TextView>(R.id.txtInicialAlunoCard).text =
            nomeAluno.first().uppercase()

        // Rota, tipo e turno
        card.findViewById<TextView>(R.id.txtNomeRotaCard).text = rota.nome
        card.findViewById<TextView>(R.id.txtTipoRotaCard).text =
            rotuloTipo(rota.tipoTrajeto)
        card.findViewById<TextView>(R.id.txtTurnoRotaCard).text =
            traduzirTurno(rota.turno)

        // Dias da semana
        montarDias(
            card.findViewById(R.id.containerDiasCard),
            rota.diasSemana.split(",").map { it.trim() }.toSet()
        )

        // Embarque e desembarque:
        //  - Ida e Ida e volta: embarca no local do aluno, desembarca na escola
        //  - Volta: embarca na escola, desembarca no local do aluno
        val localDoAluno = textoDoLocal(item)
        val escola = rota.escolaNome.ifBlank { rota.destinoNome }
        val ehVolta = rota.tipoTrajeto == "VOLTA"

        card.findViewById<TextView>(R.id.txtEmbarqueCard).text =
            if (ehVolta) escola else localDoAluno

        card.findViewById<TextView>(R.id.txtDesembarqueCard).text =
            if (ehVolta) localDoAluno else escola

        card.findViewById<View>(R.id.txtNotaVoltaCard).visibility =
            if (rota.tipoTrajeto == "IDA_E_VOLTA") View.VISIBLE else View.GONE

        // Motorista e veículo
        card.findViewById<TextView>(R.id.txtMotoristaCard).text =
            textoDoMotorista(item)

        // Próxima cobrança (apenas um aviso)
        card.findViewById<TextView>(R.id.txtProximaCobrancaCard).text =
            "Próxima cobrança: " + textoProximaCobranca(parada?.contratadoEm ?: 0L)

        // Acompanhar a viagem DESTE filho (cada card leva a sua própria rota)
        card.findViewById<View>(R.id.btnAcompanharViagemCard).setOnClickListener {
            abrirAcompanhamento(item)
        }
    }

    /**
     * Abre a tela de acompanhamento já informando de qual filho e de qual rota se trata.
     */
    private fun abrirAcompanhamento(
        item: RotasDoResponsavel
    ) {

        val tela = AcompanharViagemFragment().apply {
            arguments = Bundle().apply {
                putString("rotaId", item.rota.id)
                putString("motoristaId", item.rota.motoristaId)
                putString("passageiroId", item.parada?.passageiroId.orEmpty())
                putString("nomePassageiro", item.parada?.nomePassageiro.orEmpty())
            }
        }

        parentFragmentManager
            .beginTransaction()
            .replace(
                R.id.fragmentContainer,
                tela
            )
            .addToBackStack(null)
            .commit()
    }

    // ---------------------------------------------------------
    // TEXTOS
    // ---------------------------------------------------------

    private fun textoDoLocal(item: RotasDoResponsavel): String {

        val parada = item.parada

        val nome = parada?.nomeLocal.orEmpty()
        val endereco = parada?.endereco.orEmpty()

        return when {
            nome.isNotBlank() && endereco.isNotBlank() -> "$nome · $endereco"
            endereco.isNotBlank() -> endereco
            nome.isNotBlank() -> nome
            else -> "Local não informado"
        }
    }

    private fun textoDoMotorista(item: RotasDoResponsavel): String {

        val nome = item.motorista?.nome ?: "Motorista não informado"
        val placa = item.veiculo?.placa

        return if (placa.isNullOrBlank()) {
            nome
        } else {
            "$nome · $placa"
        }
    }

    /**
     * A cobrança cai no mesmo dia do mês da contratação (o 1º mês é pago ao contratar).
     * Em meses mais curtos, usa o último dia do mês.
     * Sem a data de contratação registrada, mostra "A definir".
     */
    private fun textoProximaCobranca(contratadoEm: Long): String {

        if (contratadoEm <= 0L) {
            return "a definir"
        }

        val agora = System.currentTimeMillis()

        val diaDaCobranca =
            Calendar.getInstance()
                .apply { timeInMillis = contratadoEm }
                .get(Calendar.DAY_OF_MONTH)

        var meses = 1

        while (true) {

            val candidata = Calendar.getInstance().apply {
                timeInMillis = contratadoEm
                set(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MONTH, meses)
                set(
                    Calendar.DAY_OF_MONTH,
                    minOf(diaDaCobranca, getActualMaximum(Calendar.DAY_OF_MONTH))
                )
            }

            if (candidata.timeInMillis > agora) {
                return SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
                    .format(Date(candidata.timeInMillis))
            }

            meses++
        }
    }

    private fun rotuloTipo(tipoTrajeto: String): String =
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

    // ---------------------------------------------------------
    // DIAS DA SEMANA (os da rota em laranja, os outros em cinza)
    // ---------------------------------------------------------

    private fun montarDias(
        container: LinearLayout,
        diasDaRota: Set<String>
    ) {

        container.removeAllViews()

        ORDEM_DIAS.forEach { chave ->

            val ativo = chave in diasDaRota

            val quadrado = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f).apply {
                    marginStart = dp(2)
                    marginEnd = dp(2)
                }
                gravity = Gravity.CENTER
                text = ROTULO_DIAS[chave]
                textSize = 11f
                setTypeface(typeface, Typeface.BOLD)
                setBackgroundResource(
                    if (ativo) R.drawable.bg_dia_ativo else R.drawable.bg_dia_inativo
                )
                setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        if (ativo) android.R.color.white else R.color.light_gray
                    )
                )
            }

            container.addView(quadrado)
        }
    }

    private fun dp(valor: Int): Int =
        (valor * resources.displayMetrics.density).toInt()
}