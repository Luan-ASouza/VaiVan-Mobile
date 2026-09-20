package com.example.vaivan.ui.responsavel.perfil

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider // Nativo do Android
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.vaivan.ui.inicio.LoginActivity
import com.example.vaivan.R
import com.example.vaivan.data.local.VaivanDatabase // Substitui pelo teu banco do Room
import com.example.vaivan.data.repository.UsuarioRepository
import com.example.vaivan.ui.responsavel.ResponsavelViewModel
import com.example.vaivan.ui.responsavel.passageiros.AdicionarLocalActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class PerfilFragment : Fragment() {

    // 1. Declaras a variável do ViewModel
    private lateinit var viewModel: ResponsavelViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_perfil, container, false)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        // =========================================================
        // BANCO / REPOSITÓRIO / VIEWMODEL
        // =========================================================

        val database =
            VaivanDatabase.getInstance(
                requireContext()
            )

        val UsuarioDao =
            database.UsuarioDao()

        val repository =
            UsuarioRepository(
                UsuarioDao
            )

        val factory =
            object : ViewModelProvider.Factory {

                override fun <T : androidx.lifecycle.ViewModel> create(
                    modelClass: Class<T>
                ): T {

                    return ResponsavelViewModel(
                        repository
                    ) as T
                }
            }

        viewModel =
            ViewModelProvider(
                this,
                factory
            )[ResponsavelViewModel::class.java]

        // =========================================================
        // VIEWS
        // =========================================================

        val txtNome =
            view.findViewById<TextView>(
                R.id.txtNomeUsuarioCompleto
            )

        val txtNascimento =
            view.findViewById<TextView>(
                R.id.txtNascimento
            )

        val txtCPF =
            view.findViewById<TextView>(
                R.id.txtCPF
            )

        val btnLocais =
            view.findViewById<TextView>(
                R.id.btnLocais
            )

        val btnDesconectar =
            view.findViewById<MaterialButton>(
                R.id.btnDesconectar
            )

        // =========================================================
        // UID
        // =========================================================

        val uid =
            FirebaseAuth
                .getInstance()
                .currentUser
                ?.uid

        // =========================================================
        // BOTÃO LOCAIS
        // =========================================================

        btnLocais.setOnClickListener {

            startActivity(
                Intent(
                    requireContext(),
                    AdicionarLocalActivity::class.java
                )
            )
        }

        // =========================================================
        // DESCONECTAR
        // =========================================================

        btnDesconectar.setOnClickListener {

            viewLifecycleOwner.lifecycleScope.launch {

                /*
                 * Limpa o Room fora da Main Thread.
                 */
                withContext(Dispatchers.IO) {

                    database.clearAllTables()
                }

                /*
                 * Desconecta do Firebase.
                 */
                FirebaseAuth
                    .getInstance()
                    .signOut()

                /*
                 * Volta para o Login.
                 */
                val intent =
                    Intent(
                        requireContext(),
                        LoginActivity::class.java
                    )

                intent.flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK

                startActivity(intent)
            }
        }

        // =========================================================
        // OBSERVAR USUÁRIO PELO VIEWMODEL
        // =========================================================

        if (uid != null) {

            viewLifecycleOwner.lifecycleScope.launch {

                viewLifecycleOwner.repeatOnLifecycle(
                    Lifecycle.State.STARTED
                ) {

                    viewModel
                        .observarPorId(uid)
                        .collect { responsavel ->

                            if (responsavel != null) {

                                /*
                                 * Nome
                                 */
                                txtNome.text =
                                    responsavel.nome

                                /*
                                 * CPF
                                 */
                                txtCPF.text =
                                    responsavel.cpf

                                /*
                                 * Data de nascimento
                                 */
                                responsavel.dataNascimento
                                    ?.let { timestamp ->

                                        val date =
                                            timestamp.toDate()

                                        val formato =
                                            SimpleDateFormat(
                                                "dd / MM / yyyy",
                                                Locale.getDefault()
                                            )

                                        txtNascimento.text =
                                            formato.format(
                                                date
                                            )
                                    }
                            }
                        }
                }
            }
        }
    }
}