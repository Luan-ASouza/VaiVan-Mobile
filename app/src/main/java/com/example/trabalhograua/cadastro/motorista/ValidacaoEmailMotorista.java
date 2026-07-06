package com.example.trabalhograua.cadastro.motorista;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.trabalhograua.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class ValidacaoEmailMotorista extends AppCompatActivity {

    private TextInputEditText edtCodigo1, edtCodigo2,
            edtCodigo3, edtCodigo4;

    private TextInputLayout layoutCodigo1, layoutCodigo2,
            layoutCodigo3, layoutCodigo4;

    private TextView txtErroCodigo, txtReenviarCodigo;

    private MaterialButton btnContinuar;

    // CÓDIGO MOCK
    private final String CODIGO_CORRETO = "1234";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_validacao_email_motorista);

        // =========================
        // CAMPOS
        // =========================

        edtCodigo1 = findViewById(R.id.edtCodigo1);
        edtCodigo2 = findViewById(R.id.edtCodigo2);
        edtCodigo3 = findViewById(R.id.edtCodigo3);
        edtCodigo4 = findViewById(R.id.edtCodigo4);

        // =========================
        // LAYOUTS
        // =========================

        layoutCodigo1 = findViewById(R.id.layoutCodigo1);
        layoutCodigo2 = findViewById(R.id.layoutCodigo2);
        layoutCodigo3 = findViewById(R.id.layoutCodigo3);
        layoutCodigo4 = findViewById(R.id.layoutCodigo4);

        // =========================
        // TEXTOS
        // =========================

        txtErroCodigo = findViewById(R.id.txtErroCodigo);
        txtReenviarCodigo = findViewById(R.id.txtReenviarCodigo);

        // =========================
        // BOTÃO
        // =========================

        btnContinuar = findViewById(R.id.btnContinuar);

        // =========================
        // ESCONDER ERRO
        // =========================

        txtErroCodigo.setVisibility(View.GONE);

        // =========================
        // AUTO PASSAR PRO PRÓXIMO CAMPO
        // =========================

        configurarAutoAvanco(edtCodigo1, edtCodigo2);
        configurarAutoAvanco(edtCodigo2, edtCodigo3);
        configurarAutoAvanco(edtCodigo3, edtCodigo4);

        // =========================
        // VALIDAR BOTÃO
        // =========================

        btnContinuar.setOnClickListener(v -> validarCodigo());

        // =========================
        // REENVIAR CÓDIGO
        // =========================

        txtReenviarCodigo.setOnClickListener(v -> {

            txtErroCodigo.setVisibility(View.GONE);

            // AQUI VOCÊ PODE:
            // reenviar email
            // chamar API
            // firebase etc
        });
    }

    private void validarCodigo() {

        String codigo1 = edtCodigo1.getText() != null
                ? edtCodigo1.getText().toString().trim()
                : "";

        String codigo2 = edtCodigo2.getText() != null
                ? edtCodigo2.getText().toString().trim()
                : "";

        String codigo3 = edtCodigo3.getText() != null
                ? edtCodigo3.getText().toString().trim()
                : "";

        String codigo4 = edtCodigo4.getText() != null
                ? edtCodigo4.getText().toString().trim()
                : "";

        String codigoDigitado =
                codigo1 + codigo2 + codigo3 + codigo4;

        if (codigoDigitado.equals(CODIGO_CORRETO)) {

            txtErroCodigo.setVisibility(View.GONE);

            resetarCampos();

            // PRÓXIMA ACTIVITY

            Intent intent = new Intent(
                    ValidacaoEmailMotorista.this,
                    EnderecoMotorista.class
            );

            startActivity(intent);

            finish();

        } else {

            txtErroCodigo.setVisibility(View.VISIBLE);

            mostrarErroCampos();
        }
    }

    private void mostrarErroCampos() {

        layoutCodigo1.setBackgroundResource(
                R.drawable.bg_input_white_red
        );

        layoutCodigo2.setBackgroundResource(
                R.drawable.bg_input_white_red
        );

        layoutCodigo3.setBackgroundResource(
                R.drawable.bg_input_white_red
        );

        layoutCodigo4.setBackgroundResource(
                R.drawable.bg_input_white_red
        );
    }

    private void resetarCampos() {

        layoutCodigo1.setBackgroundResource(
                R.drawable.bg_input_white
        );

        layoutCodigo2.setBackgroundResource(
                R.drawable.bg_input_white
        );

        layoutCodigo3.setBackgroundResource(
                R.drawable.bg_input_white
        );

        layoutCodigo4.setBackgroundResource(
                R.drawable.bg_input_white
        );
    }

    private void configurarAutoAvanco(
            TextInputEditText atual,
            TextInputEditText proximo
    ) {

        atual.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(
                    CharSequence s,
                    int start,
                    int count,
                    int after
            ) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s,
                    int start,
                    int before,
                    int count
            ) {

                if (s.length() == 1) {

                    proximo.requestFocus();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }
}