package com.example.vaivan.ui.motorista.veiculos

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.vaivan.R
import com.example.vaivan.core.util.ConfirmacaoExclusaoHelper
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.entities.VeiculoEntity
import com.example.vaivan.data.repository.VeiculoRepository
import com.example.vaivan.data.repository.VeiculoVinculadoARotaException
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import androidx.fragment.app.viewModels

class ListaVeiculosFragment : Fragment() {

    private lateinit var containerConfirmados: LinearLayout
    private lateinit var txtSemVeiculos: TextView

    private val viewModel: ListaVeiculosViewModel by viewModels {
        viewModelFactory {
            initializer {
                val database = VaivanDatabase.getInstance(requireContext())
                ListaVeiculosViewModel(
                    VeiculoRepository(
                        veiculoDao = database.veiculoDao(),
                        documentoDao = database.documentoDao()
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
            R.layout.fragment_lista_veiculos,
            container,
            false
        )
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        val btnNovoVeiculo =
            view.findViewById<LinearLayout>(R.id.btnNovoVeiculo)

        containerConfirmados =
            view.findViewById(R.id.containerConfirmados)

        txtSemVeiculos =
            view.findViewById(R.id.txtSemVeiculos)

        btnNovoVeiculo.setOnClickListener {
            startActivity(
                Intent(
                    requireContext(),
                    CadastroVeiculoActivity::class.java
                )
            )
        }

        observarVeiculos()
    }

    private fun observarVeiculos() {

        viewLifecycleOwner.lifecycleScope.launch {

            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {

                viewModel.veiculos.collect { veiculos ->

                    renderizarLista(veiculos)
                }
            }
        }
    }

    private fun renderizarLista(
        veiculos: List<VeiculoEntity>
    ) {
        containerConfirmados.removeAllViews()

        if (veiculos.isEmpty()) {
            txtSemVeiculos.visibility = View.VISIBLE
            return
        }

        txtSemVeiculos.visibility = View.GONE

        val inflater = LayoutInflater.from(requireContext())

        for (veiculo in veiculos) {

            val itemView = inflater.inflate(
                R.layout.item_veiculo,
                containerConfirmados,
                false
            )

            val linhaPrincipal =
                itemView.findViewById<LinearLayout>(
                    R.id.linhaPrincipalItem
                )

            val imgChevron =
                itemView.findViewById<ImageView>(
                    R.id.imgChevronItem
                )

            val imgEditar =
                itemView.findViewById<ImageView>(
                    R.id.imgEditarVeiculoItem
                )

            val imgExcluir =
                itemView.findViewById<ImageView>(
                    R.id.imgExcluirVeiculoItem
                )

            val containerDetalhes =
                itemView.findViewById<LinearLayout>(
                    R.id.containerDetalhesItem
                )

            val txtPlaca =
                itemView.findViewById<TextView>(
                    R.id.txtPlacaVeiculoItem
                )

            val txtModelo =
                itemView.findViewById<TextView>(
                    R.id.txtModeloVeiculoItem
                )

            val txtMarca =
                itemView.findViewById<TextView>(
                    R.id.txtMarcaVeiculoItem
                )

            val txtCor =
                itemView.findViewById<TextView>(
                    R.id.txtCorVeiculoItem
                )

            val txtAno =
                itemView.findViewById<TextView>(
                    R.id.txtAnoVeiculoItem
                )

            val txtCapacidade =
                itemView.findViewById<TextView>(
                    R.id.txtCapacidadeVeiculoItem
                )

            val txtStatus =
                itemView.findViewById<TextView>(
                    R.id.txtStatusVeiculoItem
                )


            // ==========================
            // INFORMAÇÕES PRINCIPAIS
            // ==========================

            txtPlaca.text =
                if (veiculo.placa.isNotBlank()) {
                    veiculo.placa
                } else {
                    "Placa não informada"
                }

            txtModelo.text = when {
                veiculo.marca.isNotBlank() &&
                        veiculo.modelo.isNotBlank() -> {
                    "${veiculo.marca} ${veiculo.modelo}"
                }

                veiculo.modelo.isNotBlank() -> {
                    veiculo.modelo
                }

                veiculo.marca.isNotBlank() -> {
                    veiculo.marca
                }

                else -> {
                    "Veículo sem modelo informado"
                }
            }


            // ==========================
            // DETALHES
            // ==========================

            txtMarca.text =
                if (veiculo.marca.isNotBlank()) {
                    "Marca: ${veiculo.marca}"
                } else {
                    "Marca: Não informada"
                }

            txtCor.text =
                if (veiculo.cor.isNotBlank()) {
                    "Cor: ${veiculo.cor}"
                } else {
                    "Cor: Não informada"
                }

            txtAno.text =
                if (veiculo.anoFabricacao > 0) {
                    "Ano de fabricação: ${veiculo.anoFabricacao}"
                } else {
                    "Ano de fabricação: Não informado"
                }

            txtCapacidade.text =
                if (veiculo.capacidadePassageiros > 0) {
                    "Capacidade: ${veiculo.capacidadePassageiros} passageiros"
                } else {
                    "Capacidade: Não informada"
                }

            txtStatus.text =
                if (veiculo.status.isNotBlank()) {
                    "Status: ${veiculo.status}"
                } else {
                    "Status: Não informado"
                }


            // ==========================
            // EXPANSÃO
            // ==========================

            containerDetalhes.visibility = View.GONE
            imgChevron.rotation = 0f

            linhaPrincipal.setOnClickListener {

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
                        if (vaiExpandir) 180f else 0f
                    )
                    .setDuration(150)
                    .start()
            }

            imgEditar.setOnClickListener {
                startActivity(
                    Intent(requireContext(), CadastroVeiculoActivity::class.java)
                        .putExtra(CadastroVeiculoActivity.EXTRA_VEICULO_ID, veiculo.id)
                )
            }

            imgExcluir.setOnClickListener {
                ConfirmacaoExclusaoHelper.confirmar(
                    contexto = requireContext(),
                    titulo = "Excluir \"${veiculo.placa.ifBlank { "este veículo" }}\"?",
                    mensagem = "Essa ação não pode ser desfeita."
                ) {
                    excluirVeiculo(veiculo)
                }
            }

            containerConfirmados.addView(itemView)
        }
    }

    private fun excluirVeiculo(veiculo: VeiculoEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                viewModel.excluirVeiculo(veiculo.id)

                Toast.makeText(requireContext(), "Veículo excluído.", Toast.LENGTH_SHORT).show()

            } catch (erro: VeiculoVinculadoARotaException) {
                Toast.makeText(requireContext(), erro.message, Toast.LENGTH_LONG).show()

            } catch (erro: Exception) {
                Toast.makeText(requireContext(), "Erro ao excluir: ${erro.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
