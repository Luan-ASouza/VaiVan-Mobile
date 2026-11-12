package com.example.vaivan.ui.comum.perfil

import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.vaivan.R
import com.example.vaivan.core.util.TextoUtil
import com.example.vaivan.data.local.entities.UsuarioEntity
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth

/**
 * Cabeçalho + lista de campos do fragment_perfil, compartilhados pelo
 * PerfilFragment do motorista e do responsável (o layout é o mesmo). Cada
 * linha editável abre a sua tela de edição em ui/comum/perfil.
 *
 * CPF e e-mail são só exibição: CPF é a identidade da conta (checado contra
 * duplicidade no cadastro) e o e-mail é o login do Firebase Auth.
 */
class PerfilCampos(private val fragment: Fragment, raiz: View) {

    private val imgAvatar: ShapeableImageView = raiz.findViewById(R.id.imgUsuarioAvatar)
    private val txtNomeCompleto: TextView = raiz.findViewById(R.id.txtNomeUsuarioCompleto)

    private val nome = LinhaCampoPerfil(raiz, R.id.campoNome, "Nome")
    private val sobrenome = LinhaCampoPerfil(raiz, R.id.campoSobrenome, "Sobrenome")
    private val nascimento = LinhaCampoPerfil(raiz, R.id.campoNascimento, "Data de nascimento")
    private val cpf = LinhaCampoPerfil(raiz, R.id.campoCpf, "CPF", editavel = false)
    private val telefone = LinhaCampoPerfil(raiz, R.id.campoTelefone, "Telefone")
    private val email = LinhaCampoPerfil(raiz, R.id.campoEmail, "E-mail", editavel = false)
    private val endereco = LinhaCampoPerfil(raiz, R.id.campoEndereco, "Endereço")
    private val senha = LinhaCampoPerfil(raiz, R.id.campoSenha, "Alterar senha")

    init {
        val contexto = fragment.requireContext()

        raiz.findViewById<View>(R.id.btnFotoPerfil).setOnClickListener {
            abrir(Intent(contexto, EditarFotoPerfilActivity::class.java))
        }

        nome.aoTocar { abrir(EditarCampoPerfilActivity.intent(contexto, EditarCampoPerfilActivity.Campo.NOME)) }
        sobrenome.aoTocar { abrir(EditarCampoPerfilActivity.intent(contexto, EditarCampoPerfilActivity.Campo.SOBRENOME)) }
        telefone.aoTocar { abrir(EditarCampoPerfilActivity.intent(contexto, EditarCampoPerfilActivity.Campo.TELEFONE)) }
        nascimento.aoTocar { abrir(Intent(contexto, EditarDataNascimentoActivity::class.java)) }
        endereco.aoTocar { abrir(Intent(contexto, EditarEnderecoActivity::class.java)) }
        senha.aoTocar { abrir(Intent(contexto, AlterarSenhaActivity::class.java)) }
    }

    fun preencher(usuario: UsuarioEntity) {
        txtNomeCompleto.text = listOf(usuario.nome, usuario.sobrenome)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        nome.valor(usuario.nome)
        sobrenome.valor(usuario.sobrenome)
        nascimento.valor(formatarNascimento(usuario.dataNascimento))
        cpf.valor(TextoUtil.mascararCpf(usuario.cpf))
        telefone.valor(TextoUtil.mascararTelefone(usuario.telefone))
        endereco.valor(resumirEndereco(usuario))

        // E-mail/verificação vêm do Firebase Auth (fonte do login); o documento
        // do Firestore é só a cópia gravada no cadastro.
        val contaAuth = FirebaseAuth.getInstance().currentUser
        email.valor(TextoUtil.mascararEmail(contaAuth?.email ?: usuario.email))
        val verificado = contaAuth?.isEmailVerified ?: usuario.emailConfirmado
        email.status(
            if (verificado) "Verificado" else "Não verificado",
            if (verificado) R.color.status_success else R.color.status_danger
        )

        Glide.with(imgAvatar)
            .load(usuario.fotoUrl)
            .placeholder(R.drawable.ic_perfil)
            .error(R.drawable.ic_perfil)
            .circleCrop()
            .into(imgAvatar)
    }

    private fun abrir(intent: Intent) = fragment.startActivity(intent)

    /** "yyyy/MM/dd" (formato gravado no cadastro) → "dd/MM/yyyy". */
    private fun formatarNascimento(data: String?): String {
        val partes = data?.split("/") ?: return ""
        if (partes.size != 3) return data
        return "${partes[2]}/${partes[1]}/${partes[0]}"
    }

    private fun resumirEndereco(usuario: UsuarioEntity): String =
        listOf(
            listOf(usuario.rua, usuario.numero).filter { it.isNotBlank() }.joinToString(", "),
            usuario.cidade
        ).filter { it.isNotBlank() }.joinToString(" - ")
}
