package com.example.vaivan.ui.responsavel.rotas

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.vaivan.R
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.entities.RotaEntity
import com.example.vaivan.data.local.entities.SolicitacaoInclusaoEntity
import com.example.vaivan.data.repository.RotaRepository
import com.example.vaivan.data.repository.SolicitacaoInclusaoRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class PesquisarRotasActivity : AppCompatActivity() {

    private lateinit var edtDesembarque: EditText
    private lateinit var edtHoraInicio: EditText
    private lateinit var containerResultados: LinearLayout
    private lateinit var txtSemResultados: TextView

    private lateinit var botoesTurno: Map<String, Button>
    private var turnoSelecionado: String? = null

    private var passageiroId: String = ""
    private var nomePassageiro: String = ""
    private var localId: String? = null

    private val rotaRepository by lazy {
        val db = VaivanDatabase.getInstance(this)
        RotaRepository(this, db.rotaDao(), db.paradaRotaDao())
    }

    private val solicitacaoRepository by lazy {
        SolicitacaoInclusaoRepository(this, VaivanDatabase.getInstance(this).solicitacaoInclusaoDao())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pesquisar_rotas_responsavel)

        passageiroId = intent.getStringExtra("passageiroId") ?: ""
        nomePassageiro = intent.getStringExtra("nomePassageiro") ?: ""
        localId = intent.getStringExtra("localId")

        edtDesembarque = findViewById(R.id.edtDesembarque)
        edtHoraInicio = findViewById(R.id.edtHoraInicio)
        containerResultados = findViewById(R.id.containerResultadosRotas)
        txtSemResultados = findViewById(R.id.txtSemResultados)

        botoesTurno = mapOf(
            "MANHA" to findViewById(R.id.btnManha),
            "TARDE" to findViewById(R.id.btnTarde),
            "NOITE" to findViewById(R.id.btnNoite)
        )
        botoesTurno.forEach { (chave, botao) ->
            botao.setOnClickListener {
                turnoSelecionado = chave
                atualizarCoresTurno()
            }
        }

        findViewById<Button>(R.id.btnPesquisarRotas).setOnClickListener { pesquisar() }
    }

    private fun atualizarCoresTurno() {
        botoesTurno.forEach { (chave, botao) ->
            val cor = if (chave == turnoSelecionado) R.color.orange else R.color.light_gray
            botao.backgroundTintList =
                android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, cor))
        }
    }

    private fun pesquisar() {
        val textoBusca = edtDesembarque.text?.toString()?.trim() ?: ""

        lifecycleScope.launch {
            try {
                val resultados = rotaRepository.buscarRotasDisponiveis(textoBusca, turnoSelecionado)
                renderizarResultados(resultados)
            } catch (e: Exception) {
                Toast.makeText(this@PesquisarRotasActivity, "Erro na busca: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun renderizarResultados(rotas: List<RotaEntity>) {
        containerResultados.removeAllViews()

        if (rotas.isEmpty()) {
            txtSemResultados.visibility = android.view.View.VISIBLE
            return
        }
        txtSemResultados.visibility = android.view.View.GONE

        val inflater = layoutInflater
        rotas.forEach { rota ->
            val itemView = inflater.inflate(R.layout.item_resultado_rota, containerResultados, false)

            itemView.findViewById<TextView>(R.id.txtNomeRotaResultado).text =
                "${rota.nome} · Destino: ${rota.destinoEndereco}"
            itemView.findViewById<TextView>(R.id.txtVagasResultado).text =
                "Vagas: ${rota.capacidadeTotal - rota.vagasOcupadas} disponíveis"

            val txtNomeMotorista = itemView.findViewById<TextView>(R.id.txtNomeMotoristaResultado)
            txtNomeMotorista.text = "Carregando motorista..."
            lifecycleScope.launch {
                try {
                    val doc = FirebaseFirestore.getInstance().collection("usuarios").document(rota.motoristaId).get().await()
                    txtNomeMotorista.text = doc.getString("nome") ?: "Motorista"
                } catch (e: Exception) {
                    txtNomeMotorista.text = "Motorista"
                }
            }

            itemView.findViewById<Button>(R.id.btnReservarResultado).setOnClickListener {
                enviarSolicitacao(rota)
            }

            containerResultados.addView(itemView)
        }
    }

    private fun enviarSolicitacao(rota: RotaEntity) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Sessão expirada.", Toast.LENGTH_SHORT).show()
            return
        }

        if (passageiroId.isBlank() || localId.isNullOrBlank()) {
            Toast.makeText(this, "Este aluno precisa ter um local de embarque cadastrado.", Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch {
            try {
                val localDoc = FirebaseFirestore.getInstance().collection("locais").document(localId!!).get().await()
                val nomeLocal = localDoc.getString("nome") ?: "Local"
                val endereco = localDoc.getString("endereco") ?: ""
                val latitude = localDoc.getDouble("latitude") ?: 0.0
                val longitude = localDoc.getDouble("longitude") ?: 0.0

                val solicitacao = SolicitacaoInclusaoEntity(
                    rotaId = rota.id,
                    passageiroId = passageiroId,
                    nomePassageiro = nomePassageiro,
                    responsavelId = uid,
                    motoristaId = rota.motoristaId,
                    localEmbarqueId = localId!!,
                    nomeLocalEmbarque = nomeLocal,
                    enderecoEmbarque = endereco,
                    latitudeEmbarque = latitude,
                    longitudeEmbarque = longitude,
                    horarioDesejado = edtHoraInicio.text?.toString()?.trim() ?: ""
                )

                solicitacaoRepository.enviarSolicitacaoAsync(
                    solicitacao,
                    onSuccess = {
                        Toast.makeText(this@PesquisarRotasActivity, "Solicitação enviada!", Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onError = { erro ->
                        Toast.makeText(this@PesquisarRotasActivity, "Erro: ${erro.message}", Toast.LENGTH_LONG).show()
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(this@PesquisarRotasActivity, "Erro ao carregar o local: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}