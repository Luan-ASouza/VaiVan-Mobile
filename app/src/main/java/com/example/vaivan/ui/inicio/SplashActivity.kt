package com.example.vaivan.ui.inicio

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.repository.UsuarioRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val usuario = FirebaseAuth.getInstance().currentUser

        if (usuario == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Sessão retomada: só pula direto pra escolha de perfil se o perfil
        // JÁ estiver completo. Um cadastro abandonado no meio (conta já
        // existe no Auth, mas usuarios/{uid} nunca foi gravado) NÃO retoma
        // o assistente automaticamente — isso só acontece se a pessoa clicar
        // de novo em "Cadastrar" na tela de login (ver LoginActivity).
        lifecycleScope.launch {
            val usuarioRepository = UsuarioRepository(VaivanDatabase.getInstance(this@SplashActivity).usuarioDao())

            val perfilCompleto = try {
                usuarioRepository.consultarUsuario(usuario.uid) != null
            } catch (erro: Exception) {
                true // sem internet/erro de leitura: não trava o usuário no login por engano
            }

            val destino = if (perfilCompleto) EscolhaTipoPerfilActivity::class.java else LoginActivity::class.java

            startActivity(Intent(this@SplashActivity, destino))
            finish()
        }
    }
}
