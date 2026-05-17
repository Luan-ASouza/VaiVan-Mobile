package com.example.trabalhograua;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.trabalhograua.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.CheckBox;
import android.content.Intent;


public class DadosDeAcessoResponsavel extends AppCompatActivity {

    private TextInputEditText edtEmail, edtTelefone, edtSenha, edtConfirmarSenha;
    private TextView txtErroEmail, txtInfoSenha, txtErroSenha;
    private Spinner spinnerDDD;
    private CheckBox checkTermos;
    private TextView txtErroTelefone;
    private TextInputLayout layoutTelefone;
    private TextInputLayout layoutEmail, layoutSenha, layoutConfirmarSenha;

    private MaterialButton btnCadastrar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dados_de_acesso_responsavel);

        // CAMPOS
        edtEmail = findViewById(R.id.edtEmail);
        edtTelefone = findViewById(R.id.edtTelefone);
        edtSenha = findViewById(R.id.edtSenha);
        edtConfirmarSenha = findViewById(R.id.edtConfirmarSenha);


        // TEXTOS DE ERRO
        txtErroEmail = findViewById(R.id.txtErroEmail);
        txtInfoSenha = findViewById(R.id.txtInfoSenha);
        txtErroSenha = findViewById(R.id.txtErroSenha);
        txtErroTelefone = findViewById(R.id.txtErroTelefone);


        // BOTÃO
        btnCadastrar = findViewById(R.id.btnCadastrar);

        //SPINNER
        spinnerDDD = findViewById(R.id.spinnerDDD);

        //CHECKBOX
        checkTermos = findViewById(R.id.checkTermos);

        // ESCONDE ERROS INICIALMENTE
        txtErroEmail.setVisibility(View.GONE);
        txtInfoSenha.setVisibility(View.GONE);
        txtErroSenha.setVisibility(View.GONE);
        txtErroTelefone.setVisibility(View.GONE);

        layoutEmail = findViewById(R.id.layoutEmail);
        layoutSenha = findViewById(R.id.layoutSenha);
        layoutConfirmarSenha = findViewById(R.id.layoutConfirmarSenha);
        layoutTelefone = findViewById(R.id.layoutTelefone);

        btnCadastrar.setOnClickListener(v -> validarFormulario());
    }

    private void validarFormulario() {

        // PEGAR DADOS
        String email = edtEmail.getText().toString().trim();
        String telefone = edtTelefone.getText().toString().trim();
        String senha = edtSenha.getText().toString().trim();
        String confirmarSenha = edtConfirmarSenha.getText().toString().trim();

        boolean formularioValido = true;

        // =========================
        // VALIDAR EMAIL
        // =========================

        if (TextUtils.isEmpty(email) ||
                !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {

            txtErroEmail.setText("*Digite um email válido");
            txtErroEmail.setVisibility(View.VISIBLE);
            layoutEmail.setBackgroundResource(R.drawable.bg_input_white_red);

            formularioValido = false;

        } else {

            txtErroEmail.setVisibility(View.GONE);
            layoutEmail.setBackgroundResource(R.drawable.bg_input_white);
        }

        // =========================
        // VALIDAR TELEFONE
        // =========================

        if (!telefoneValido(telefone)) {

            txtErroTelefone.setVisibility(View.VISIBLE);

            layoutTelefone.setBackgroundResource(R.drawable.bg_input_white_red);

            formularioValido = false;

        } else {

            txtErroTelefone.setVisibility(View.GONE);

            layoutTelefone.setBackgroundResource(R.drawable.bg_input_white);
        }

        // =========================
        // VALIDAR SENHA
        // =========================

        if (!senhaValida(senha)) {

            txtInfoSenha.setVisibility(View.VISIBLE);
            layoutSenha.setBackgroundResource(R.drawable.bg_input_white_red);

            formularioValido = false;

        } else {

            txtInfoSenha.setVisibility(View.GONE);
            layoutSenha.setBackgroundResource(R.drawable.bg_input_white);
        }

        // =========================
        // CONFIRMAR SENHA
        // =========================

        if (!senha.equals(confirmarSenha)) {

            txtErroSenha.setVisibility(View.VISIBLE);
            layoutConfirmarSenha.setBackgroundResource(R.drawable.bg_input_white_red);

            formularioValido = false;

        } else {

            txtErroSenha.setVisibility(View.GONE);
            layoutConfirmarSenha.setBackgroundResource(R.drawable.bg_input_white);
        }

        // =========================
        // SE ESTIVER TUDO CERTO
        // =========================

        if (checkTermos.isChecked() && formularioValido) {

            salvarUsuario(email, telefone, senha);

            Intent intent = new Intent(
                    DadosDeAcessoResponsavel.this,
                    InformacoesPessoais.class
            );

            startActivity(intent);

            finish();
        }

        // =========================
        // SPINNER
        // =========================

        String[] ddds = {"+55", "+1", "+351"};

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                ddds
        );

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        spinnerDDD.setAdapter(adapter);

        // COMEÇA COM +55
        spinnerDDD.setSelection(0);
    }


    private boolean telefoneValido(String telefone) {

        // remove espaços e símbolos
        telefone = telefone.replaceAll("[^0-9]", "");

        // celular BR geralmente 10 ou 11 números
        return telefone.length() >= 10 && telefone.length() <= 11;
    }

    private boolean senhaValida(String senha) {

        // mínimo 8 caracteres
        if (senha.length() < 8) {
            return false;
        }

        // pelo menos 1 número
        if (!senha.matches(".*\\d.*")) {
            return false;
        }

        // pelo menos 1 caractere especial
        if (!senha.matches(".*[!@#$%^&*()_+=|<>?{}\\[\\]~-].*")) {
            return false;
        }

        return true;
    }

    private void salvarUsuario(String email, String telefone, String senha) {

        // EXEMPLO
        System.out.println("Email: " + email);
        System.out.println("Telefone: " + telefone);
        System.out.println("Senha: " + senha);

        // Aqui você pode:
        // - salvar no SQLite
        // - Firebase
        // - API
        // - SharedPreferences
    }
}