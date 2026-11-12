package com.example.vaivan.ui.responsavel.passageiros.perfil

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import com.example.vaivan.ui.comum.avatar.EscolherAvatar
import com.example.vaivan.ui.comum.perfil.acompanharEdicao
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView

/** Foto do passageiro: da galeria (recortada) ou um avatar pronto com cor.
 * Salva na hora e continua aberta, como a foto do perfil do usuário. */
class FotoPassageiroActivity : BaseActivity() {

    companion object {
        fun intent(context: Context, passageiroId: String): Intent =
            Intent(context, FotoPassageiroActivity::class.java)
                .putExtra(PerfilPassageiroActivity.EXTRA_PASSAGEIRO_ID, passageiroId)
    }

    private val viewModel: EditarPassageiroViewModel by viewModels {
        viewModelFactory {
            initializer {
                EditarPassageiroViewModel(
                    EditarPassageiroViewModel.criarRepository(this@FotoPassageiroActivity),
                    intent.getStringExtra(PerfilPassageiroActivity.EXTRA_PASSAGEIRO_ID).orEmpty()
                )
            }
        }
    }

    // Os dois precisam ser registrados em onCreate (antes de STARTED).
    private lateinit var seletorDeFoto: SeletorDeFotoHelper
    private val escolherAvatar = registerForActivityResult(EscolherAvatar()) { uri ->
        if (uri != null) enviar(uri)
    }

    private lateinit var imgFoto: ShapeableImageView
    private lateinit var btnGaleria: MaterialButton
    private lateinit var btnAvatar: MaterialButton
    private lateinit var btnRemover: TextView
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_foto_passageiro)
        aplicarGapsDeSistema()

        seletorDeFoto = SeletorDeFotoHelper(this) { uri -> enviar(uri) }

        imgFoto = findViewById(R.id.imgFotoPassageiroEdicao)
        btnGaleria = findViewById(R.id.btnGaleriaFotoPassageiro)
        btnAvatar = findViewById(R.id.btnAvatarFotoPassageiro)
        btnRemover = findViewById(R.id.btnRemoverFotoPassageiroEdicao)
        progress = findViewById(R.id.progressFotoPassageiro)

        btnGaleria.setOnClickListener { seletorDeFoto.abrirSeletor() }
        btnAvatar.setOnClickListener { escolherAvatar.launch(Unit) }
        btnRemover.setOnClickListener { confirmarRemocao() }

        acompanharEdicao(
            viewModel = viewModel,
            aoMudarOcupado = { ocupado ->
                progress.visibility = if (ocupado) View.VISIBLE else View.GONE
                btnGaleria.isEnabled = !ocupado
                btnAvatar.isEnabled = !ocupado
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

    private fun enviar(uri: Uri) {
        // Mostra já a nova imagem enquanto sobe.
        Glide.with(imgFoto).load(uri).circleCrop().into(imgFoto)
        viewModel.atualizarFoto(uri)
    }

    private fun exibirFotoAtual() {
        val fotoUrl = viewModel.item.value?.fotoUrl
        btnRemover.visibility = if (fotoUrl == null) View.GONE else View.VISIBLE

        Glide.with(imgFoto)
            .load(fotoUrl)
            .placeholder(R.drawable.avatar_01)
            .error(R.drawable.avatar_01)
            .circleCrop()
            .into(imgFoto)
    }

    private fun confirmarRemocao() {
        ConfirmacaoExclusaoHelper.confirmar(
            contexto = this,
            titulo = "Remover foto?",
            mensagem = "A foto deste passageiro será removida.",
            textoConfirmar = "Sim, remover"
        ) {
            viewModel.removerFoto()
        }
    }
}
