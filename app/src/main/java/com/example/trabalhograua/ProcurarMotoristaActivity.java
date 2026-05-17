package com.example.trabalhograua;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class ProcurarMotoristaActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_procurar_motorista);

        Button btnVincular = findViewById(R.id.btnVincularMotorista);

        btnVincular.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(ProcurarMotoristaActivity.this,
                        "Rota contratada com sucesso para o passageiro!", Toast.LENGTH_LONG).show();

                // Fecha essa tela e volta lá para a lista de passageiros
                finish();
            }
        });
    }
}