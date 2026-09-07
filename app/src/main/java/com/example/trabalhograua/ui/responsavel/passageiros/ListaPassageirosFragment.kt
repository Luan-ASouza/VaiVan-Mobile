package com.example.trabalhograua.ui.responsavel.passageiros

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.trabalhograua.R
import com.example.trabalhograua.data.local.VaivanDatabase
import com.example.trabalhograua.data.local.entities.PassageiroEntity
import com.example.trabalhograua.data.repository.PassageiroRepository
import com.example.trabalhograua.util.DataUtil
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/**
 * Tela "Seus Passageiros": lista os filhos (menores de idade) já
 * cadastrados pelo responsável logado (RF3/RF4).
 *
 * Os dados vêm do PassageiroRepository, que sincroniza com o Firestore
 * e mantém uma cópia local (Room) para a tela continuar funcionando
 * mesmo com internet instável.
 */
class ListaPassageirosFragment : Fragment() {

    private lateinit var containerConfirmados: LinearLayout
    private lateinit var txtSemPassageiros: TextView

    private val repository by lazy {
        PassageiroRepository(VaivanDatabase.getInstance(requireContext()).passageiroDao())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_lista_passageiros, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnNovoPassageiro = view.findViewById<LinearLayout>(R.id.btnNovoPassageiro)
        containerConfirmados = view.findViewById(R.id.containerConfirmados)
        txtSemPassageiros = view.findViewById(R.id.txtSemPassageiros)

        btnNovoPassageiro.setOnClickListener {
            startActivity(Intent(requireContext(), AdicionarPassageiroActivity::class.java))
        }

        observarPassageiros()
    }

    private fun observarPassageiros() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // Puxa do Firestore para o cache local assim que a tela abre.
        repository.iniciarSincronizacao()

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.observarPorResponsavel(uid).collect { passageiros ->
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
            val itemView = inflater.inflate(R.layout.item_passageiro, containerConfirmados, false)

            val linhaPrincipal = itemView.findViewById<LinearLayout>(R.id.linhaPrincipalItem)
            val txtNome = itemView.findViewById<TextView>(R.id.txtNomePassageiroItem)
            val txtIdade = itemView.findViewById<TextView>(R.id.txtIdadePassageiroItem)
            val imgChevron = itemView.findViewById<ImageView>(R.id.imgChevronItem)
            val containerDetalhes = itemView.findViewById<LinearLayout>(R.id.containerDetalhesItem)
            val txtNecessidade = itemView.findViewById<TextView>(R.id.txtNecessidadePassageiroItem)
            val txtObservacoes = itemView.findViewById<TextView>(R.id.txtObservacoesPassageiroItem)

            txtNome.text = passageiro.nome

            val idade = DataUtil.calcularIdade(passageiro.dataNascimento)
            txtIdade.text = if (idade >= 0) "$idade anos" else "Idade indisponível"

            if (passageiro.necessidadesEspeciais) {
                txtNecessidade.visibility = View.VISIBLE
                txtNecessidade.text = if (passageiro.descricaoNecessidades.isNotBlank()) {
                    "Necessidade especial: ${passageiro.descricaoNecessidades}"
                } else {
                    "Possui necessidade especial"
                }
            } else {
                txtNecessidade.visibility = View.GONE
            }

            if (passageiro.observacoes.isNotBlank()) {
                txtObservacoes.visibility = View.VISIBLE
                txtObservacoes.text = "Observações: ${passageiro.observacoes}"
            } else {
                txtObservacoes.visibility = View.GONE
            }

            // Só mostra a setinha se tiver algum detalhe pra exibir
            val temDetalhes = passageiro.necessidadesEspeciais || passageiro.observacoes.isNotBlank()
            imgChevron.visibility = if (temDetalhes) View.VISIBLE else View.INVISIBLE

            linhaPrincipal.setOnClickListener {
                if (!temDetalhes) return@setOnClickListener

                val vaiExpandir = containerDetalhes.visibility != View.VISIBLE
                containerDetalhes.visibility = if (vaiExpandir) View.VISIBLE else View.GONE
                imgChevron.animate().rotation(if (vaiExpandir) 180f else 0f).setDuration(150).start()
            }

            containerConfirmados.addView(itemView)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        repository.pararSincronizacao()
    }
}