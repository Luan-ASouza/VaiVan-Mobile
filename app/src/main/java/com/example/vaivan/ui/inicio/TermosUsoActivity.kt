package com.example.vaivan.ui.inicio

import android.os.Bundle
import android.widget.ImageButton
import com.example.vaivan.R
import com.example.vaivan.core.base.BaseActivity

/** Tela de Termos de Uso/Privacidade — texto genérico, deixando claro o
 * caráter acadêmico do projeto (ver CLAUDE.md). Aberta a partir do checkbox
 * de aceite na etapa final do cadastro. */
class TermosUsoActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_termos_uso)
        aplicarGapsDeSistema()

        findViewById<ImageButton>(R.id.btnVoltar).setOnClickListener {
            finish()
        }
    }
}
