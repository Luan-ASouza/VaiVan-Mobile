package com.example.vaivan.ui.responsavel.passageiros.perfil

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
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
 *
 * Dois modos, mesma tela:
 *  - edição ([intent]): passageiro já existe; salva na hora e continua aberta,
 *    como a foto do perfil do usuário;
 *  - escolha ([EscolherFotoNovoPassageiro]): cadastro de um passageiro novo,
 *    que ainda não tem id — só devolve a foto escolhida para o formulário,
 *    que sobe depois de salvar. */
class FotoPassageiroActivity : BaseActivity() {

    companion object {
        /** Foto já escolhida no formulário (modo escolha). */
        internal const val EXTRA_FOTO_ATUAL = "foto_atual"

        fun intent(context: Context, passageiroId: String): Intent =
            Intent(context, FotoPassageiroActivity::class.java)
                .putExtra(PerfilPassageiroActivity.EXTRA_PASSAGEIRO_ID, passageiroId)
    }

    private val passageiroId: String by lazy {
        intent.getStringExtra(PerfilPassageiroActivity.EXTRA_PASSAGEIRO_ID).orEmpty()
    }

    /** Sem passageiro: cadastro de um novo (só escolhe e devolve). */
    private val modoEscolha: Boolean get() = passageiroId.isBlank()

    private val viewModel: EditarPassageiroViewModel by viewModels {
        viewModelFactory {
            initializer {
                EditarPassageiroViewModel(
                    EditarPassageiroViewModel.criarRepository(this@FotoPassageiroActivity),
                    passageiroId
                )
            }
        }
    }

    // Os dois precisam ser registrados em onCreate (antes de STARTED).
    private lateinit var seletorDeFoto: SeletorDeFotoHelper
    private val escolherAvatar = registerForActivityResult(EscolherAvatar()) { uri ->
        if (uri != null) aoEscolher(uri)
    }

    /** Foto escolhida no modo escolha (ainda não enviada). */
    private var fotoEscolhida: Uri? = null

    private lateinit var imgFoto: ShapeableImageView
    private lateinit var btnGaleria: MaterialButton
    private lateinit var btnAvatar: MaterialButton
    private lateinit var btnRemover: TextView
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_foto_passageiro)
        aplicarGapsDeSistema()

        seletorDeFoto = SeletorDeFotoHelper(this) { uri -> aoEscolher(uri) }

        imgFoto = findViewById(R.id.imgFotoPassageiroEdicao)
        btnGaleria = findViewById(R.id.btnGaleriaFotoPassageiro)
        btnAvatar = findViewById(R.id.btnAvatarFotoPassageiro)
        btnRemover = findViewById(R.id.btnRemoverFotoPassageiroEdicao)
        progress = findViewById(R.id.progressFotoPassageiro)

        findViewById<View>(R.id.btnVoltarEdicaoPerfil)?.setOnClickListener { finish() }
        btnGaleria.setOnClickListener { seletorDeFoto.abrirSeletor() }
        btnAvatar.setOnClickListener { escolherAvatar.launch(Unit) }
        btnRemover.setOnClickListener { confirmarRemocao() }

        if (modoEscolha) {
            fotoEscolhida = IntentCompat.getParcelableExtra(intent, EXTRA_FOTO_ATUAL, Uri::class.java)
            exibir(fotoEscolhida)
            return
        }

        acompanharEdicao(
            viewModel = viewModel,
            aoMudarOcupado = { ocupado ->
                progress.visibility = if (ocupado) View.VISIBLE else View.GONE
                btnGaleria.isEnabled = !ocupado
                btnAvatar.isEnabled = !ocupado
                btnRemover.isEnabled = !ocupado
            },
            aoCarregar = { exibir(viewModel.item.value?.fotoUrl) },
            aoFalharSalvar = { mensagem ->
                Toast.makeText(this, mensagem, Toast.LENGTH_LONG).show()
                exibir(viewModel.item.value?.fotoUrl) // volta a foto anterior
            },
            aoSalvarSemFechar = { exibir(viewModel.item.value?.fotoUrl) }
        )
    }

    private fun aoEscolher(uri: Uri) {
        if (modoEscolha) {
            definirEscolha(uri)
            return
        }
        // Mostra já a nova imagem enquanto sobe.
        Glide.with(imgFoto).load(uri).circleCrop().into(imgFoto)
        viewModel.atualizarFoto(uri)
    }

    /** Modo escolha: guarda e já deixa o resultado pronto para quando voltar. */
    private fun definirEscolha(uri: Uri?) {
        fotoEscolhida = uri
        setResult(Activity.RESULT_OK, Intent().setData(uri))
        exibir(uri)
    }

    /** [foto]: Uri local ou URL (String) do Storage; null = avatar padrão. */
    private fun exibir(foto: Any?) {
        btnRemover.visibility = if (foto == null) View.GONE else View.VISIBLE

        Glide.with(imgFoto)
            .load(foto)
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
            if (modoEscolha) definirEscolha(null) else viewModel.removerFoto()
        }
    }
}

/** Resultado do modo escolha: [uri] = foto escolhida (null = removeu a foto). */
data class EscolhaDeFoto(val uri: Uri?)

/** Abre a tela de foto no modo escolha (cadastro de passageiro novo). A entrada
 * é a foto já escolhida no formulário; a saída é null quando nada mudou. */
class EscolherFotoNovoPassageiro : ActivityResultContract<Uri?, EscolhaDeFoto?>() {

    override fun createIntent(context: Context, input: Uri?): Intent =
        Intent(context, FotoPassageiroActivity::class.java)
            .putExtra(FotoPassageiroActivity.EXTRA_FOTO_ATUAL, input)

    override fun parseResult(resultCode: Int, intent: Intent?): EscolhaDeFoto? =
        if (resultCode == Activity.RESULT_OK) EscolhaDeFoto(intent?.data) else null
}
