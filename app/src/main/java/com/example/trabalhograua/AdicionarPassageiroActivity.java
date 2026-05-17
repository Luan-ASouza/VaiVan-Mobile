package com.example.trabalhograua;

import android.content.Intent;
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
        setContentView(R.layout.activity_adicionar_passageiro);

        ImageView btnVoltar = findViewById(R.id.btnVoltar);
        Button btnSalvar = findViewById(R.id.btnSalvar);
        EditText edtNome = findViewById(R.id.edtNomePassageiro);
        EditText edtNascimento = findViewById(R.id.edtNascimento);

        btnVoltar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        btnSalvar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String nome = edtNome.getText().toString().trim();
                String nascimento = edtNascimento.getText().toString().trim();

                if (!nome.isEmpty() && !nascimento.isEmpty()) {
                    // 1. Salva temporariamente na memória RAM do aplicativo
                    DadosGlobais.listaPassageiros.add(nome);

                    // 2. Redireciona imediatamente para a tela de procurar motorista/rota
                    Intent intent = new Intent(AdicionarPassageiroActivity.this, ProcurarMotoristaActivity.class);
                    startActivity(intent);

                    // 3. Fecha a tela de cadastro por baixo
                    finish();
                } else {
                    Toast.makeText(AdicionarPassageiroActivity.this,
                            "Por favor, preencha o Nome e a Data de Nascimento!", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}