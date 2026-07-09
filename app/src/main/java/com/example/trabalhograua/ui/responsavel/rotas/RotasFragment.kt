package com.example.trabalhograua.ui.responsavel.rotas

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import com.example.trabalhograua.MotoristaActivity
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

        val view = inflater.inflate(
            R.layout.fragment_lista_rotas,
            container,
            false
        )

        val btnRota = view.findViewById<LinearLayout>(R.id.Button_rota)

        btnRota.setOnClickListener {
            startActivity(Intent(requireContext(), MotoristaActivity::class.java))
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