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
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.example.vaivan.R
import com.example.vaivan.ui.responsavel.passageiros.perfil.PerfilPassageiroActivity
import com.example.vaivan.core.util.ConfirmacaoExclusaoHelper
import com.example.vaivan.core.util.DataUtil
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.entities.PassageiroEntity
import com.example.vaivan.data.repository.PassageiroRepository
import com.example.vaivan.ui.responsavel.rotas.PesquisarRotasFragment
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.launch

class ListaPassageirosFragment : Fragment() {

    private lateinit var containerConfirmados: LinearLayout
    private lateinit var txtSemPassageiros: TextView
    private lateinit var swipeRefresh: SwipeRefreshLayout

    private val viewModel: ListaPassageirosViewModel by viewModels {
        viewModelFactory {
            initializer {
                val database = VaivanDatabase.getInstance(requireContext())
                ListaPassageirosViewModel(
                    PassageiroRepository(
                        passageiroDao = database.passageiroDao(),
                        paradaRotaDao = database.paradaRotaDao(),
                        solicitacaoInclusaoDao = database.solicitacaoInclusaoDao()
                    )
                )
            }
        }
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
        containerConfirmados = view.findViewById(R.id.containerConfirmados)
        txtSemPassageiros = view.findViewById(R.id.txtSemPassageiros)

        swipeRefresh = view.findViewById(R.id.swipeRefreshPassageiros)
        swipeRefresh.setOnRefreshListener {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    viewModel.atualizarPassageiros()
                } finally {
                    swipeRefresh.isRefreshing = false
                }
            }
        }
    }

    private fun configurarBotoes() {
        val btnNovoPassageiro = view?.findViewById<LinearLayout>(R.id.btnNovoPassageiro) ?: return

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
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.passageiros.collect { passageiros ->
                    renderizarLista(passageiros)
                }
            }
        }
    }

    private fun renderizarLista(passageiros: List<PassageiroEntity>) {
        containerConfirmados.removeAllViews()

        if (passageiros.isEmpty()) {
            txtSemPassageiros.visibility = View.VISIBLE
            return
        }

        txtSemPassageiros.visibility = View.GONE

        val inflater = LayoutInflater.from(requireContext())

        for (passageiro in passageiros) {
            val itemView = inflater.inflate(
                R.layout.item_passageiro,
                containerConfirmados,
                false
            )

            configurarItemPassageiro(itemView, passageiro)
            containerConfirmados.addView(itemView)
        }
    }

    private fun configurarItemPassageiro(
        itemView: View,
        passageiro: PassageiroEntity
    ) {
        val linhaPrincipal = itemView.findViewById<LinearLayout>(R.id.linhaPrincipalItem)
        val imgFoto = itemView.findViewById<ShapeableImageView>(R.id.imgFotoPassageiroItem)
        val txtNome = itemView.findViewById<TextView>(R.id.txtNomePassageiroItem)
        val txtIdade = itemView.findViewById<TextView>(R.id.txtIdadePassageiroItem)
        val imgEditar = itemView.findViewById<ImageView>(R.id.imgEditarPassageiroItem)
        val imgExcluir = itemView.findViewById<ImageView>(R.id.imgExcluirPassageiroItem)
        val imgChevron = itemView.findViewById<ImageView>(R.id.imgChevronItem)
        val containerDetalhes = itemView.findViewById<LinearLayout>(R.id.containerDetalhesItem)
        val txtNecessidade = itemView.findViewById<TextView>(R.id.txtNecessidadePassageiroItem)
        val txtObservacoes = itemView.findViewById<TextView>(R.id.txtObservacoesPassageiroItem)
        val btnBuscarRota = itemView.findViewById<Button>(R.id.btnBuscarRotaItem)

        // Informações Principais usando String Resources
        txtNome.text = passageiro.nome

        Glide.with(imgFoto)
            .load(passageiro.fotoUrl)
            .placeholder(R.drawable.avatar_01)
            .error(R.drawable.avatar_01)
            .circleCrop()
            .into(imgFoto)

        val idade = DataUtil.calcularIdade(passageiro.dataNascimento)
        txtIdade.text = if (idade >= 0) {
            getString(R.string.idade_anos, idade)
        } else {
            getString(R.string.idade_indisponivel)
        }

        // Necessidade Especial
        if (passageiro.necessidadesEspeciais) {
            txtNecessidade.visibility = View.VISIBLE
            txtNecessidade.text = if (!passageiro.descricaoNecessidades.isNullOrBlank()) {
                getString(R.string.necessidade_especial_descricao, passageiro.descricaoNecessidades)
            } else {
                getString(R.string.necessidade_especial_padrao)
            }
        } else {
            txtNecessidade.visibility = View.GONE
        }

        // Observações
        if (!passageiro.observacoes.isNullOrBlank()) {
            txtObservacoes.visibility = View.VISIBLE
            txtObservacoes.text = getString(R.string.observacoes_descricao, passageiro.observacoes)
        } else {
            txtObservacoes.visibility = View.GONE
        }

        // Expansão do Card
        val temDetalhes = passageiro.necessidadesEspeciais || !passageiro.observacoes.isNullOrBlank()

        imgChevron.visibility = if (temDetalhes) View.VISIBLE else View.INVISIBLE

        linhaPrincipal.setOnClickListener {
            if (!temDetalhes) return@setOnClickListener

            val vaiExpandir = containerDetalhes.visibility != View.VISIBLE

            containerDetalhes.visibility = if (vaiExpandir) View.VISIBLE else View.GONE

            imgChevron.animate()
                .rotation(if (vaiExpandir) 180f else 0f)
                .setDuration(150)
                .start()
        }

        // Buscar Rota
        btnBuscarRota.setOnClickListener {
            abrirPesquisaRotas(passageiro)
        }

        // Editar: abre o perfil do passageiro (cada campo tem sua tela)
        imgEditar.setOnClickListener {
            startActivity(PerfilPassageiroActivity.intent(requireContext(), passageiro.id))
        }

        // Excluir
        imgExcluir.setOnClickListener {
            ConfirmacaoExclusaoHelper.confirmar(
                contexto = requireContext(),
                titulo = "Excluir \"${passageiro.nome}\"?",
                mensagem = "Isso remove o passageiro e sua vaga em qualquer rota. Essa ação não pode ser desfeita."
            ) {
                excluirPassageiro(passageiro)
            }
        }
    }

    private fun excluirPassageiro(passageiro: PassageiroEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                viewModel.excluirPassageiro(passageiro.id)

                Toast.makeText(requireContext(), "Passageiro excluído.", Toast.LENGTH_SHORT).show()

            } catch (erro: Exception) {
                Toast.makeText(requireContext(), "Erro ao excluir: ${erro.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun abrirPesquisaRotas(passageiro: PassageiroEntity) {
        val fragment = PesquisarRotasFragment()

        fragment.arguments = Bundle().apply {
            putString("passageiroId", passageiro.id)
            putString("nomePassageiro", passageiro.nome)
        }

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }
}