package com.example.trabalhograua;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class DadosDeAcessoResponsavel extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dados_de_acesso_responsavel);

        // EMAIL
        TextInputEditText edtEmail = findViewById(R.id.edtEmail);

        edtEmail.setOnFocusChangeListener((v, hasFocus) -> {

            if (hasFocus) {
                edtEmail.setHint("");
            } else {
                edtEmail.setHint("Digite seu email");
            }

        });

        // BOTÃO
        MaterialButton btnCadastrar = findViewById(R.id.btnCadastrar);

        btnCadastrar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Intent intent = new Intent(
                        DadosDeAcessoResponsavel.this,
                        InformacoesPessoais.class
                );

                startActivity(intent);

            }
        });

    }
}