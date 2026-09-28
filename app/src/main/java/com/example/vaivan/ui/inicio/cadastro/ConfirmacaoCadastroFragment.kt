package com.example.vaivan.ui.inicio.cadastro

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.vaivan.R
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.entities.UsuarioEntity
import com.example.vaivan.data.repository.FirebaseAuthRepository
import com.example.vaivan.data.repository.UsuarioRepository
import com.example.vaivan.ui.inicio.EscolhaTipoPerfilActivity
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import com.example.vaivan.data.local.dao.UsuarioDao

class ConfirmacaoCadastroFragment :
    Fragment(R.layout.fragment_confirmacao_cadastro) {

    private val viewModel: CadastroViewModel by activityViewModels()

    private lateinit var btnConfirmar: MaterialButton
    private lateinit var txtErro: TextView

    private lateinit var txtNome: TextView
    private lateinit var txtCpf: TextView
    private lateinit var txtDataNascimento: TextView
    private lateinit var txtEmail: TextView
    private lateinit var txtTelefone: TextView

    private lateinit var txtCep: TextView
    private lateinit var txtCidadeEstado: TextView
    private lateinit var txtRuaNumero: TextView
    private lateinit var txtBairro: TextView
    private lateinit var txtComplemento: TextView
    private lateinit var txtLabelComplemento: TextView

    private lateinit var repository: UsuarioRepository
    private lateinit var authRepository: FirebaseAuthRepository

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        configurarViews(view)
        configurarRepositories()
        preencherDados()
        configurarBotao()
    }

    private fun configurarViews(
        view: View
    ) {

        txtNome =
            view.findViewById(R.id.txtNome)

        txtCpf =
            view.findViewById(R.id.txtCpf)

        txtDataNascimento =
            view.findViewById(R.id.txtDataNascimento)

        txtEmail =
            view.findViewById(R.id.txtEmail)

        txtTelefone =
            view.findViewById(R.id.txtTelefone)

        txtCep =
            view.findViewById(R.id.txtCep)

        txtCidadeEstado =
            view.findViewById(R.id.txtCidadeEstado)

        txtRuaNumero =
            view.findViewById(R.id.txtRuaNumero)

        txtBairro =
            view.findViewById(R.id.txtBairro)

        txtComplemento =
            view.findViewById(R.id.txtComplemento)

        txtLabelComplemento =
            view.findViewById(R.id.txtLabelComplemento)

        txtErro =
            view.findViewById(R.id.txtErro)

        btnConfirmar =
            view.findViewById(R.id.btnConfirmar)
    }

    private fun configurarRepositories() {

        repository =
            UsuarioRepository(
                VaivanDatabase
                    .getInstance(requireContext())
                    .usuarioDao()
            )

        authRepository =
            FirebaseAuthRepository()
    }

    private fun preencherDados() {

        val usuario =
            viewModel.cadastro.usuario

        txtNome.text =
            usuario.nome.ifBlank {
                "Não informado"
            }

        txtCpf.text =
            usuario.cpf.ifBlank {
                "Não informado"
            }

        txtDataNascimento.text =
            usuario.dataNascimento?.ifBlank {
                "Não informado"
            }

        txtEmail.text =
            usuario.email.ifBlank {
                "Não informado"
            }

        txtTelefone.text =
            usuario.telefone.ifBlank {
                "Não informado"
            }

        txtCep.text =
            usuario.cep.ifBlank {
                "Não informado"
            }

        txtCidadeEstado.text =
            formatarCidadeEstado(
                usuario.cidade,
                usuario.estado
            )

        txtRuaNumero.text =
            formatarRuaNumero(
                usuario.rua,
                usuario.numero
            )

        txtBairro.text =
            usuario.bairro.ifBlank {
                "Não informado"
            }

        if (
            usuario.complemento?.isBlank() == true
        ) {

            txtLabelComplemento.visibility =
                View.GONE

            txtComplemento.visibility =
                View.GONE

        } else {

            txtLabelComplemento.visibility =
                View.VISIBLE

            txtComplemento.visibility =
                View.VISIBLE

            txtComplemento.text =
                usuario.complemento
        }
    }

    private fun formatarCidadeEstado(
        cidade: String,
        estado: String
    ): String {

        if (
            cidade.isBlank() &&
            estado.isBlank()
        ) {
            return "Não informado"
        }

        if (cidade.isBlank()) {
            return estado
        }

        if (estado.isBlank()) {
            return cidade
        }

        return "$cidade - $estado"
    }

    private fun formatarRuaNumero(
        rua: String,
        numero: String
    ): String {

        if (
            rua.isBlank() &&
            numero.isBlank()
        ) {
            return "Não informado"
        }

        if (rua.isBlank()) {
            return numero
        }

        if (numero.isBlank()) {
            return rua
        }

        return "$rua, $numero"
    }

    private fun configurarBotao() {

        btnConfirmar.setOnClickListener {
            confirmarCadastro()
        }
    }

    private fun confirmarCadastro() {

        val cadastro =
            viewModel.cadastro

        btnConfirmar.isEnabled =
            false

        txtErro.visibility =
            View.GONE

        authRepository.cadastrar(
            email = cadastro.usuario.email,
            senha = cadastro.senha,

            onSuccess = { uid ->
                salvarUsuario(uid)
            },

            onError = { erro ->

                btnConfirmar.isEnabled =
                    true

                mostrarErro(
                    erro.message
                        ?: "*Erro ao criar a conta."
                )
            }
        )
    }

    private fun salvarUsuario(
        uid: String
    ) {

        val cadastro =
            viewModel.cadastro

        val usuario =
            UsuarioEntity(
                id = uid,

                nome =
                    cadastro.usuario.nome,

                cpf =
                    cadastro.usuario.cpf,

                email =
                    cadastro.usuario.email,

                telefone =
                    cadastro.usuario.telefone,

                dataNascimento =
                    cadastro.usuario.dataNascimento,

                cep =
                    cadastro.usuario.cep,

                estado =
                    cadastro.usuario.estado,

                cidade =
                    cadastro.usuario.cidade,

                bairro =
                    cadastro.usuario.bairro,

                rua =
                    cadastro.usuario.rua,

                numero =
                    cadastro.usuario.numero,

                complemento =
                    cadastro.usuario.complemento,

                status = "ATIVO",

                emailConfirmado =
                    cadastro.usuario.emailConfirmado,

                lastUpdated =
                    System.currentTimeMillis()
            )

        viewLifecycleOwner.lifecycleScope.launch {

            try {

                repository.salvarUsuario(
                    usuario
                )

                Toast.makeText(
                    requireContext(),
                    "Cadastro realizado com sucesso!",
                    Toast.LENGTH_SHORT
                ).show()

                abrirEscolhaTipoPerfil()

            } catch (e: Exception) {

                btnConfirmar.isEnabled =
                    true

                mostrarErro(
                    "*Erro ao salvar os dados: ${e.message}"
                )
            }
        }
    }

    private fun mostrarErro(
        mensagem: String
    ) {

        txtErro.text =
            mensagem

        txtErro.visibility =
            View.VISIBLE
    }

    private fun abrirEscolhaTipoPerfil() {

        startActivity(
            Intent(
                requireContext(),
                EscolhaTipoPerfilActivity::class.java
            )
        )

        requireActivity().finish()
    }
}