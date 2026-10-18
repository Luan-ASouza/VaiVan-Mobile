package com.example.vaivan.ui.motorista.rotas

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch

class MinhasRotasFragment : Fragment() {

    private lateinit var containerRotas: LinearLayout

    private lateinit var txtSemRotas: TextView

    private val rotaRepository: RotaRepository by lazy {

        val db =
            VaivanDatabase.getInstance(
                requireContext()
            )

        RotaRepository(
            rotaDao = db.rotaDao(),
            paradaRotaDao = db.paradaRotaDao(),
            routesClient = GoogleRoutesClient(requireContext())
        )
    }


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
                mostrarOpcoes(rota)
            }


            containerRotas.addView(
                itemView
            )
        }
    }


    // ---------------------------------------------------------
    // PAINÉIS (BOTTOM SHEET)
    // ---------------------------------------------------------

    /** Painel com as opções da rota: detalhes, trajeto e excluir. */
    private fun mostrarOpcoes(
        rota: RotaEntity
    ) {

        val sheet = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_rota_opcoes, null)
        sheet.setContentView(view)

        // Deixa o fundo padrão transparente para aparecerem os cantos arredondados
        (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)

        view.findViewById<TextView>(R.id.txtSheetTitulo).text = rota.nome

        view.findViewById<View>(R.id.btnSheetDetalhes).setOnClickListener {
            sheet.dismiss()

            val intent =
                Intent(
                    requireContext(),
                    RotaDetalheActivity::class.java
                )

            intent.putExtra(
                RotaDetalheActivity.EXTRA_ROTA_ID,
                rota.id
            )

            startActivity(intent)
        }

        view.findViewById<View>(R.id.btnSheetTrajeto).setOnClickListener {
            sheet.dismiss()

            val intent =
                Intent(
                    requireContext(),
                    VisualizarRotaActivity::class.java
                )

            intent.putExtra(
                VisualizarRotaActivity.EXTRA_ROTA_ID,
                rota.id
            )

            startActivity(intent)
        }

        view.findViewById<View>(R.id.btnSheetExcluir).setOnClickListener {
            sheet.dismiss()
            confirmarExclusao(rota)
        }

        sheet.show()
    }


    /** Painel de confirmação antes de apagar a rota. */
    private fun confirmarExclusao(
        rota: RotaEntity
    ) {

        val aviso =
            if (rota.vagasOcupadas > 0)
                "Esta rota já tem ${rota.vagasOcupadas} passageiro(s) " +
                        "vinculado(s). Eles perderão a vaga.\n\n" +
                        "Essa ação não pode ser desfeita."
            else
                "Essa ação não pode ser desfeita."

        val sheet = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_confirmar_exclusao, null)
        sheet.setContentView(view)

        (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)

        view.findViewById<TextView>(R.id.txtConfirmarTitulo).text =
            "Excluir \"${rota.nome}\"?"

        view.findViewById<TextView>(R.id.txtConfirmarMensagem).text = aviso

        view.findViewById<View>(R.id.btnCancelarExcluir).setOnClickListener {
            sheet.dismiss()
        }

        view.findViewById<View>(R.id.btnConfirmarExcluir).setOnClickListener {
            sheet.dismiss()

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    rotaRepository.excluirRota(rota.id)

                    Toast.makeText(
                        requireContext(),
                        "Rota excluída.",
                        Toast.LENGTH_SHORT
                    ).show()

                } catch (e: Exception) {

                    Toast.makeText(
                        requireContext(),
                        "Erro ao excluir: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        sheet.show()
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