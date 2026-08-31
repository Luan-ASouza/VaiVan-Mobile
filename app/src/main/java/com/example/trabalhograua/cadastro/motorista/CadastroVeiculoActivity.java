package com.example.trabalhograua.cadastro.motorista;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.trabalhograua.R;
import com.example.trabalhograua.data.local.VaivanDatabase;
import com.example.trabalhograua.data.local.entities.DocumentoEntity;
import com.example.trabalhograua.data.local.entities.VeiculoEntity;
import com.example.trabalhograua.data.repository.DocumentoRepository;
import com.example.trabalhograua.data.repository.VeiculoRepository;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

public class CadastroVeiculoActivity extends AppCompatActivity {

    private TextInputEditText edtPlaca, edtModelo, edtAno, edtCapacidade;
    private TextView txtErroPlaca, txtErroModelo, txtErroAno, txtErroCapacidade;
    private Button btnSelecionarCrlv, btnSelecionarAutorizacao, btnSalvar;
    private TextView txtNomeArquivoCrlv, txtNomeArquivoAutorizacao;
    private ImageView iconCheckCrlv, iconCheckAutorizacao;

    private VeiculoRepository veiculoRepository;
    private DocumentoRepository documentoRepository;

    private Uri uriCrlv;
    private Uri uriAutorizacao;

    private static final Pattern REGEX_PLACA = Pattern.compile("^[A-Z]{3}[0-9][0-9A-Z][0-9]{2}$");

    // --- Seletores de arquivo (aceitam imagem OU pdf) ---
    private final ActivityResultLauncher<String[]> pickerCrlv =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    uriCrlv = uri;
                    txtNomeArquivoCrlv.setText(nomeDoArquivo(uri));
                    txtNomeArquivoCrlv.setTextColor(getColor(R.color.black));
                    iconCheckCrlv.setVisibility(View.VISIBLE);
                }
            });

    private final ActivityResultLauncher<String[]> pickerAutorizacao =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    uriAutorizacao = uri;
                    txtNomeArquivoAutorizacao.setText(nomeDoArquivo(uri));
                    txtNomeArquivoAutorizacao.setTextColor(getColor(R.color.black));
                    iconCheckAutorizacao.setVisibility(View.VISIBLE);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cadastro_veiculo);

        veiculoRepository = new VeiculoRepository(
                VaivanDatabase.Companion.getInstance(this).veiculoDao(),
                FirebaseFirestore.getInstance()
        );
        documentoRepository = new DocumentoRepository(
                VaivanDatabase.Companion.getInstance(this).documentoDao(),
                FirebaseFirestore.getInstance()
        );

        edtPlaca = findViewById(R.id.edtPlaca);
        edtModelo = findViewById(R.id.edtModelo);
        edtAno = findViewById(R.id.edtAno);
        edtCapacidade = findViewById(R.id.edtCapacidade);

        txtErroPlaca = findViewById(R.id.txtErroPlaca);
        txtErroModelo = findViewById(R.id.txtErroModelo);
        txtErroAno = findViewById(R.id.txtErroAno);
        txtErroCapacidade = findViewById(R.id.txtErroCapacidade);

        txtErroPlaca.setVisibility(View.GONE);
        txtErroModelo.setVisibility(View.GONE);
        txtErroAno.setVisibility(View.GONE);
        txtErroCapacidade.setVisibility(View.GONE);

        btnSelecionarCrlv = findViewById(R.id.btnSelecionarCrlv);
        btnSelecionarAutorizacao = findViewById(R.id.btnSelecionarAutorizacao);
        txtNomeArquivoCrlv = findViewById(R.id.txtNomeArquivoCrlv);
        txtNomeArquivoAutorizacao = findViewById(R.id.txtNomeArquivoAutorizacao);
        iconCheckCrlv = findViewById(R.id.iconCheckCrlv);
        iconCheckAutorizacao = findViewById(R.id.iconCheckAutorizacao);

        btnSelecionarCrlv.setOnClickListener(v ->
                pickerCrlv.launch(new String[]{"image/*", "application/pdf"}));

        btnSelecionarAutorizacao.setOnClickListener(v ->
                pickerAutorizacao.launch(new String[]{"image/*", "application/pdf"}));

        btnSalvar = findViewById(R.id.btnSalvarVeiculo);
        btnSalvar.setOnClickListener(v -> validarESalvar());
    }

    private String nomeDoArquivo(Uri uri) {
        String nome = "arquivo";
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index != -1 && cursor.moveToFirst()) {
                    nome = cursor.getString(index);
                }
            }
        }
        return nome;
    }

    private void validarESalvar() {
        String placa = edtPlaca.getText() != null
                ? edtPlaca.getText().toString().trim().toUpperCase().replace("-", "") : "";
        String modelo = edtModelo.getText() != null ? edtModelo.getText().toString().trim() : "";
        String anoTexto = edtAno.getText() != null ? edtAno.getText().toString().trim() : "";
        String capacidadeTexto = edtCapacidade.getText() != null ? edtCapacidade.getText().toString().trim() : "";

        boolean valido = true;

        if (!REGEX_PLACA.matcher(placa).matches()) {
            txtErroPlaca.setVisibility(View.VISIBLE);
            valido = false;
        } else {
            txtErroPlaca.setVisibility(View.GONE);
        }

        if (modelo.length() < 2) {
            txtErroModelo.setVisibility(View.VISIBLE);
            valido = false;
        } else {
            txtErroModelo.setVisibility(View.GONE);
        }

        Integer ano = null;
        try {
            ano = Integer.parseInt(anoTexto);
        } catch (NumberFormatException ignored) {}
        int anoAtual = Calendar.getInstance().get(Calendar.YEAR);
        if (ano == null || ano < 1990 || ano > anoAtual + 1) {
            txtErroAno.setVisibility(View.VISIBLE);
            valido = false;
        } else {
            txtErroAno.setVisibility(View.GONE);
        }

        Integer capacidade = null;
        try {
            capacidade = Integer.parseInt(capacidadeTexto);
        } catch (NumberFormatException ignored) {}
        if (capacidade == null || capacidade <= 0 || capacidade > 40) {
            txtErroCapacidade.setVisibility(View.VISIBLE);
            valido = false;
        } else {
            txtErroCapacidade.setVisibility(View.GONE);
        }

        if (!valido) return;

        if (uriCrlv == null) {
            Toast.makeText(this, "Selecione o arquivo do CRLV", Toast.LENGTH_SHORT).show();
            return;
        }
        if (uriAutorizacao == null) {
            Toast.makeText(this, "Selecione o documento de autorização", Toast.LENGTH_SHORT).show();
            return;
        }

        String motoristaId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (motoristaId == null) {
            Toast.makeText(this, "Sessão expirada. Faça login novamente.", Toast.LENGTH_LONG).show();
            return;
        }

        btnSalvar.setEnabled(false);
        btnSalvar.setText("Salvando...");

        VeiculoEntity veiculo = new VeiculoEntity(
                "",
                placa,
                modelo,
                ano,
                capacidade,
                "ATIVO",
                motoristaId,
                0L
        );

        final String motoristaIdFinal = motoristaId;

        veiculoRepository.salvarAsync(
                veiculo,
                veiculoId -> {
                    enviarCrlv(veiculoId, motoristaIdFinal);
                    return null; // exigido pelo tipo Function1<String, Unit> do Kotlin
                },
                erro -> {
                    btnSalvar.setEnabled(true);
                    btnSalvar.setText("Salvar e continuar");
                    Toast.makeText(this, "Erro ao salvar veículo: " + erro.getMessage(), Toast.LENGTH_LONG).show();
                    return null;
                }
        );
    }

    private void enviarCrlv(String veiculoId, String motoristaId) {
        StorageReference ref = FirebaseStorage.getInstance().getReference()
                .child("documentos_veiculos/" + veiculoId + "/crlv_" + System.currentTimeMillis());

        ref.putFile(uriCrlv)
                .addOnSuccessListener(taskSnapshot -> ref.getDownloadUrl()
                        .addOnSuccessListener(url -> salvarDocumento(
                                veiculoId, motoristaId, "CRLV", url.toString(),
                                () -> enviarAutorizacao(veiculoId, motoristaId)
                        ))
                        .addOnFailureListener(this::tratarErroUpload))
                .addOnFailureListener(this::tratarErroUpload);
    }

    private void enviarAutorizacao(String veiculoId, String motoristaId) {
        StorageReference ref = FirebaseStorage.getInstance().getReference()
                .child("documentos_veiculos/" + veiculoId + "/autorizacao_" + System.currentTimeMillis());

        ref.putFile(uriAutorizacao)
                .addOnSuccessListener(taskSnapshot -> ref.getDownloadUrl()
                        .addOnSuccessListener(url -> salvarDocumento(
                                veiculoId, motoristaId, "AUTORIZACAO_TRANSPORTE_ESCOLAR", url.toString(),
                                this::finalizarCadastro
                        ))
                        .addOnFailureListener(this::tratarErroUpload))
                .addOnFailureListener(this::tratarErroUpload);
    }

    // Interface simples só pra representar "o que fazer depois de salvar o documento"
    private interface AoSalvar {
        void executar();
    }

    private void salvarDocumento(String veiculoId, String motoristaId, String tipo, String url, AoSalvar aoSalvar) {
        DocumentoEntity documento = new DocumentoEntity(
                "",
                tipo,
                url,
                new SimpleDateFormat("dd/MM/yyyy", new Locale("pt", "BR")).format(new Date()),
                "EM_ANALISE",
                motoristaId,
                veiculoId,
                0L
        );

        documentoRepository.salvarAsync(
                documento,
                () -> {
                    aoSalvar.executar();
                    return null;
                },
                erro -> {
                    tratarErroUpload(erro);
                    return null;
                }
        );
    }

    private void finalizarCadastro() {
        Toast.makeText(this, "Veículo e documentos enviados!", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, StatusDocumentosVeiculoActivity.class);
        // O id do veículo já foi usado nos passos anteriores; se quiser reabrir
        // a tela de status depois, dá pra buscar o último veículo do motorista.
        startActivity(intent);
        finish();
    }

    private void tratarErroUpload(@NonNull Exception e) {
        btnSalvar.setEnabled(true);
        btnSalvar.setText("Salvar e continuar");
        Toast.makeText(this, "Erro ao enviar documento: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }
}