package com.example.trabalhograua.cadastro.responsavel.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.TextView;
import com.example.trabalhograua.cadastro.CadastroSession;
import com.example.trabalhograua.cadastro.responsavel.CadastroResponsavel;
import androidx.appcompat.app.AppCompatActivity;
import com.example.trabalhograua.cadastro.MascaraUtil;
import com.example.trabalhograua.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.CheckBox;
import android.content.Intent;

public class DadosDeAcessoResponsavel extends AppCompatActivity {

    private TextInputEditText edtEmail, edtTelefone, edtSenha, edtConfirmarSenha;
    private TextView txtErroEmail, txtInfoSenha, txtErroSenha, txtErroTermos, txtErroTelefone;
    private Spinner spinnerDDD;
    private CheckBox checkTermos;
    private TextInputLayout layoutEmail, layoutSenha, layoutConfirmarSenha, layoutTelefone;
    private MaterialButton btnCadastrar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dados_de_acesso_responsavel);

        // Inicialização dos campos (Campo Nome removido)
        edtEmail = findViewById(R.id.edtEmail);
        edtTelefone = findViewById(R.id.edtTelefone);
        edtSenha = findViewById(R.id.edtSenha);
        edtConfirmarSenha = findViewById(R.id.edtConfirmarSenha);

        edtTelefone.addTextChangedListener(MascaraUtil.inserir("(##) #####-####", edtTelefone));

        txtErroEmail = findViewById(R.id.txtErroEmail);
        txtInfoSenha = findViewById(R.id.txtInfoSenha);
        txtErroSenha = findViewById(R.id.txtErroSenha);
        txtErroTelefone = findViewById(R.id.txtErroTelefone);
        txtErroTermos = findViewById(R.id.txtErroTermos);
        btnCadastrar = findViewById(R.id.btnCadastrar);
        spinnerDDD = findViewById(R.id.spinnerDDD);
        checkTermos = findViewById(R.id.checkTermos);

        txtErroEmail.setVisibility(View.GONE);
        txtInfoSenha.setVisibility(View.GONE);
        txtErroSenha.setVisibility(View.GONE);
        txtErroTelefone.setVisibility(View.GONE);
        txtErroTermos.setVisibility(View.GONE);

        layoutEmail = findViewById(R.id.layoutEmail);
        layoutSenha = findViewById(R.id.layoutSenha);
        layoutConfirmarSenha = findViewById(R.id.layoutConfirmarSenha);
        layoutTelefone = findViewById(R.id.layoutTelefone);

        btnCadastrar.setOnClickListener(v -> validarFormulario());

        String[] ddds = {"+55", "+1", "+351"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, ddds);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDDD.setAdapter(adapter);
        spinnerDDD.setSelection(0);
    }

    private void validarFormulario() {
        String email = edtEmail.getText() != null ? edtEmail.getText().toString().trim() : "";
        String telefone = edtTelefone.getText() != null ? edtTelefone.getText().toString().trim() : "";
        String senha = edtSenha.getText() != null ? edtSenha.getText().toString().trim() : "";
        String confirmarSenha = edtConfirmarSenha.getText() != null ? edtConfirmarSenha.getText().toString().trim() : "";

        boolean formularioValido = true;

        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            txtErroEmail.setText("*Digite um email válido");
            txtErroEmail.setVisibility(View.VISIBLE);
            layoutEmail.setBackgroundResource(R.drawable.bg_input_white_red);
            formularioValido = false;
        } else {
            txtErroEmail.setVisibility(View.GONE);
            layoutEmail.setBackgroundResource(R.drawable.bg_input_white);
        }

        if (!telefoneValido(telefone)) {
            txtErroTelefone.setVisibility(View.VISIBLE);
            layoutTelefone.setBackgroundResource(R.drawable.bg_input_white_red);
            formularioValido = false;
        } else {
            txtErroTelefone.setVisibility(View.GONE);
            layoutTelefone.setBackgroundResource(R.drawable.bg_input_white);
        }

        if (!senhaValida(senha)) {
            txtInfoSenha.setVisibility(View.VISIBLE);
            layoutSenha.setBackgroundResource(R.drawable.bg_input_white_red);
            formularioValido = false;
        } else {
            txtInfoSenha.setVisibility(View.GONE);
            layoutSenha.setBackgroundResource(R.drawable.bg_input_white);
        }

        if (!senha.equals(confirmarSenha)) {
            txtErroSenha.setVisibility(View.VISIBLE);
            layoutConfirmarSenha.setBackgroundResource(R.drawable.bg_input_white_red);
            formularioValido = false;
        } else {
            txtErroSenha.setVisibility(View.GONE);
            layoutConfirmarSenha.setBackgroundResource(R.drawable.bg_input_white);
        }

        if (termosAceitos() && formularioValido) {
            verificarEmailFirebase(email, telefone, senha);
        }
    }

    @SuppressWarnings("deprecation")
    private void verificarEmailFirebase(String email, String telefone, String senha) {
        btnCadastrar.setEnabled(false);
        FirebaseAuth.getInstance().fetchSignInMethodsForEmail(email)
                .addOnCompleteListener(task -> {
                    btnCadastrar.setEnabled(true);
                    if (task.isSuccessful()) {
                        // Se a lista não estiver vazia, o e-mail já existe
                        boolean existe = !task.getResult().getSignInMethods().isEmpty();
                        if (existe) {
                            txtErroEmail.setText("*Email já cadastrado no sistema");
                            txtErroEmail.setVisibility(View.VISIBLE);
                            layoutEmail.setBackgroundResource(R.drawable.bg_input_white_red);
                        } else {
                            salvarDadosTemporarios(email, telefone, senha);
                            startActivity(new Intent(this, InformacoesPessoaisResponsavel.class));
                        }
                    } else {
                        txtErroEmail.setText("*Erro ao verificar e-mail. Tente novamente.");
                        txtErroEmail.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void salvarDadosTemporarios(String email, String telefone, String senha) {
        CadastroResponsavel cadastro = CadastroSession.INSTANCE.getCadastroResponsavel();
        cadastro.setEmail(email);
        cadastro.setTelefone(telefone);
        cadastro.setSenha(senha);
    }

    private boolean termosAceitos() {
        if (!checkTermos.isChecked()) {
            txtErroTermos.setVisibility(View.VISIBLE);
            return false;
        }
        txtErroTermos.setVisibility(View.GONE);
        return true;
    }

    private boolean telefoneValido(String t) {
        t = t.replaceAll("[^0-9]", "");
        return t.length() >= 10 && t.length() <= 11;
    }

    private boolean senhaValida(String s) {
        return s.length() >= 8 && s.matches(".*\\d.*") && s.matches(".*[!@#$%^&*()_+=|<>?{}\\[\\]~-].*");
    }
}