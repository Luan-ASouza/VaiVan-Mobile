package com.example.trabalhograua.ui.responsavel.perfil

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.trabalhograua.R
import com.example.trabalhograua.repository.FirebaseRepository
import com.google.firebase.auth.FirebaseAuth

class PerfilFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(
            R.layout.fragment_perfil,
            container,
            false
        )

        val txtNome = view.findViewById<TextView>(R.id.txtNomeUsuarioCompleto)

        val uid = FirebaseAuth.getInstance().currentUser?.uid

        if (uid != null) {

            FirebaseRepository().buscarUsuario(

                uid,

                { usuario ->

                    txtNome.text = usuario.nome

                },

                {

                }

            )

        }

        return view
    }

}