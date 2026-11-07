package com.example.vaivan.ui.comum.chat

import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.vaivan.R
import com.example.vaivan.core.util.WindowInsetsUtil.aplicarInsets
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.repository.UsuarioRepository
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

/**
 * Extras aceitos:
 *  - "outroUsuarioId" (obrigatório): com quem conversar
 *  - "nomeContato": nome mostrado no topo
 *  - "chatId": id de uma conversa que já existe (usado pela lista de conversas)
 *  - "rotaId", "passageiroId", "passageiroNome", "rotaNome": identificam a viagem e o aluno;
 *    cada combinação tem a sua própria conversa
 *  - "solicitacaoId", "mensagemInicial", "mensagemInicialEm": a solicitação aprovada e o
 *    pedido do responsável, que vira a primeira mensagem da conversa
 */
class ChatActivity : AppCompatActivity() {

    private lateinit var activityChat: LinearLayout
    private lateinit var recyclerMensagens: RecyclerView
    private lateinit var edtMensagem: EditText
    private lateinit var btnEnviar: ImageButton
    private lateinit var txtNomeContato: TextView
    private lateinit var imgFotoContato: ShapeableImageView
    private lateinit var barraMensagem: ConstraintLayout

    private val usuarioRepository by lazy {
        UsuarioRepository(VaivanDatabase.getInstance(this).usuarioDao())
    }

    private val adapter = MensagemAdapter()

    private var listenerMensagens: ListenerRegistration? = null
    private var chatId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_chat)

        // --------------------------------------------------
        // REFERÊNCIAS
        // --------------------------------------------------

        activityChat = findViewById(R.id.activity_chat)
        recyclerMensagens = findViewById(R.id.recyclerMensagens)
        edtMensagem = findViewById(R.id.edtMensagem)
        btnEnviar = findViewById(R.id.btnEnviar)
        txtNomeContato = findViewById(R.id.txtNomeContato)
        imgFotoContato = findViewById(R.id.imgFotoContato)
        barraMensagem = findViewById(R.id.barraMensagem)

        aplicarInsets(activityChat)

        // --------------------------------------------------
        // RECYCLERVIEW
        // --------------------------------------------------

        recyclerMensagens.layoutManager =
            LinearLayoutManager(this)

        recyclerMensagens.adapter = adapter

        // --------------------------------------------------
        // PEGAR DADOS DA CONVERSA
        // --------------------------------------------------

        val outroUsuarioId =
            intent.getStringExtra("outroUsuarioId")

        if (outroUsuarioId == null) {

            Toast.makeText(
                this,
                "Erro: não foi informado com quem conversar.",
                Toast.LENGTH_LONG
            ).show()

            finish()
            return
        }

        val chatIdExistente = intent.getStringExtra("chatId")

        // Dados da solicitação aprovada (vêm do botão de mensagem do responsável)
        val solicitacaoId = intent.getStringExtra("solicitacaoId")
        val mensagemInicial = intent.getStringExtra("mensagemInicial")
        val mensagemInicialEm =
            intent.getLongExtra("mensagemInicialEm", 0L).takeIf { it > 0L }
        val rotaId = intent.getStringExtra("rotaId").orEmpty()
        val passageiroId = intent.getStringExtra("passageiroId").orEmpty()
        val passageiroNome = intent.getStringExtra("passageiroNome").orEmpty()
        val rotaNome = intent.getStringExtra("rotaNome").orEmpty()

        // Só há contexto se soubermos a viagem E o aluno
        val contexto =
            if (rotaId.isNotBlank() && passageiroId.isNotBlank()) {
                ChatRepository.ContextoChat(
                    rotaId = rotaId,
                    passageiroId = passageiroId,
                    passageiroNome = passageiroNome,
                    rotaNome = rotaNome
                )
            } else {
                null
            }

        // --------------------------------------------------
        // NOME NO TOPO (contato + aluno, quando houver)
        // --------------------------------------------------

        val nomeContato =
            intent.getStringExtra("nomeContato")

        txtNomeContato.text =
            when {
                nomeContato.isNullOrBlank() -> "Conversa"
                passageiroNome.isNotBlank() -> "$nomeContato · $passageiroNome"
                else -> nomeContato
            }

        // --------------------------------------------------
        // FOTO DO CONTATO
        // --------------------------------------------------

        lifecycleScope.launch {
            val usuario = try {
                usuarioRepository.consultarUsuario(outroUsuarioId)
            } catch (e: Exception) {
                null
            }

            Glide.with(imgFotoContato)
                .load(usuario?.fotoUrl)
                .placeholder(R.drawable.ic_perfil)
                .error(R.drawable.ic_perfil)
                .circleCrop()
                .into(imgFotoContato)
        }

        // --------------------------------------------------
        // CARREGAR CHAT
        // --------------------------------------------------

        lifecycleScope.launch {

            try {

                val id =
                    chatIdExistente
                        ?: ChatRepository.iniciarOuObterChat(
                            outroUsuarioId = outroUsuarioId,
                            solicitacaoId = solicitacaoId,
                            mensagemInicial = mensagemInicial,
                            mensagemInicialEm = mensagemInicialEm,
                            contexto = contexto
                        )

                chatId = id

                listenerMensagens =
                    ChatRepository.escutarMensagens(
                        id,
                        aoErro = { erro ->
                            runOnUiThread {
                                Toast.makeText(
                                    this@ChatActivity,
                                    "Não foi possível ler as mensagens: ${erro.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    ) { mensagens ->

                        adapter.atualizarMensagens(mensagens)

                        if (mensagens.isNotEmpty()) {

                            recyclerMensagens.post {

                                recyclerMensagens.scrollToPosition(
                                    mensagens.size - 1
                                )
                            }
                        }
                    }

            } catch (e: Exception) {

                Log.e(
                    "ChatActivity",
                    "Erro ao iniciar chat",
                    e
                )

                Toast.makeText(
                    this@ChatActivity,
                    "Erro ao abrir a conversa: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        // --------------------------------------------------
        // ENVIAR MENSAGEM
        // --------------------------------------------------

        btnEnviar.setOnClickListener {

            val texto =
                edtMensagem.text
                    .toString()
                    .trim()

            if (texto.isEmpty()) {
                return@setOnClickListener
            }

            val idAtual = chatId

            if (idAtual == null) {

                Toast.makeText(
                    this,
                    "A conversa ainda está carregando, aguarde.",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            lifecycleScope.launch {

                try {

                    ChatRepository.enviarMensagem(
                        idAtual,
                        texto
                    )

                    edtMensagem.setText("")

                    recyclerMensagens.post {

                        if (adapter.itemCount > 0) {

                            recyclerMensagens.scrollToPosition(
                                adapter.itemCount - 1
                            )
                        }
                    }

                } catch (e: Exception) {

                    Log.e(
                        "ChatActivity",
                        "Erro ao enviar mensagem",
                        e
                    )

                    Toast.makeText(
                        this@ChatActivity,
                        "Erro ao enviar mensagem: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        // --------------------------------------------------
        // FOCO NO CAMPO
        // --------------------------------------------------

        edtMensagem.setOnFocusChangeListener { _, temFoco ->

            if (temFoco) {

                recyclerMensagens.postDelayed({

                    if (adapter.itemCount > 0) {

                        recyclerMensagens.scrollToPosition(
                            adapter.itemCount - 1
                        )
                    }

                }, 250)
            }
        }
    }

    // --------------------------------------------------
    // DESTRUIR ACTIVITY
    // --------------------------------------------------

    override fun onDestroy() {

        listenerMensagens?.remove()

        super.onDestroy()
    }
}