package com.example.vaivan.ui.motorista.assinatura

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.vaivan.R
import com.example.vaivan.data.repository.AssinaturaRepository
import com.example.vaivan.domain.usecase.assinatura.AssinaturaPolicy
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/**
 * Área de gestão de pagamentos do motorista (recebimentos e repasses, RF15).
 * Só abre com a assinatura em dia; sem ela, mostra o bloqueio.
 * O recebimento das mensalidades pelo aplicativo ainda não foi implementado.
 */
class PagamentosMotoristaActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pagamentos_motorista)

        AssinaturaUi.aplicarInsets(findViewById(R.id.rootPagamentos))

        findViewById<ImageButton>(R.id.btnVoltarPagamentos).setOnClickListener { finish() }

        findViewById<Button>(R.id.btnIrParaAssinatura).setOnClickListener {
            startActivity(Intent(this, AssinaturaActivity::class.java))
            finish()
        }

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            finish()
            return
        }

        val conteudo = findViewById<View>(R.id.conteudoPagamentos)
        val bloqueio = findViewById<View>(R.id.blocoBloqueado)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AssinaturaRepository().observarAssinatura(uid).collect { assinatura ->
                    val liberado = AssinaturaPolicy.podeVerPagamentos(
                        assinatura, System.currentTimeMillis()
                    )
                    conteudo.visibility = if (liberado) View.VISIBLE else View.GONE
                    bloqueio.visibility = if (liberado) View.GONE else View.VISIBLE
                }
            }
        }
    }
}