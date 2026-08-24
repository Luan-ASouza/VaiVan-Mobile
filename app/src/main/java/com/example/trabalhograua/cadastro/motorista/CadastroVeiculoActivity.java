package com.example.trabalhograua.cadastro.motorista;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.trabalhograua.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class CadastroVeiculoActivity extends AppCompatActivity {

    // PADRÕES DE PLACA ACEITOS (ANTIGO E MERCOSUL)
    private static final Pattern PLACA_ANTIGA = Pattern.compile("^[A-Z]{3}[0-9]{4}$");
    private static final Pattern PLACA_MERCOSUL = Pattern.compile("^[A-Z]{3}[0-9][A-Z][0-9]{2}$");

    // CAMPOS
    private TextInputEditText edtPlaca, edtMarca, edtModelo, edtCor, edtCapacidade;

    // LAYOUTS
    private TextInputLayout layoutPlaca, layoutMarca, layoutModelo, layoutCor, layoutCapacidade;

    // TEXTOS DE ERRO
    private TextView txtErroPlaca, txtErroMarca, txtErroModelo, txtErroCor, txtErroCapacidade;

    // SPINNER
    private Spinner spinnerAnoFabricacao;

    // BOTÃO
    private MaterialButton btnContinuar;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cadastro_veiculo);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // CAMPOS
        edtPlaca = findViewById(R.id.edtPlaca);
        // FORÇA MAIÚSCULAS E ACEITA SÓ LETRAS/NÚMEROS
        edtPlaca.setFilters(new InputFilter[]{
                (source, start, end, dest, dstart, dend) -> {

                    if (source.equals("")) {
                        return null;
                    }

                    String filtrado = source.toString()
                            .toUpperCase()
                            .replaceAll("[^A-Z0-9]", "");

                    if (!filtrado.equals(source.toString())) {
                        return filtrado;
                    }

                    return source.toString().toUpperCase();
                }
        });

        edtMarca = findViewById(R.id.edtMarca);
        edtModelo = findViewById(R.id.edtModelo);

        edtCor = findViewById(R.id.edtCor);
        // SOMENTE LETRAS
        edtCor.setFilters(new InputFilter[]{
                (source, start, end, dest, dstart, dend) -> {

                    if (source.equals("")) {
                        return null;
                    }

                    if (!source.toString().matches("[a-zA-ZáàâãéèêíìóòôõúùçÁÀÂÃÉÈÊÍÌÓÒÔÕÚÙÇ ]+")) {
                        return "";
                    }

                    return null;
                }
        });

        edtCapacidade = findViewById(R.id.edtCapacidade);

        // LAYOUTS
        layoutPlaca = findViewById(R.id.layoutPlaca);
        layoutMarca = findViewById(R.id.layoutMarca);
        layoutModelo = findViewById(R.id.layoutModelo);
        layoutCor = findViewById(R.id.layoutCor);
        layoutCapacidade = findViewById(R.id.layoutCapacidade);

        // TEXTOS DE ERRO
        txtErroPlaca = findViewById(R.id.txtErroPlaca);
        txtErroMarca = findViewById(R.id.txtErroMarca);
        txtErroModelo = findViewById(R.id.txtErroModelo);
        txtErroCor = findViewById(R.id.txtErroCor);
        txtErroCapacidade = findViewById(R.id.txtErroCapacidade);

        // SPINNER
        spinnerAnoFabricacao = findViewById(R.id.spinnerAnoFabricacao);

        // BOTÃO
        btnContinuar = findViewById(R.id.btnContinuarVeiculo);

        // ESCONDER ERROS
        txtErroPlaca.setVisibility(View.GONE);
        txtErroMarca.setVisibility(View.GONE);
        txtErroModelo.setVisibility(View.GONE);
        txtErroCor.setVisibility(View.GONE);
        txtErroCapacidade.setVisibility(View.GONE);

        // CONFIGURAR SPINNER DE ANO
        configurarSpinnerAno();

        // CLICK BOTÃO
        btnContinuar.setOnClickListener(v -> validarFormulario());
    }

    private void configurarSpinnerAno() {

        int anoAtual = Calendar.getInstance().get(Calendar.YEAR);

        // ACEITA VEÍCULOS DE ATÉ 30 ANOS ATRÁS + PRÓXIMO ANO (0KM)
        int totalAnos = 32;

        String[] anos = new String[totalAnos];

        for (int i = 0; i < totalAnos; i++) {
            anos[i] = String.valueOf((anoAtual + 1) - i);
        }

        ArrayAdapter<String> adapterAno = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                anos
        );

        adapterAno.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spinnerAnoFabricacao.setAdapter(adapterAno);
    }

    private void validarFormulario() {

        String placa = obterTexto(edtPlaca).toUpperCase();
        String marca = obterTexto(edtMarca);
        String modelo = obterTexto(edtModelo);
        String cor = obterTexto(edtCor);
        String capacidadeTexto = obterTexto(edtCapacidade);

        boolean formularioValido = true;

        // =========================
        // VALIDAR PLACA
        // =========================

        if (!placaValida(placa)) {

            txtErroPlaca.setVisibility(View.VISIBLE);
            layoutPlaca.setBackgroundResource(R.drawable.bg_input_white_red);
            formularioValido = false;

        } else {

            txtErroPlaca.setVisibility(View.GONE);
            layoutPlaca.setBackgroundResource(R.drawable.bg_input_white);
        }

        // =========================
        // VALIDAR MARCA
        // =========================

        if (marca.length() < 2) {

            txtErroMarca.setVisibility(View.VISIBLE);
            layoutMarca.setBackgroundResource(R.drawable.bg_input_white_red);
            formularioValido = false;

        } else {

            txtErroMarca.setVisibility(View.GONE);
            layoutMarca.setBackgroundResource(R.drawable.bg_input_white);
        }

        // =========================
        // VALIDAR MODELO
        // =========================

        if (modelo.length() < 1) {

            txtErroModelo.setVisibility(View.VISIBLE);
            layoutModelo.setBackgroundResource(R.drawable.bg_input_white_red);
            formularioValido = false;

        } else {

            txtErroModelo.setVisibility(View.GONE);
            layoutModelo.setBackgroundResource(R.drawable.bg_input_white);
        }

        // =========================
        // VALIDAR COR
        // =========================

        if (cor.length() < 3) {

            txtErroCor.setVisibility(View.VISIBLE);
            layoutCor.setBackgroundResource(R.drawable.bg_input_white_red);
            formularioValido = false;

        } else {

            txtErroCor.setVisibility(View.GONE);
            layoutCor.setBackgroundResource(R.drawable.bg_input_white);
        }

        // =========================
        // VALIDAR CAPACIDADE
        // =========================

        int capacidade = 0;
        boolean capacidadeValida = true;

        try {
            capacidade = Integer.parseInt(capacidadeTexto);

            if (capacidade < 1 || capacidade > 40) {
                capacidadeValida = false;
            }

        } catch (NumberFormatException e) {
            capacidadeValida = false;
        }

        if (!capacidadeValida) {

            txtErroCapacidade.setVisibility(View.VISIBLE);
            layoutCapacidade.setBackgroundResource(R.drawable.bg_input_white_red);
            formularioValido = false;

        } else {

            txtErroCapacidade.setVisibility(View.GONE);
            layoutCapacidade.setBackgroundResource(R.drawable.bg_input_white);
        }

        // =========================
        // SALVAR E AVANÇAR
        // =========================

        if (formularioValido) {

            int anoFabricacao = Integer.parseInt(
                    spinnerAnoFabricacao.getSelectedItem().toString()
            );

            salvarVeiculo(placa, marca, modelo, cor, anoFabricacao, capacidade);
        }
    }

    private void salvarVeiculo(
            String placa,
            String marca,
            String modelo,
            String cor,
            int anoFabricacao,
            int capacidadePassageiros
    ) {

        String userId = auth.getCurrentUser() != null
                ? auth.getCurrentUser().getUid()
                : null;

        Map<String, Object> dados = new HashMap<>();
        dados.put("placa", placa);
        dados.put("marca", marca);
        dados.put("modelo", modelo);
        dados.put("cor", cor);
        dados.put("anoFabricacao", anoFabricacao);
        dados.put("capacidadePassageiros", capacidadePassageiros);
        dados.put("motoristaId", userId);
        dados.put("dataCadastro", FieldValue.serverTimestamp());

        btnContinuar.setEnabled(false);

        db.collection("veiculos")
                .add(dados)
                .addOnSuccessListener(documentReference -> {

                    Toast.makeText(
                            this,
                            "Veículo cadastrado com sucesso!",
                            Toast.LENGTH_SHORT
                    ).show();

                    Intent intent = new Intent(
                            CadastroVeiculoActivity.this,
                            AnaliseDocumentosActivity.class
                    );

                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(erro -> {

                    btnContinuar.setEnabled(true);

                    Toast.makeText(
                            this,
                            "Erro ao cadastrar veículo: " + erro.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private boolean placaValida(String placa) {

        return PLACA_ANTIGA.matcher(placa).matches()
                || PLACA_MERCOSUL.matcher(placa).matches();
    }

    private String obterTexto(TextInputEditText editText) {

        return editText.getText() != null
                ? editText.getText().toString().trim()
                : "";
    }
}
