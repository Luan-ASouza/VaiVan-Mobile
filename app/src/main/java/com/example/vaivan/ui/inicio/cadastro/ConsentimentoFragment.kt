package com.example.vaivan.ui.inicio.cadastro

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.vaivan.R
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.entities.UsuarioEntity
import com.example.vaivan.data.repository.CadastroRascunhoRepository
import com.example.vaivan.data.repository.UsuarioRepository
import com.example.vaivan.ui.inicio.EscolhaTipoPerfilActivity
import com.example.vaivan.ui.inicio.TermosUsoActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/** Etapa 5 — consentimentos e finalização. A conta no Firebase Auth já
 * existe nesse ponto (criada na etapa 2) — aqui só se grava o perfil
 * completo no Firestore/Room. */
class ConsentimentoFragment :
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

    private lateinit var checkTermos: CheckBox
    private lateinit var txtTermos: TextView
    private lateinit var txtErroTermos: TextView
    private lateinit var checkComunicacoes: CheckBox

    private lateinit var repository: UsuarioRepository

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        configurarEtapaCadastro(atual = 7)
        configurarViews(view)
        configurarRepositories()
        preencherDados()
        configurarBotao()

        txtTermos.setOnClickListener {
            startActivity(Intent(requireContext(), TermosUsoActivity::class.java))
        }
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

        checkTermos =
            view.findViewById(R.id.checkTermos)

        txtTermos =
            view.findViewById(R.id.txtTermos)

        txtErroTermos =
            view.findViewById(R.id.txtErroTermos)

        checkComunicacoes =
            view.findViewById(R.id.checkComunicacoes)
    }

    private fun configurarRepositories() {

        repository =
            UsuarioRepository(
                VaivanDatabase
                    .getInstance(requireContext())
                    .usuarioDao()
            )
    }

    private fun preencherDados() {

        val usuario =
            viewModel.cadastro.usuario

        val nomeCompleto =
            listOf(usuario.nome, usuario.sobrenome)
                .filter { it.isNotBlank() }
                .joinToString(" ")

        txtNome.text =
            nomeCompleto.ifBlank {
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

        if (!checkTermos.isChecked) {
            txtErroTermos.visibility = View.VISIBLE
            return
        }

        txtErroTermos.visibility = View.GONE

        val uid =
            FirebaseAuth.getInstance().currentUser?.uid

        if (uid == null) {
            mostrarErro("*Sessão expirada. Volte e entre novamente.")
            return
        }

        btnConfirmar.isEnabled =
            false

        txtErro.visibility =
            View.GONE

        viewModel.definirConsentimento(
            aceitouTermos = true,
            aceitaComunicacoes = checkComunicacoes.isChecked
        )

        salvarUsuario(uid)
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

                sobrenome =
                    cadastro.usuario.sobrenome,

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

                aceitouTermos =
                    cadastro.usuario.aceitouTermos,

                aceitaComunicacoes =
                    cadastro.usuario.aceitaComunicacoes,

                lastUpdated =
                    System.currentTimeMillis()
            )

        viewLifecycleOwner.lifecycleScope.launch {

            try {

                repository.salvarUsuario(
                    usuario
                )

                CadastroRascunhoRepository(requireContext()).limpar()

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
