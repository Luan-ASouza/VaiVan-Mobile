package com.example.vaivan.ui.inicio.cadastro

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.vaivan.R

class CadastroActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_cadastro)

        if (savedInstanceState == null) {
            abrirCredenciais()
        }
    }

    fun abrirCredenciais() {
        supportFragmentManager
            .beginTransaction()
            .replace(
                R.id.containerCadastro,
                CredenciaisFragment()
            )
            .commit()
    }

    fun abrirInformacoesPessoais() {
        supportFragmentManager
            .beginTransaction()
            .replace(
                R.id.containerCadastro,
                InformacoesPessoaisFragment()
            )
            .commit()
    }

    fun abrirEndereco() {
        supportFragmentManager
            .beginTransaction()
            .replace(
                R.id.containerCadastro,
                EnderecoFragment()
            )
            .commit()
    }

    fun abrirCodigoVerificacao() {
        supportFragmentManager
            .beginTransaction()
            .replace(
                R.id.containerCadastro,
                CodigoVerificacaoFragment()
            )
            .commit()
    }

    fun abrirConfirmacao() {
        supportFragmentManager
            .beginTransaction()
            .replace(
                R.id.containerCadastro,
                ConfirmacaoCadastroFragment()
            )
            .addToBackStack(null)
            .commit()
    }
}