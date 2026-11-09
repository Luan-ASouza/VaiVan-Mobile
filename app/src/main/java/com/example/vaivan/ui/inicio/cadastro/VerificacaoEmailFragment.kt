package com.example.vaivan.ui.inicio.cadastro

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.vaivan.R
import com.example.vaivan.data.repository.CadastroRascunhoRepository
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Etapa 2.5 — verificação real do e-mail: o Firebase manda um link (não um
 * código numérico), então aqui só se confere se o link já foi clicado
 * (`user.reload()` + `isEmailVerified`). */
class VerificacaoEmailFragment : Fragment(R.layout.fragment_verificacao_email) {

    private val viewModel: CadastroViewModel by activityViewModels()
    private val rascunhoRepository by lazy { CadastroRascunhoRepository(requireContext()) }

    private lateinit var txtDescricaoVerificacao: TextView
    private lateinit var txtErroVerificacao: TextView
    private lateinit var btnJaVerifiquei: MaterialButton
    private lateinit var txtReenviar: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        configurarEtapaCadastro(atual = 3)

        txtDescricaoVerificacao = view.findViewById(R.id.txtDescricaoVerificacao)
        txtErroVerificacao = view.findViewById(R.id.txtErroVerificacao)
        btnJaVerifiquei = view.findViewById(R.id.btnJaVerifiquei)
        txtReenviar = view.findViewById(R.id.txtReenviar)

        val email = FirebaseAuth.getInstance().currentUser?.email.orEmpty()
        txtDescricaoVerificacao.text =
            "Mandamos um link de confirmação pro seu e-mail ($email). Abra seu e-mail, toque no link e volte aqui."

        btnJaVerifiquei.setOnClickListener { conferirVerificacao() }
        txtReenviar.setOnClickListener { reenviarEmail() }
    }

    private fun conferirVerificacao() {
        val usuario = FirebaseAuth.getInstance().currentUser ?: return

        btnJaVerifiquei.isEnabled = false
        txtErroVerificacao.visibility = View.GONE

        lifecycleScope.launch {
            try {
                usuario.reload().await()

                if (usuario.isEmailVerified) {
                    viewModel.definirVerificacaoEmail()
                    rascunhoRepository.salvar(viewModel.cadastro, CadastroActivity.ETAPA_INFORMACOES_PESSOAIS)
                    (requireActivity() as CadastroActivity).abrirInformacoesPessoais()
                } else {
                    txtErroVerificacao.visibility = View.VISIBLE
                }
            } catch (erro: Exception) {
                txtErroVerificacao.visibility = View.VISIBLE
            } finally {
                btnJaVerifiquei.isEnabled = true
            }
        }
    }

    private fun reenviarEmail() {
        val usuario = FirebaseAuth.getInstance().currentUser ?: return

        txtReenviar.isEnabled = false

        usuario.sendEmailVerification()
            .addOnCompleteListener {
                Toast.makeText(requireContext(), "E-mail reenviado.", Toast.LENGTH_SHORT).show()
                txtReenviar.isEnabled = true
            }
    }
}
