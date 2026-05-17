package com.example.trabalhograua;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class ValidacaoTelefoneResponsavel extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // LIGA ESTA ACTIVITY AO XML DE VALIDAÇÃO DE TELEFONE DO RESPONSÁVEL
        setContentView(R.layout.activity_validacao_telefone_responsavel);

        // BOTÃO CONTINUAR
        MaterialButton btnContinuar = findViewById(R.id.btnContinuarValidacaoTelefone);

        // AO CLICAR, ABRE A PRÓXIMA TELA DO FLUXO DO RESPONSÁVEL
        btnContinuar.setOnClickListener(v -> {
            Intent intent = new Intent(
                    ValidacaoTelefoneResponsavel.this,
                    DadosDeAcessoResponsavel.class
            );

            intent.putExtra("tipo", "Responsável");

            startActivity(intent);
        });
    }
}