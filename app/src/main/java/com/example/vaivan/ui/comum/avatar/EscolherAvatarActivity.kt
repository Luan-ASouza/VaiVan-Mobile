package com.example.vaivan.ui.comum.avatar

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.vaivan.R
import com.example.vaivan.core.base.BaseActivity
import com.example.vaivan.core.util.AvatarCatalogo
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/**
 * Escolha de um avatar pronto + cor (AvatarCatalogo). Devolve a Uri de um
 * JPEG já renderizado — quem chama trata igual a uma foto da galeria.
 * Use pelo contrato [EscolherAvatar].
 */
class EscolherAvatarActivity : BaseActivity() {

    companion object {
        const val EXTRA_URI = "avatar_uri"
        private const val ESTADO_AVATAR = "avatar"
        private const val ESTADO_COR = "cor"
    }

    private var avatarSelecionado = 0
    private var corSelecionada = 0

    private lateinit var imgPrevia: ImageView
    private lateinit var containerCores: LinearLayout
    private lateinit var btnUsar: MaterialButton
    private lateinit var progress: ProgressBar
    private val adapter = AvataresAdapter()

    private val cor get() = AvatarCatalogo.CORES[corSelecionada]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_escolher_avatar)
        aplicarGapsDeSistema()

        avatarSelecionado = savedInstanceState?.getInt(ESTADO_AVATAR) ?: 0
        corSelecionada = savedInstanceState?.getInt(ESTADO_COR) ?: 0

        imgPrevia = findViewById(R.id.imgPreviaAvatar)
        containerCores = findViewById(R.id.containerCoresAvatar)
        btnUsar = findViewById(R.id.btnSalvarEdicaoPerfil)
        progress = findViewById(R.id.progressEdicaoPerfil)

        findViewById<ImageButton>(R.id.btnVoltarEdicaoPerfil).setOnClickListener { finish() }

        findViewById<RecyclerView>(R.id.recyclerAvatares).apply {
            layoutManager = GridLayoutManager(this@EscolherAvatarActivity, 4)
            adapter = this@EscolherAvatarActivity.adapter
        }

        btnUsar.text = "Usar este avatar"
        btnUsar.setOnClickListener { confirmar() }

        montarBolinhasDeCor()
        atualizarPrevia()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(ESTADO_AVATAR, avatarSelecionado)
        outState.putInt(ESTADO_COR, corSelecionada)
    }

    // =========================================================
    // CORES
    // =========================================================

    private fun montarBolinhasDeCor() {
        val dp = resources.displayMetrics.density
        val tamanho = (40 * dp).toInt()
        val margem = (5 * dp).toInt()
        val respiro = (3 * dp).toInt()

        AvatarCatalogo.CORES.forEachIndexed { indice, cor ->
            // Moldura (contorno quando selecionada) + bolinha da cor por dentro.
            val moldura = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(tamanho, tamanho).apply {
                    marginStart = margem
                    marginEnd = margem
                }
                setPadding(respiro, respiro, respiro, respiro)
                contentDescription = cor.nome
                isClickable = true
                isFocusable = true
                setOnClickListener { selecionarCor(indice) }
            }

            moldura.addView(
                View(this).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(ContextCompat.getColor(this@EscolherAvatarActivity, cor.cor))
                    }
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )

            containerCores.addView(moldura)
        }

        marcarCorSelecionada()
    }

    private fun selecionarCor(indice: Int) {
        if (indice == corSelecionada) return
        corSelecionada = indice
        marcarCorSelecionada()
        atualizarPrevia()
        // Todos os avatares da grade mudam de cor.
        adapter.notifyItemRangeChanged(0, adapter.itemCount)
    }

    private fun marcarCorSelecionada() {
        for (i in 0 until containerCores.childCount) {
            containerCores.getChildAt(i).background =
                if (i == corSelecionada) {
                    ContextCompat.getDrawable(this, R.drawable.bg_avatar_selecionado)
                } else {
                    null
                }
        }
    }

    // =========================================================
    // AVATAR
    // =========================================================

    private fun selecionarAvatar(indice: Int) {
        if (indice == avatarSelecionado) return
        val anterior = avatarSelecionado
        avatarSelecionado = indice
        adapter.notifyItemChanged(anterior)
        adapter.notifyItemChanged(indice)
        atualizarPrevia()
    }

    private fun atualizarPrevia() {
        imgPrevia.setImageDrawable(
            AvatarCatalogo.desenhar(this, AvatarCatalogo.AVATARES[avatarSelecionado], cor)
        )
    }

    // =========================================================
    // CONFIRMAR
    // =========================================================

    private fun confirmar() {
        btnUsar.isEnabled = false
        btnUsar.text = ""
        progress.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val uri = AvatarCatalogo.renderizar(
                    this@EscolherAvatarActivity,
                    AvatarCatalogo.AVATARES[avatarSelecionado],
                    cor
                )
                setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_URI, uri))
                finish()
            } catch (e: Exception) {
                Toast.makeText(
                    this@EscolherAvatarActivity,
                    "Não foi possível gerar o avatar: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                btnUsar.isEnabled = true
                btnUsar.text = "Usar este avatar"
                progress.visibility = View.GONE
            }
        }
    }

    // =========================================================
    // GRADE
    // =========================================================

    private inner class AvataresAdapter : RecyclerView.Adapter<AvataresAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val moldura: View = view.findViewById(R.id.molduraAvatar)
            val img: ImageView = view.findViewById(R.id.imgAvatarItem)
        }

        override fun getItemCount() = AvatarCatalogo.AVATARES.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_avatar, parent, false)
        )

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.img.setImageDrawable(
                AvatarCatalogo.desenhar(holder.img.context, AvatarCatalogo.AVATARES[position], cor)
            )
            holder.img.contentDescription = "Avatar ${position + 1}"
            holder.moldura.background =
                if (position == avatarSelecionado) {
                    ContextCompat.getDrawable(holder.moldura.context, R.drawable.bg_avatar_selecionado)
                } else {
                    null
                }
            holder.img.setOnClickListener { selecionarAvatar(holder.bindingAdapterPosition) }
        }
    }
}

/** Abre a [EscolherAvatarActivity]; devolve a Uri do JPEG do avatar, ou null se cancelou. */
class EscolherAvatar : ActivityResultContract<Unit, Uri?>() {

    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(context, EscolherAvatarActivity::class.java)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK && intent != null) {
            IntentCompat.getParcelableExtra(intent, EscolherAvatarActivity.EXTRA_URI, Uri::class.java)
        } else {
            null
        }
}
