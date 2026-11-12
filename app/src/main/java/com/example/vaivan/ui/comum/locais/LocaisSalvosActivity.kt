package com.example.vaivan.ui.comum.locais

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.vaivan.R
import com.example.vaivan.core.base.BaseActivity
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.entities.LocalEntity
import com.example.vaivan.data.repository.LocalRepository
import com.example.vaivan.ui.responsavel.passageiros.AdicionarLocalActivity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/** Lista dos locais salvos do usuário (motorista ou responsável), com busca.
 * Tocar num local abre a edição; "Adicionar local" abre a criação. */
class LocaisSalvosActivity : BaseActivity() {

    private val uid: String? = FirebaseAuth.getInstance().currentUser?.uid

    private val viewModel: LocaisSalvosViewModel by viewModels {
        viewModelFactory {
            initializer {
                val db = VaivanDatabase.getInstance(this@LocaisSalvosActivity)
                LocaisSalvosViewModel(LocalRepository(db.localDao()), uid.orEmpty())
            }
        }
    }

    private val adapter = LocaisSalvosAdapter { local -> abrirEdicao(local) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (uid == null) {
            Toast.makeText(this, "Sessão expirada. Faça login novamente.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContentView(R.layout.activity_locais_salvos)
        aplicarGapsDeSistema()

        findViewById<View>(R.id.btnVoltarLocais).setOnClickListener { finish() }

        findViewById<RecyclerView>(R.id.recyclerLocaisSalvos).apply {
            layoutManager = LinearLayoutManager(this@LocaisSalvosActivity)
            adapter = this@LocaisSalvosActivity.adapter
        }

        findViewById<EditText>(R.id.edtBuscarLocal).doAfterTextChanged {
            viewModel.buscar(it?.toString().orEmpty())
        }

        findViewById<View>(R.id.btnAdicionarLocalSalvo).setOnClickListener {
            startActivity(Intent(this, AdicionarLocalActivity::class.java))
        }

        val txtVazio = findViewById<TextView>(R.id.txtLocaisVazio)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.locais.collect { locais ->
                    if (locais == null) return@collect

                    adapter.submitList(locais)

                    txtVazio.text = if (viewModel.temLocais) {
                        "Nenhum local encontrado para essa busca."
                    } else {
                        "Você ainda não salvou nenhum local.\n" +
                            "Salve sua casa ou outros endereços para usar como ponto de embarque."
                    }
                    txtVazio.visibility = if (locais.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun abrirEdicao(local: LocalEntity) {
        startActivity(AdicionarLocalActivity.intentEdicao(this, local))
    }
}
