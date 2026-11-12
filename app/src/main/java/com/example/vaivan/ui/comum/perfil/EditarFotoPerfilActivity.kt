package com.example.vaivan.ui.comum.perfil

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bumptech.glide.Glide
import com.example.vaivan.R
import com.example.vaivan.core.base.BaseActivity
import com.example.vaivan.core.util.ConfirmacaoExclusaoHelper
import com.example.vaivan.core.util.SeletorDeFotoHelper
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.repository.UsuarioRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth

/** Troca/remoção da foto de perfil (aberta tocando no avatar do perfil).
 * Diferente das outras telas de edição, continua aberta depois de salvar. */
class EditarFotoPerfilActivity : BaseActivity() {

    private val viewModel: EditarPerfilViewModel by viewModels {
        viewModelFactory {
            initializer {
                val db = VaivanDatabase.getInstance(this@EditarFotoPerfilActivity)
                EditarPerfilViewModel(UsuarioRepository(db.usuarioDao()), FirebaseAuth.getInstance().currentUser?.uid)
            }
        }
    }

    // Precisa ser criado em onCreate (antes de STARTED) — ver SeletorDeFotoHelper.
    private lateinit var seletorDeFoto: SeletorDeFotoHelper

    private lateinit var imgFoto: ShapeableImageView
    private lateinit var btnTrocar: MaterialButton
    private lateinit var btnRemover: TextView
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editar_foto_perfil)
        aplicarGapsDeSistema()

        seletorDeFoto = SeletorDeFotoHelper(this) { uri ->
            Glide.with(imgFoto).load(uri).circleCrop().into(imgFoto)
            viewModel.atualizarFoto(uri)
        }

        imgFoto = findViewById(R.id.imgFotoPerfilEdicao)
        btnTrocar = findViewById(R.id.btnTrocarFotoPerfil)
        btnRemover = findViewById(R.id.btnRemoverFotoPerfil)
        progress = findViewById(R.id.progressFotoPerfil)

        btnTrocar.setOnClickListener { seletorDeFoto.abrirSeletor() }
        btnRemover.setOnClickListener { confirmarRemocao() }

        acompanharEdicao(
            viewModel = viewModel,
            aoMudarOcupado = { ocupado ->
                progress.visibility = if (ocupado) View.VISIBLE else View.GONE
                btnTrocar.isEnabled = !ocupado
                btnRemover.isEnabled = !ocupado
            },
            aoCarregar = { exibirFotoAtual() },
            aoFalharSalvar = { mensagem ->
                Toast.makeText(this, mensagem, Toast.LENGTH_LONG).show()
                exibirFotoAtual() // volta a foto anterior
            },
            aoSalvarSemFechar = { exibirFotoAtual() }
        )
    }

    private fun exibirFotoAtual() {
        val fotoUrl = viewModel.item.value?.fotoUrl
        btnRemover.visibility = if (fotoUrl == null) View.GONE else View.VISIBLE

        Glide.with(imgFoto)
            .load(fotoUrl)
            .placeholder(R.drawable.ic_perfil)
            .error(R.drawable.ic_perfil)
            .circleCrop()
            .into(imgFoto)
    }

    private fun confirmarRemocao() {
        ConfirmacaoExclusaoHelper.confirmar(
            contexto = this,
            titulo = "Remover foto?",
            mensagem = "Sua foto de perfil atual será removida.",
            textoConfirmar = "Sim, remover"
        ) {
            viewModel.removerFoto()
        }
    }
}
