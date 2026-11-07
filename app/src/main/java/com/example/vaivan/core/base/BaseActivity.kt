package com.example.vaivan.core.base

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.vaivan.core.util.SystemBarUtils

/**
 * Base pra qualquer Activity de tela cheia (edge-to-edge) que usa os
 * marcadores `topGap`/`bottomGap` do layout (ver [SystemBarUtils]) pra
 * reservar o espaço da status bar/nav bar, em vez de cada Activity repetir
 * `enableEdgeToEdge()` + a chamada de gap na mão.
 */
abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
    }

    /** Chamar depois do `setContentView`. Por padrão aplica os dois gaps;
     * passe `false` num deles pra pular (ex.: uma tela que só precisa do
     * gap de topo). */
    protected fun aplicarGapsDeSistema(aplicarTopo: Boolean = true, aplicarBase: Boolean = true) {
        val root = findViewById<View>(android.R.id.content)
        when {
            aplicarTopo && aplicarBase -> SystemBarUtils.applyTopAndBottomGaps(root)
            aplicarTopo -> SystemBarUtils.applyTopGap(root)
            aplicarBase -> SystemBarUtils.applyBottomGap(root)
        }
    }
}
