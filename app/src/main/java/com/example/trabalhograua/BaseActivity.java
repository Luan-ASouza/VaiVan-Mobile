package com.example.trabalhograua;

import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

public class BaseActivity extends AppCompatActivity {

    protected void configurarBottomNavigation(View view, int telaAtual) {

        LinearLayout passageiros = view.findViewById(R.id.navPassageiros);
        LinearLayout rotas = view.findViewById(R.id.navRotas);
        LinearLayout chat = view.findViewById(R.id.navChat);
        LinearLayout perfil = view.findViewById(R.id.navPerfil);

        selecionar(passageiros, telaAtual == R.id.navPassageiros);
        selecionar(rotas, telaAtual == R.id.navRotas);
        selecionar(chat, telaAtual == R.id.navChat);
        selecionar(perfil, telaAtual == R.id.navPerfil);

        passageiros.setOnClickListener(v -> {

            if (telaAtual != R.id.navPassageiros) {

                startActivity(new Intent(this, ListaPassageirosActivity.class));

                overridePendingTransition(
                        android.R.anim.fade_in,
                        android.R.anim.fade_out
                );
            }
        });

        rotas.setOnClickListener(v -> {

            if (telaAtual != R.id.navRotas) {

                startActivity(new Intent(this, MotoristaActivity.class));

                overridePendingTransition(
                        android.R.anim.fade_in,
                        android.R.anim.fade_out
                );
            }
        });

        chat.setOnClickListener(v -> {

            // Futuro
        });

        perfil.setOnClickListener(v -> {

            // Futuro
        });
    }

    private void selecionar(LinearLayout layout, boolean ativo) {

        if (ativo) {
            layout.setBackgroundResource(R.drawable.bg_nav_item_selected);
        } else {
            layout.setBackgroundResource(android.R.color.transparent);
        }
    }
}