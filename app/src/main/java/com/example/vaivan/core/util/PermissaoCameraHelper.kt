package com.example.vaivan.core.util

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Pede a permissão de câmera antes de abrir a câmera do sistema.
 *
 * Por que é necessário: o app declara `CAMERA` no manifest (a selfie usa
 * CameraX). Com a permissão declarada, o Android recusa o
 * `ActivityResultContracts.TakePicture` (SecurityException) enquanto ela
 * não for concedida — era por isso que "Tirar foto" não abria nada nos
 * envios de CNH, comprovante e documentos do veículo.
 *
 * Criar como propriedade da Activity (antes de STARTED), como qualquer
 * registerForActivityResult. [aoNegar] é chamado se o usuário recusar.
 */
class PermissaoCameraHelper(
    private val activity: ComponentActivity,
    private val aoNegar: () -> Unit
) {
    private var acaoPendente: (() -> Unit)? = null

    private val pedido = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedida ->
        val acao = acaoPendente
        acaoPendente = null
        if (concedida) acao?.invoke() else aoNegar()
    }

    /** Executa [acao] já com a permissão (pedindo antes, se faltar). */
    fun comPermissao(acao: () -> Unit) {
        val concedida = ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (concedida) {
            acao()
        } else {
            acaoPendente = acao
            pedido.launch(Manifest.permission.CAMERA)
        }
    }
}
