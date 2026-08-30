package com.example.trabalhograua.ui.responsavel.passageiros

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.trabalhograua.R
import com.example.trabalhograua.cadastro.MascaraUtil
import com.example.trabalhograua.data.local.VaivanDatabase
import com.example.trabalhograua.data.local.entities.PassageiroEntity
import com.example.trabalhograua.data.repository.PassageiroRepository
import com.example.trabalhograua.util.DataUtil
import com.google.firebase.auth.FirebaseAuth

/**
 * Tela em que o RESPONSÁVEL cadastra um passageiro (filho) menor de idade.
 *
 * Implementa o RF3 (Cadastro do passageiro) e a parte do RF4 (Vínculo de
 * passageiros) referente a estudantes menores de 18 anos: o vínculo é
 * criado automaticamente dentro da conta do responsável, sem necessidade
 * de confirmação (RNF 4.3 do TCC).
 *
 * Estudantes maiores de idade se cadastram sozinhos e SOLICITAM o vínculo
 * (fluxo diferente, fora do escopo desta tela).
 */
class AdicionarPassageiroActivity : AppCompatActivity() {

    private lateinit var edtNome: EditText
    private lateinit var edtNascimento: EditText
    private lateinit var edtNecessidades: EditText
    private lateinit var edtObservacoes: EditText
    private lateinit var btnSim: Button
    private lateinit var btnNao: Button
    private lateinit var btnSalvar: Button

    private var temNecessidadeEspecial = false

    private val repository by lazy {
        PassageiroRepository(VaivanDatabase.getInstance(this).passageiroDao())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_adicionar_passageiro)

        val btnVoltar = findViewById<ImageView>(R.id.btnVoltar)
        btnSalvar = findViewById(R.id.btnSalvar)
        edtNome = findViewById(R.id.edtNomePassageiro)
        edtNascimento = findViewById(R.id.edtNascimento)
        edtNecessidades = findViewById(R.id.edtNecessidades)
        edtObservacoes = findViewById(R.id.edtObservacoes)
        btnSim = findViewById(R.id.btnSimNecessidade)
        btnNao = findViewById(R.id.btnNaoNecessidade)

        // Máscara de data: o usuário só digita números, o "/" aparece sozinho
        edtNascimento.addTextChangedListener(
            MascaraUtil.inserir("##/##/####", edtNascimento)
        )

        configurarToggleNecessidades()

        btnVoltar.setOnClickListener { finish() }
        btnSalvar.setOnClickListener { validarESalvar() }
    }

    /** Configura os botões "Sim"/"Não" para mostrar ou esconder o campo de descrição. */
    private fun configurarToggleNecessidades() {
        // Estado inicial: "Não" selecionado, campo de descrição escondido
        atualizarBotoesNecessidade(selecionado = false)

        btnSim.setOnClickListener { atualizarBotoesNecessidade(selecionado = true) }
        btnNao.setOnClickListener { atualizarBotoesNecessidade(selecionado = false) }
    }

    private fun atualizarBotoesNecessidade(selecionado: Boolean) {
        temNecessidadeEspecial = selecionado

        btnSim.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (selecionado) 0xFFF5B42D.toInt() else 0xFFB0B0B0.toInt()
        )
        btnNao.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (selecionado) 0xFFB0B0B0.toInt() else 0xFF707070.toInt()
        )

        edtNecessidades.visibility = if (selecionado) {
            android.view.View.VISIBLE
        } else {
            edtNecessidades.setText("")
            android.view.View.GONE
        }
    }

    private fun validarESalvar() {
        val nome = edtNome.text?.toString()?.trim() ?: ""
        val nascimentoDigitado = edtNascimento.text?.toString()?.trim() ?: ""

        if (nome.length < 3) {
            Toast.makeText(this, "Informe o nome completo do passageiro.", Toast.LENGTH_SHORT).show()
            return
        }

        if (nascimentoDigitado.length < 10) {
            Toast.makeText(this, "Informe a data de nascimento completa (DD/MM/AAAA).", Toast.LENGTH_SHORT).show()
            return
        }

        val nascimentoIso = DataUtil.paraIso(nascimentoDigitado)
        if (nascimentoIso == null) {
            Toast.makeText(this, "Data de nascimento inválida.", Toast.LENGTH_SHORT).show()
            return
        }

        val idade = DataUtil.calcularIdade(nascimentoIso)
        if (idade < 0) {
            Toast.makeText(this, "Não foi possível calcular a idade informada.", Toast.LENGTH_SHORT).show()
            return
        }

        // RF4: por essa tela, só entram passageiros MENORES de idade.
        // Maiores de idade criam a própria conta e enviam uma solicitação de vínculo.
        if (idade >= 18) {
            Toast.makeText(
                this,
                "Este passageiro já é maior de idade. Peça para ele criar a própria conta " +
                        "e enviar uma solicitação de vínculo para você.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Sessão expirada. Faça login novamente.", Toast.LENGTH_SHORT).show()
            return
        }

        val descricaoNecessidades = if (temNecessidadeEspecial) {
            edtNecessidades.text?.toString()?.trim() ?: ""
        } else {
            ""
        }
        val observacoes = edtObservacoes.text?.toString()?.trim() ?: ""

        val novoPassageiro = PassageiroEntity(
            id = "",
            nome = nome,
            dataNascimento = nascimentoIso,
            status = "ATIVO",
            maiorIdade = false,
            necessidadesEspeciais = temNecessidadeEspecial,
            descricaoNecessidades = descricaoNecessidades,
            observacoes = observacoes,
            responsavelId = uid
        )

        btnSalvar.isEnabled = false

        repository.salvarAsync(
            novoPassageiro,
            onSuccess = {
                Toast.makeText(this, "Passageiro cadastrado com sucesso!", Toast.LENGTH_SHORT).show()
                finish()
            },
            onError = { erro ->
                btnSalvar.isEnabled = true
                Toast.makeText(
                    this,
                    "Erro ao cadastrar passageiro: ${erro.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }
}