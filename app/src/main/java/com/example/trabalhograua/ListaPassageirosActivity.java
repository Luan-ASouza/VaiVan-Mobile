package com.example.trabalhograua;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class ListaPassageirosActivity extends BaseActivity  {

    private TextView txtNome2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lista_passageiros);

        View bottom = findViewById(R.id.bottomNavigation);

        configurarBottomNavigation(
                bottom,
                R.id.navPassageiros
        );

        CardView btnNovoPassageiro = findViewById(R.id.btnNovoPassageiro);
        txtNome2 = findViewById(R.id.txtNome2); // O segundo card da sua lista

        btnNovoPassageiro.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ListaPassageirosActivity.this, AdicionarPassageiroActivity.class);
                startActivity(intent);
            }
        });
    }

    // O onResume roda TODA VEZ que você volta para essa tela
    @Override
    protected void onResume() {
        super.onResume();

        // Se a nossa lista na memória tiver pelo menos 1 passageiro cadastrado...
        if (!DadosGlobais.listaPassageiros.isEmpty()) {
            // Pega o último nome que você cadastrou
            String ultimoCadastrado = DadosGlobais.listaPassageiros.get(DadosGlobais.listaPassageiros.size() - 1);

            // Substitui o texto do segundo card pelo nome real que você digitou!
            txtNome2.setText(ultimoCadastrado);
        }
    }
}