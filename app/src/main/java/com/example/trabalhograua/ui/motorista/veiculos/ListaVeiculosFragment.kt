package com.example.trabalhograua.ui.motorista.veiculos

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
import com.example.trabalhograua.data.local.entities.VeiculoEntity
import com.example.trabalhograua.data.repository.VeiculoRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class ListaVeiculosFragment : Fragment() {

    private lateinit var containerConfirmados: LinearLayout
    private lateinit var txtSemVeiculos: TextView

    private val repository by lazy {
        VeiculoRepository(
            VaivanDatabase.getInstance(requireContext())
                .veiculoDao()
        )
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

        val uid = FirebaseAuth
            .getInstance()
            .currentUser
            ?.uid
            ?: return

        // Inicia a sincronização do Firestore
        // com o banco local.
        repository.iniciarSincronizacao()

        viewLifecycleOwner.lifecycleScope.launch {

            repeatOnLifecycle(Lifecycle.State.STARTED) {

                repository
                    .observarPorMotorista(uid)
                    .collect { veiculos ->

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

            containerConfirmados.addView(itemView)
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()

        repository.pararSincronizacao()
    }
}
