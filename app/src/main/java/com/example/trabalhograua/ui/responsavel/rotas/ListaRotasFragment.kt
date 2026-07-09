package com.example.trabalhograua.ui.responsavel.rotas

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import com.example.trabalhograua.AcompanharViagemFragment
import com.example.trabalhograua.R

private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

class ListaRotasFragment : Fragment() {

    private var param1: String? = null
    private var param2: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // 1. Infla o layout do fragment
        val view = inflater.inflate(
            R.layout.fragment_lista_rotas,
            container,
            false
        )

        // 2. Busca o LinearLayout (que funciona como botão) usando a view inflada
        val btnAcompanharViagem = view.findViewById<LinearLayout>(R.id.btnAcompanharViagem)

        // 3. Configura o clique para substituir pelo MotoristaFragment
        btnAcompanharViagem.setOnClickListener {
            val novoFragment = AcompanharViagemFragment()

            parentFragmentManager.beginTransaction()
                // Substitui o container atual pelo novo fragment do mapa
                .replace(R.id.fragmentContainer, novoFragment)
                // Permite que o usuário volte para a lista ao apertar o botão "Voltar" do celular
                .addToBackStack(null)
                .commit()
        }

        return view
    }

    companion object {
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            ListaRotasFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}