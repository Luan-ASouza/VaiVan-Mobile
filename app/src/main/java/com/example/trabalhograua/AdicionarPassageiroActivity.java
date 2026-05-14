package com.example.trabalhograua; // Garanta que este nome seja igual ao do seu projeto

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class AdicionarPassageiroActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Define o layout que criamos na pasta res/layout
        setContentView(R.layout.activity_adicionar_passageiro);

        // Referenciando os componentes do XML
        ImageView btnVoltar = findViewById(R.id.btnVoltar);
        Button btnSalvar = findViewById(R.id.btnSalvar);
        EditText edtNome = findViewById(R.id.edtNomePassageiro);
        EditText edtNascimento = findViewById(R.id.edtNascimento);

        // Ação do botão Voltar
        btnVoltar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish(); // Fecha a tela atual e volta para a anterior
            }
        });

        // Ação do botão Salvar
        btnSalvar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String nome = edtNome.getText().toString().trim();

                if (!nome.isEmpty()) {
                    Toast.makeText(AdicionarPassageiroActivity.this,
                            "Passageiro " + nome + " salvo!", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(AdicionarPassageiroActivity.this,
                            "Por favor, preencha o nome!", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}