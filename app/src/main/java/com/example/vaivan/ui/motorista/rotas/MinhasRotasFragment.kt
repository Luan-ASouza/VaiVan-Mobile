package com.example.vaivan.ui.motorista.rotas

import android.content.Intent
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
import com.example.vaivan.data.local.entities.RotaEntity
import com.example.vaivan.data.remote.routes.GoogleRoutesClient
import com.example.vaivan.data.repository.RotaRepository
import kotlinx.coroutines.launch

class MinhasRotasFragment : Fragment() {

    private lateinit var containerRotas: LinearLayout

    private lateinit var txtSemRotas: TextView


    private val viewModel: MinhasRotasViewModel by lazy {

        val db =
            VaivanDatabase.getInstance(
                requireContext()
            )

        val repository =
            RotaRepository(

                rotaDao =
                    db.rotaDao(),

                paradaRotaDao =
                    db.paradaRotaDao(),

                routesClient =
                    GoogleRoutesClient(
                        requireContext()
                    )
            )

        ViewModelProvider(
            this,

            object :
                ViewModelProvider.Factory {

                override fun <T : ViewModel>
                        create(
                    modelClass: Class<T>
                ): T {

                    return MinhasRotasViewModel(
                        repository
                    ) as T
                }
            }

        )[MinhasRotasViewModel::class.java]
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        return inflater.inflate(
            R.layout.fragment_minhas_rotas,
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


        containerRotas =
            view.findViewById(
                R.id.containerRotas
            )

        txtSemRotas =
            view.findViewById(
                R.id.txtSemRotas
            )


        view
            .findViewById<LinearLayout>(
                R.id.btnNovaRota
            )
            .setOnClickListener {

                startActivity(
                    Intent(
                        requireContext(),
                        CriarRotaActivity::class.java
                    )
                )
            }


        observarRotas()
    }


    private fun observarRotas() {

        viewLifecycleOwner
            .lifecycleScope
            .launch {

                viewLifecycleOwner
                    .repeatOnLifecycle(
                        Lifecycle.State.STARTED
                    ) {

                        viewModel
                            .rotas
                            .collect { rotas ->

                                renderizar(
                                    rotas
                                )
                            }
                    }
            }
    }


    private fun renderizar(
        rotas: List<RotaEntity>
    ) {

        containerRotas.removeAllViews()


        if (rotas.isEmpty()) {

            txtSemRotas.visibility =
                View.VISIBLE

            return
        }


        txtSemRotas.visibility =
            View.GONE


        val inflater =
            LayoutInflater.from(
                requireContext()
            )


        rotas.forEach { rota ->

            val itemView =
                inflater.inflate(
                    R.layout.item_rota_motorista,
                    containerRotas,
                    false
                )


            itemView
                .findViewById<TextView>(
                    R.id.txtNomeRotaItem
                )
                .text =
                rota.nome


            itemView
                .findViewById<TextView>(
                    R.id.txtDetalheRotaItem
                )
                .text =
                "${traduzirTurno(rota.turno)} · " +
                        rota.diasSemana
                            .replace(
                                ",",
                                ", "
                            )


            itemView
                .findViewById<TextView>(
                    R.id.txtVagasRotaItem
                )
                .text =
                "Vagas: " +
                        "${rota.vagasOcupadas}/" +
                        rota.capacidadeTotal


            itemView.setOnClickListener {

                val intent =
                    Intent(
                        requireContext(),
                        RotaDetalheActivity::class.java
                    )

                intent.putExtra(
                    RotaDetalheActivity.EXTRA_ROTA_ID,
                    rota.id
                )

                startActivity(
                    intent
                )
            }


            containerRotas.addView(
                itemView
            )
        }
    }


    private fun traduzirTurno(
        turno: String
    ): String {

        return when (turno) {

            "MANHA" ->
                "Manhã"

            "TARDE" ->
                "Tarde"

            "NOITE" ->
                "Noite"

            else ->
                turno
        }
    }
}