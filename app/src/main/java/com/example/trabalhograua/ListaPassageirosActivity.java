package com.example.trabalhograua; // Verifique se o nome do pacote está igual aos outros arquivos

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class ListaPassageirosActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Liga este código ao arquivo XML de layout da lista
        setContentView(R.layout.activity_lista_passageiros);

        // Encontra o card/botão de "Adicionar Passageiro +" pelo ID que definimos no XML
        CardView btnNovoPassageiro = findViewById(R.id.btnNovoPassageiro);

        // Define o que acontece ao clicar no botão
        btnNovoPassageiro.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Comando para mudar da tela de Lista para a tela de Adicionar
                Intent intent = new Intent(ListaPassageirosActivity.this, AdicionarPassageiroActivity.class);
                startActivity(intent);
            }
        });
    }
}
