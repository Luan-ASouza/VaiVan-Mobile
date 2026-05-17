package com.example.trabalhograua;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class EnderecoResponsavel extends AppCompatActivity {

    // CAMPOS
    private TextInputEditText edtCEP, edtCidade, edtEstado,
            edtRua, edtBairro, edtComplemento, edtNumero;

    // LAYOUTS
    private TextInputLayout layoutCEP, layoutCidade,
            layoutEstado, layoutRua,
            layoutBairro, layoutComplemento,
            layoutNumero;

    // TEXTO ERRO
    private TextView txtErroEndereco;

    // BOTÃO
    private MaterialButton btnContinuar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_endereco_responsavel);

        // =========================
        // CAMPOS
        // =========================

        edtCEP = findViewById(R.id.edtCEP);
        edtCidade = findViewById(R.id.edtCidade);
        edtEstado = findViewById(R.id.edtEstado);
        edtRua = findViewById(R.id.edtRua);
        edtBairro = findViewById(R.id.edtBairro);
        edtComplemento = findViewById(R.id.edtComplemento);
        edtNumero = findViewById(R.id.edtNumero);

        // =========================
        // MÁSCARA CEP
        // =========================

        edtCEP.addTextChangedListener(
                MascaraUtil.inserir(
                        "#####-###",
                        edtCEP
                )
        );

        // =========================
        // BUSCAR CEP AUTOMÁTICO
        // =========================

        edtCEP.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s,
                                          int start,
                                          int count,
                                          int after) {
            }

            @Override
            public void onTextChanged(CharSequence s,
                                      int start,
                                      int before,
                                      int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {

                String cep = s.toString()
                        .replaceAll("[^0-9]", "");

                // QUANDO TIVER 8 DÍGITOS
                if (cep.length() == 8) {

                    buscarCEP(cep);
                }
            }
        });

        // =========================
        // LAYOUTS
        // =========================

        layoutCEP = findViewById(R.id.layoutCEP);
        layoutCidade = findViewById(R.id.layoutCidade);
        layoutEstado = findViewById(R.id.layoutEstado);
        layoutRua = findViewById(R.id.layoutRua);
        layoutBairro = findViewById(R.id.layoutBairro);
        layoutComplemento = findViewById(R.id.layoutComplemento);
        layoutNumero = findViewById(R.id.layoutNumero);

        // =========================
        // TEXTO ERRO
        // =========================

        txtErroEndereco = findViewById(R.id.txtErroEndereco);

        txtErroEndereco.setVisibility(View.GONE);

        // =========================
        // BOTÃO
        // =========================

        btnContinuar = findViewById(R.id.btnContinuar);

        btnContinuar.setOnClickListener(v -> validarFormulario());
    }

    // =========================
    // BUSCAR CEP
    // =========================

    private void buscarCEP(String cep) {

        new Thread(() -> {

            try {

                URL url = new URL(
                        "https://viacep.com.br/ws/"
                                + cep +
                                "/json/"
                );

                HttpURLConnection conexao =
                        (HttpURLConnection) url.openConnection();

                conexao.setRequestMethod("GET");

                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        conexao.getInputStream()
                                )
                        );

                StringBuilder resultado =
                        new StringBuilder();

                String linha;

                while ((linha = reader.readLine()) != null) {

                    resultado.append(linha);
                }

                reader.close();

                JSONObject json =
                        new JSONObject(resultado.toString());

                // VERIFICA SE CEP EXISTE
                if (json.has("erro")) {

                    runOnUiThread(() -> {

                        txtErroEndereco.setText(
                                "*CEP não encontrado"
                        );

                        txtErroEndereco.setVisibility(
                                View.VISIBLE
                        );

                        layoutCEP.setBackgroundResource(
                                R.drawable.bg_input_white_red
                        );
                    });

                    return;
                }

                String cidade =
                        json.getString("localidade");

                String estado =
                        json.getString("uf");

                String rua =
                        json.getString("logradouro");

                String bairro =
                        json.getString("bairro");

                runOnUiThread(() -> {

                    edtCidade.setText(cidade);
                    edtEstado.setText(estado);
                    edtRua.setText(rua);
                    edtBairro.setText(bairro);

                    txtErroEndereco.setVisibility(
                            View.GONE
                    );

                    layoutCEP.setBackgroundResource(
                            R.drawable.bg_input_white
                    );
                });

            } catch (Exception e) {

                e.printStackTrace();

                runOnUiThread(() -> {

                    txtErroEndereco.setText(
                            "*Erro ao buscar CEP"
                    );

                    txtErroEndereco.setVisibility(
                            View.VISIBLE
                    );
                });
            }

        }).start();
    }

    private void validarFormulario() {

        String cep = edtCEP.getText() != null
                ? edtCEP.getText().toString().trim()
                : "";

        String cidade = edtCidade.getText() != null
                ? edtCidade.getText().toString().trim()
                : "";

        String estado = edtEstado.getText() != null
                ? edtEstado.getText().toString().trim()
                : "";

        String rua = edtRua.getText() != null
                ? edtRua.getText().toString().trim()
                : "";

        String bairro = edtBairro.getText() != null
                ? edtBairro.getText().toString().trim()
                : "";

        String complemento = edtComplemento.getText() != null
                ? edtComplemento.getText().toString().trim()
                : "";

        String numero = edtNumero.getText() != null
                ? edtNumero.getText().toString().trim()
                : "";

        boolean formularioValido = true;

        // =========================
        // VALIDAR CEP
        // =========================

        if (TextUtils.isEmpty(cep)) {

            layoutCEP.setBackgroundResource(
                    R.drawable.bg_input_white_red
            );

            formularioValido = false;

        } else {

            layoutCEP.setBackgroundResource(
                    R.drawable.bg_input_white
            );
        }

        // =========================
        // VALIDAR CIDADE
        // =========================

        if (TextUtils.isEmpty(cidade)) {

            layoutCidade.setBackgroundResource(
                    R.drawable.bg_input_white_red
            );

            formularioValido = false;

        } else {

            layoutCidade.setBackgroundResource(
                    R.drawable.bg_input_white
            );
        }

        // =========================
        // VALIDAR ESTADO
        // =========================

        if (TextUtils.isEmpty(estado)) {

            layoutEstado.setBackgroundResource(
                    R.drawable.bg_input_white_red
            );

            formularioValido = false;

        } else {

            layoutEstado.setBackgroundResource(
                    R.drawable.bg_input_white
            );
        }

        // =========================
        // VALIDAR RUA
        // =========================

        if (TextUtils.isEmpty(rua)) {

            layoutRua.setBackgroundResource(
                    R.drawable.bg_input_white_red
            );

            formularioValido = false;

        } else {

            layoutRua.setBackgroundResource(
                    R.drawable.bg_input_white
            );
        }

        // =========================
        // VALIDAR BAIRRO
        // =========================

        if (TextUtils.isEmpty(bairro)) {

            layoutBairro.setBackgroundResource(
                    R.drawable.bg_input_white_red
            );

            formularioValido = false;

        } else {

            layoutBairro.setBackgroundResource(
                    R.drawable.bg_input_white
            );
        }

        // =========================
        // VALIDAR NÚMERO
        // =========================

        if (TextUtils.isEmpty(numero)) {

            layoutNumero.setBackgroundResource(
                    R.drawable.bg_input_white_red
            );

            formularioValido = false;

        } else {

            layoutNumero.setBackgroundResource(
                    R.drawable.bg_input_white
            );
        }

        // =========================
        // COMPLEMENTO OPCIONAL
        // =========================

        layoutComplemento.setBackgroundResource(
                R.drawable.bg_input_white
        );

        // =========================
        // RESULTADO
        // =========================

        if (!formularioValido) {

            txtErroEndereco.setText(
                    "*Preencha todos os campos obrigatórios."
            );

            txtErroEndereco.setVisibility(View.VISIBLE);

        } else {

            txtErroEndereco.setVisibility(View.GONE);

            salvarEndereco(
                    cep,
                    cidade,
                    estado,
                    rua,
                    bairro,
                    complemento,
                    numero
            );

            Intent intent = new Intent(
                    EnderecoResponsavel.this,
                    LoginActivity.class
            );

            startActivity(intent);

            finish();
        }
    }

    private void salvarEndereco(
            String cep,
            String cidade,
            String estado,
            String rua,
            String bairro,
            String complemento,
            String numero
    ) {

        System.out.println("CEP: " + cep);
        System.out.println("Cidade: " + cidade);
        System.out.println("Estado: " + estado);
        System.out.println("Rua: " + rua);
        System.out.println("Bairro: " + bairro);
        System.out.println("Complemento: " + complemento);
        System.out.println("Número: " + numero);
    }
}