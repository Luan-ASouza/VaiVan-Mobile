package com.example.vaivan.ui.responsavel.rotas

import android.content.res.ColorStateList
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
        configurarTurnos()
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
    }


    private fun configurarTurnos() {

        botoesTurno.forEach { (chave, botao) ->

            botao.setOnClickListener {

                turnoSelecionado =
                    chave

                atualizarCoresTurno()
            }
        }
    }


    private fun configurarBotoes() {

        view?.findViewById<Button>(
            R.id.btnPesquisarRotas
        )?.setOnClickListener {

            pesquisar()
        }
    }


    // =========================================================
    // TURNO
    // =========================================================

    private fun atualizarCoresTurno() {

        botoesTurno.forEach { (chave, botao) ->

            val cor =
                if (
                    chave ==
                    turnoSelecionado
                ) {

                    R.color.orange

                } else {

                    R.color.light_gray
                }

            botao.backgroundTintList =
                ColorStateList.valueOf(
                    ContextCompat.getColor(
                        requireContext(),
                        cor
                    )
                )
        }
    }


    // =========================================================
    // PESQUISAR ROTAS
    // =========================================================

    private fun pesquisar() {

        val textoBusca =
            edtDesembarque
                .text
                ?.toString()
                ?.trim()
                ?: ""

        viewLifecycleOwner.lifecycleScope.launch {

            try {

                val resultados =
                    rotaRepository
                        .buscarRotasDisponiveis(
                            textoBusca,
                            turnoSelecionado
                        )

                renderizarResultados(
                    resultados
                )

            } catch (e: Exception) {

                Toast.makeText(
                    requireContext(),
                    "Erro na busca: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }


    // =========================================================
    // RESULTADOS
    // =========================================================

    private fun renderizarResultados(
        rotas: List<RotaEntity>
    ) {

        containerResultados.removeAllViews()

        if (rotas.isEmpty()) {

            txtSemResultados.visibility =
                View.VISIBLE

            return
        }

        txtSemResultados.visibility =
            View.GONE

        rotas.forEach { rota ->

            adicionarItemRota(
                rota
            )
        }
    }


    private fun adicionarItemRota(
        rota: RotaEntity
    ) {

        val itemView =
            layoutInflater.inflate(
                R.layout.item_resultado_rota,
                containerResultados,
                false
            )

        val txtNomeRota =
            itemView.findViewById<TextView>(
                R.id.txtNomeRotaResultado
            )

        val txtVagas =
            itemView.findViewById<TextView>(
                R.id.txtVagasResultado
            )

        val txtNomeMotorista =
            itemView.findViewById<TextView>(
                R.id.txtNomeMotoristaResultado
            )

        val btnReservar =
            itemView.findViewById<Button>(
                R.id.btnReservarResultado
            )


        txtNomeRota.text =
            "${rota.nome} · Destino: ${rota.destinoEndereco}"

        txtVagas.text =
            "Vagas: ${rota.capacidadeTotal - rota.vagasOcupadas} disponíveis"

        txtNomeMotorista.text =
            "Carregando motorista..."


        viewLifecycleOwner.lifecycleScope.launch {

            try {

                val documento =
                    firestore
                        .collection("usuarios")
                        .document(
                            rota.motoristaId
                        )
                        .get()
                        .await()

                txtNomeMotorista.text =
                    documento.getString(
                        "nome"
                    ) ?: "Motorista"

            } catch (e: Exception) {

                txtNomeMotorista.text =
                    "Motorista"
            }
        }


        btnReservar.setOnClickListener {

            enviarSolicitacao(
                rota
            )
        }


        containerResultados.addView(
            itemView
        )
    }


    // =========================================================
    // SOLICITAÇÃO
    // =========================================================

    private fun enviarSolicitacao(
        rota: RotaEntity
    ) {

        val uid =
            auth.currentUser?.uid

        if (uid == null) {

            Toast.makeText(
                requireContext(),
                "Sessão expirada.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }


        if (
            passageiroId.isBlank() ||
            localId.isNullOrBlank()
        ) {

            Toast.makeText(
                requireContext(),
                "Este aluno precisa ter um local de embarque cadastrado.",
                Toast.LENGTH_LONG
            ).show()

            return
        }


        viewLifecycleOwner.lifecycleScope.launch {

            try {

                val local =
                    firestore
                        .collection("locais")
                        .document(
                            localId!!
                        )
                        .get()
                        .await()


                val nomeLocal =
                    local.getString(
                        "nome"
                    ) ?: "Local"


                val endereco =
                    local.getString(
                        "endereco"
                    ) ?: ""


                val latitude =
                    local.getDouble(
                        "latitude"
                    ) ?: 0.0


                val longitude =
                    local.getDouble(
                        "longitude"
                    ) ?: 0.0


                val solicitacao =
                    SolicitacaoInclusaoEntity(

                        rotaId =
                            rota.id,

                        passageiroId =
                            passageiroId,

                        nomePassageiro =
                            nomePassageiro,

                        responsavelId =
                            uid,

                        motoristaId =
                            rota.motoristaId,

                        localEmbarqueId =
                            localId!!,

                        nomeLocalEmbarque =
                            nomeLocal,

                        enderecoEmbarque =
                            endereco,

                        latitudeEmbarque =
                            latitude,

                        longitudeEmbarque =
                            longitude,

                        horarioDesejado =
                            edtHoraInicio
                                .text
                                ?.toString()
                                ?.trim()
                                ?: ""
                    )


                solicitacaoRepository
                    .enviarSolicitacao(
                        solicitacao
                    )


                Toast.makeText(
                    requireContext(),
                    "Solicitação enviada!",
                    Toast.LENGTH_SHORT
                ).show()

                requireActivity()
                    .onBackPressedDispatcher
                    .onBackPressed()


            } catch (e: Exception) {

                Toast.makeText(
                    requireContext(),
                    "Erro: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}