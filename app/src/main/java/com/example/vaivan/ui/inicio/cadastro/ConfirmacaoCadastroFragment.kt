package com.example.vaivan.ui.inicio.cadastro

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.vaivan.R
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.entities.UsuarioEntity
import com.example.vaivan.data.repository.FirebaseAuthRepository
import com.example.vaivan.data.repository.UsuarioRepository
import com.example.vaivan.ui.inicio.EscolhaTipoPerfilActivity
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class ConfirmacaoCadastroFragment :
    Fragment(R.layout.fragment_confirmacao_cadastro) {

    private lateinit var btnConfirmar: MaterialButton
    private lateinit var txtErro: TextView

    private lateinit var repository: UsuarioRepository
    private lateinit var authRepository: FirebaseAuthRepository

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        btnConfirmar =
            view.findViewById(R.id.btnConfirmar)

        txtErro =
            view.findViewById(R.id.txtErro)

        repository =
            UsuarioRepository(
                VaivanDatabase
                    .getInstance(requireContext())
                    .UsuarioDao()
            )

        authRepository =
            FirebaseAuthRepository()

        btnConfirmar.setOnClickListener {
            confirmarCadastro()
        }
    }

    private fun confirmarCadastro() {

        val cadastro =
            CadastroSession.cadastroUsuario

        btnConfirmar.isEnabled = false
        txtErro.visibility = View.GONE

        authRepository.cadastrar(
            cadastro.email,
            cadastro.senha,

            onSuccess = { uid ->
                salvarUsuario(uid)
            },

            onError = { erro ->

                btnConfirmar.isEnabled = true

                txtErro.text =
                    erro.message
                        ?: "*Erro ao criar a conta."

                txtErro.visibility =
                    View.VISIBLE
            }
        )
    }

    private fun salvarUsuario(uid: String) {

        val cadastro =
            CadastroSession.cadastroUsuario

        val usuario =
            UsuarioEntity(
                id = uid,

                nome = cadastro.nome,
                cpf = cadastro.cpf,
                email = cadastro.email,
                telefone = cadastro.telefone,

                dataNascimento =
                    cadastro.dataNascimento,

                cep =
                    cadastro.endereco.cep,

                estado =
                    cadastro.endereco.estado,

                cidade =
                    cadastro.endereco.cidade,

                bairro =
                    cadastro.endereco.bairro,

                rua =
                    cadastro.endereco.rua,

                numero =
                    cadastro.endereco.numero,

                complemento =
                    cadastro.endereco.complemento,

                status = "ATIVO",

                emailConfirmado = false,

                lastUpdated =
                    System.currentTimeMillis()
            )

        viewLifecycleOwner.lifecycleScope.launch {

            try {

                repository.salvarUsuario(usuario)

                Toast.makeText(
                    requireContext(),
                    "Cadastro realizado com sucesso!",
                    Toast.LENGTH_SHORT
                ).show()

                CadastroSession.limpar()

                startActivity(
                    Intent(
                        requireContext(),
                        EscolhaTipoPerfilActivity::class.java
                    )
                )

                requireActivity().finish()

            } catch (e: Exception) {

                btnConfirmar.isEnabled = true

                txtErro.text =
                    "*Erro ao salvar os dados: ${e.message}"

                txtErro.visibility =
                    View.VISIBLE
            }
        }
    }
}