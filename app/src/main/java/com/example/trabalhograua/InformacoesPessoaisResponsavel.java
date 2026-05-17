package com.example.trabalhograua;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Calendar;

public class InformacoesPessoais extends AppCompatActivity {

    private TextInputEditText edtNomeCompleto, edtCpf;

    private TextInputLayout layoutNome, layoutCpf;

    private TextView txtErroCpf, txtErroIdade, txtErroNome;

    private Spinner spinnerDia, spinnerMes, spinnerAno;

    private MaterialButton btnContinuar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_informacoes_pessoais);

        // CAMPOS
        edtNomeCompleto = findViewById(R.id.edtNomeCompleto);
            //SOMENTE LETRAS
            edtNomeCompleto.setFilters(new InputFilter[] {
                    (source, start, end, dest, dstart, dend) -> {

                        if (source.equals("")) {
                            return null;
                        }

                        // aceita somente letras e espaços
                        if (!source.toString().matches("[a-zA-ZáàâãéèêíìóòôõúùçÁÀÂÃÉÈÊÍÌÓÒÔÕÚÙÇ ]+")) {
                            return "";
                        }

                        return null;
                    }
            });

        edtCpf = findViewById(R.id.edtCpf);
            //MASCARA
            edtCpf.addTextChangedListener(
                    MascaraUtil.inserir(
                            "###.###.###-##",
                            edtCpf
                    )
            );

        // LAYOUTS
        layoutNome = findViewById(R.id.layoutNome);
        layoutCpf = findViewById(R.id.layoutCpf);

        // TEXTOS DE ERRO
        txtErroCpf = findViewById(R.id.txtErroCpf);
        txtErroIdade = findViewById(R.id.txtErroIdade);
        txtErroNome = findViewById(R.id.txtErroNome);

        // SPINNERS
        spinnerDia = findViewById(R.id.spinnerDia);
        spinnerMes = findViewById(R.id.spinnerMes);
        spinnerAno = findViewById(R.id.spinnerAno);

        // BOTÃO
        btnContinuar = findViewById(R.id.btnContinuar);

        // ESCONDER ERROS
        txtErroCpf.setVisibility(View.GONE);
        txtErroIdade.setVisibility(View.GONE);
        txtErroNome.setVisibility(View.GONE);

        // CONFIGURAR SPINNERS
        configurarSpinnersData();

        // CLICK BOTÃO
        btnContinuar.setOnClickListener(v -> validarFormulario());
    }

    private void configurarSpinnersData() {

        // DIAS
        String[] dias = new String[31];

        for (int i = 0; i < 31; i++) {
            dias[i] = String.valueOf(i + 1);
        }

        // MESES
        String[] meses = {
                "Jan", "Fev", "Mar", "Abr",
                "Mai", "Jun", "Jul", "Ago",
                "Set", "Out", "Nov", "Dez"
        };

        // ANOS
        int anoAtual = Calendar.getInstance().get(Calendar.YEAR);

        String[] anos = new String[100];

        for (int i = 0; i < 100; i++) {
            anos[i] = String.valueOf(anoAtual - i);
        }

        // ADAPTER DIA
        ArrayAdapter<String> adapterDia = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                dias
        );

        adapterDia.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spinnerDia.setAdapter(adapterDia);

        // ADAPTER MES
        ArrayAdapter<String> adapterMes = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                meses
        );

        adapterMes.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spinnerMes.setAdapter(adapterMes);

        // ADAPTER ANO
        ArrayAdapter<String> adapterAno = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                anos
        );

        adapterAno.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spinnerAno.setAdapter(adapterAno);
    }

    private void validarFormulario() {

        String nome = obterTexto(edtNomeCompleto);
        String cpf = obterTexto(edtCpf);

        boolean formularioValido = true;

        // =========================
        // VALIDAR NOME
        // =========================

        if (nome.length() < 3) {

            txtErroNome.setVisibility(View.VISIBLE);

            layoutNome.setBackgroundResource(
                    R.drawable.bg_input_white_red
            );

            formularioValido = false;

        } else {

            txtErroNome.setVisibility(View.GONE);

            layoutNome.setBackgroundResource(
                    R.drawable.bg_input_white
            );
        }

        // =========================
        // VALIDAR CPF
        // =========================

        if (!cpfValido(cpf)) {

            txtErroCpf.setVisibility(View.VISIBLE);

            layoutCpf.setBackgroundResource(
                    R.drawable.bg_input_white_red
            );

            formularioValido = false;

        } else {

            txtErroCpf.setVisibility(View.GONE);

            layoutCpf.setBackgroundResource(
                    R.drawable.bg_input_white
            );
        }

        // =========================
        // VALIDAR IDADE
        // =========================

        int anoSelecionado = Integer.parseInt(
                spinnerAno.getSelectedItem().toString()
        );

        int anoAtual = Calendar.getInstance().get(Calendar.YEAR);

        int idade = anoAtual - anoSelecionado;

        if (idade < 18) {

            txtErroIdade.setVisibility(View.VISIBLE);

            formularioValido = false;

        } else {

            txtErroIdade.setVisibility(View.GONE);
        }

        // =========================
        // PRÓXIMA TELA
        // =========================

        if (formularioValido) {

            Intent intent = new Intent(
                    InformacoesPessoais.this,
                    ValidacaoEmailResponsavel.class
            );

            startActivity(intent);

            finish();
        }
    }

    private String obterTexto(TextInputEditText editText) {

        return editText.getText() != null
                ? editText.getText().toString().trim()
                : "";
    }

    private boolean cpfValido(String cpf) {

        cpf = cpf.replaceAll("[^0-9]", "");

        return cpf.length() == 11;
    }
}