package com.example.vaivan.ui.responsavel.passageiros

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.ViewModelProvider
import com.example.vaivan.R
import com.example.vaivan.core.util.DataUtil
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.repository.PassageiroRepository
import com.example.vaivan.data.local.entities.PassageiroEntity
import com.example.vaivan.ui.responsavel.rotas.PesquisarRotasFragment
import kotlinx.coroutines.launch

class ListaPassageirosFragment : Fragment() {

    private lateinit var containerConfirmados: LinearLayout
    private lateinit var txtSemPassageiros: TextView
    private val viewModel: ListaPassageirosViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository =
            PassageiroRepository(
                VaivanDatabase
                    .getInstance(requireContext())
                    .passageiroDao()
            )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(
            R.layout.fragment_lista_passageiros,
            container,
            false
        )
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        configurarViews(view)
        configurarBotoes()
        observarPassageiros()
    }

    private fun configurarViews(view: View) {

        containerConfirmados =
            view.findViewById(R.id.containerConfirmados)

        txtSemPassageiros =
            view.findViewById(R.id.txtSemPassageiros)
    }

    private fun configurarBotoes() {

        val btnNovoPassageiro =
            view?.findViewById<LinearLayout>(R.id.btnNovoPassageiro)
                ?: return

        btnNovoPassageiro.setOnClickListener {
            startActivity(
                Intent(
                    requireContext(),
                    AdicionarPassageiroActivity::class.java
                )
            )
        }
    }

    private fun observarPassageiros() {

        viewLifecycleOwner.lifecycleScope.launch {

            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {

                viewModel.passageiros.collect { passageiros ->

                    renderizarLista(passageiros)
                }
            }
        }
    }

    private fun renderizarLista(
        passageiros: List<PassageiroEntity>
    ) {

        containerConfirmados.removeAllViews()

        if (passageiros.isEmpty()) {

            txtSemPassageiros.visibility = View.VISIBLE

            return
        }

        txtSemPassageiros.visibility = View.GONE

        val inflater =
            LayoutInflater.from(requireContext())

        for (passageiro in passageiros) {

            val itemView =
                inflater.inflate(
                    R.layout.item_passageiro,
                    containerConfirmados,
                    false
                )

            configurarItemPassageiro(
                itemView,
                passageiro
            )

            containerConfirmados.addView(itemView)
        }
    }

    private fun configurarItemPassageiro(
        itemView: View,
        passageiro: PassageiroEntity
    ) {

        val linhaPrincipal =
            itemView.findViewById<LinearLayout>(
                R.id.linhaPrincipalItem
            )

        val txtNome =
            itemView.findViewById<TextView>(
                R.id.txtNomePassageiroItem
            )

        val txtIdade =
            itemView.findViewById<TextView>(
                R.id.txtIdadePassageiroItem
            )

        val imgChevron =
            itemView.findViewById<ImageView>(
                R.id.imgChevronItem
            )

        val containerDetalhes =
            itemView.findViewById<LinearLayout>(
                R.id.containerDetalhesItem
            )

        val txtNecessidade =
            itemView.findViewById<TextView>(
                R.id.txtNecessidadePassageiroItem
            )

        val txtObservacoes =
            itemView.findViewById<TextView>(
                R.id.txtObservacoesPassageiroItem
            )

        val btnBuscarRota =
            itemView.findViewById<Button>(
                R.id.btnBuscarRotaItem
            )

        // =========================
        // INFORMAÇÕES PRINCIPAIS
        // =========================

        txtNome.text = passageiro.nome

        val idade =
            DataUtil.calcularIdade(
                passageiro.dataNascimento
            )

        txtIdade.text =
            if (idade >= 0) {
                "$idade anos"
            } else {
                "Idade indisponível"
            }

        // =========================
        // NECESSIDADE ESPECIAL
        // =========================

        if (passageiro.necessidadesEspeciais) {

            txtNecessidade.visibility = View.VISIBLE

            txtNecessidade.text =
                if (!passageiro.descricaoNecessidades.isNullOrBlank()) {

                    "Necessidade especial: " +
                            passageiro.descricaoNecessidades

                } else {

                    "Possui necessidade especial"
                }

        } else {

            txtNecessidade.visibility = View.GONE
        }

        // =========================
        // OBSERVAÇÕES
        // =========================

        if (!passageiro.observacoes.isNullOrBlank()) {

            txtObservacoes.visibility = View.VISIBLE

            txtObservacoes.text =
                "Observações: " +
                        passageiro.observacoes

        } else {

            txtObservacoes.visibility = View.GONE
        }

        // =========================
        // EXPANSÃO
        // =========================

        val temDetalhes =
            passageiro.necessidadesEspeciais ||
                    !passageiro.observacoes.isNullOrBlank()

        imgChevron.visibility =
            if (temDetalhes) {
                View.VISIBLE
            } else {
                View.INVISIBLE
            }

        linhaPrincipal.setOnClickListener {

            if (!temDetalhes) {
                return@setOnClickListener
            }

            val vaiExpandir =
                containerDetalhes.visibility != View.VISIBLE

            containerDetalhes.visibility =
                if (vaiExpandir) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

            imgChevron
                .animate()
                .rotation(
                    if (vaiExpandir) {
                        180f
                    } else {
                        0f
                    }
                )
                .setDuration(150)
                .start()
        }

        // =========================
        // BUSCAR ROTA
        // =========================

        btnBuscarRota.setOnClickListener {

            abrirPesquisaRotas(passageiro)
        }
    }

    private fun abrirPesquisaRotas(
        passageiro: PassageiroEntity
    ) {

        val fragment =
            PesquisarRotasFragment()

        fragment.arguments =
            Bundle().apply {

                putString(
                    "passageiroId",
                    passageiro.id
                )

                putString(
                    "nomePassageiro",
                    passageiro.nome
                )

                putString(
                    "pontoEmbarqueId",
                    passageiro.pontoEmbarqueId
                )
            }

        parentFragmentManager
            .beginTransaction()
            .replace(
                R.id.fragmentContainer,
                fragment
            )
            .addToBackStack(null)
            .commit()
    }
}