package com.example.trabalhograua;

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
                    // ADICIONA NA LISTA GLOBAL: Guarda na memória temporária do app
                    DadosGlobais.listaPassageiros.add(nome);

                    Toast.makeText(AdicionarPassageiroActivity.this,
                            "Passageiro " + nome + " cadastrado!", Toast.LENGTH_SHORT).show();
                    finish(); // Volta para a tela anterior
                } else {
                    Toast.makeText(AdicionarPassageiroActivity.this,
                            "Por favor, preencha o Nome e a Data de Nascimento!", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}