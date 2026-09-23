package com.example.vaivan.ui.responsavel.rotas

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.vaivan.R
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.repository.PassageiroRepository
import com.example.vaivan.data.repository.RotaRepository
import com.example.vaivan.data.repository.UsuarioRepository
import com.example.vaivan.data.repository.VeiculoRepository
import com.example.vaivan.domain.usecase.rota.BuscarRotasDoResponsavelUseCase
import com.example.vaivan.data.models.RotasDoResponsavel
import com.example.vaivan.data.remote.routes.GoogleRoutesClient
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class ListaRotasFragment : Fragment() {

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
                db.UsuarioDao()
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

        view.findViewById<View>(
            R.id.btnAcompanharViagem
        ).setOnClickListener {

            parentFragmentManager
                .beginTransaction()
                .replace(
                    R.id.fragmentContainer,
                    AcompanharViagemFragment()
                )
                .addToBackStack(null)
                .commit()
        }

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

    private fun mostrarRotas(
        rotas: List<RotasDoResponsavel>
    ) {

        containerRotas.removeAllViews()

        rotas.forEach { item ->

            val rotaView =
                layoutInflater.inflate(
                    R.layout.item_rota_responsavel,
                    containerRotas,
                    false
                )

            preencherRota(
                rotaView,
                item
            )

            containerRotas.addView(
                rotaView
            )
        }
    }

    private fun preencherRota(
        view: View,
        item: RotasDoResponsavel
    ) {

        val rota = item.rota
        val motorista = item.motorista
        val veiculo = item.veiculo

        view.findViewById<TextView>(
            R.id.txtNomeMotorista
        ).text =
            motorista?.nome
                ?: "Motorista não informado"

        view.findViewById<TextView>(
            R.id.txtRotaTurno
        ).text =
            rota.turno

        view.findViewById<TextView>(
            R.id.txtRotaDias
        ).text =
            rota.diasSemana

        view.findViewById<TextView>(
            R.id.txtRotaEnderecoDesembarque
        ).text =
            rota.destinoEndereco

        view.findViewById<TextView>(
            R.id.txtPlacaVeiculo
        ).text =
            veiculo?.placa
                ?: "Sem veículo"

        view.findViewById<TextView>(
            R.id.txtVeiculoModelo
        ).text =
            veiculo?.modelo
                ?: "Veículo não informado"

        view.findViewById<TextView>(
            R.id.txtVeiculoLugares
        ).text =
            veiculo?.capacidadePassageiros?.toString()
                ?: "-"

        val containerPassageiros =
            view.findViewById<LinearLayout>(
                R.id.containerPassageiros
            )

        containerPassageiros.removeAllViews()
    }

    private fun formatarHorario(
        minutos: Int
    ): String {

        val horas =
            minutos / 60

        val minutosRestantes =
            minutos % 60

        return String.format(
            "%02d:%02d",
            horas,
            minutosRestantes
        )
    }
}