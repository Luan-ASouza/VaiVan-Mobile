package com.example.trabalhograua.chat

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.trabalhograua.R
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private lateinit var recyclerMensagens: RecyclerView
    private lateinit var edtMensagem: EditText
    private lateinit var btnEnviar: Button
    private lateinit var txtNomeContato: TextView

    private val adapter = MensagemAdapter()
    private var listenerMensagens: ListenerRegistration? = null

    private var chatId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        // Conecta as variáveis com os elementos do layout
        recyclerMensagens = findViewById(R.id.recyclerMensagens)
        edtMensagem = findViewById(R.id.edtMensagem)
        btnEnviar = findViewById(R.id.btnEnviar)
        txtNomeContato = findViewById(R.id.txtNomeContato)

        // Configura a lista (RecyclerView)
        recyclerMensagens.layoutManager = LinearLayoutManager(this)
        recyclerMensagens.adapter = adapter

        // Pega o UID de quem você está conversando, que a tela anterior precisa enviar
        val outroUsuarioId = intent.getStringExtra("outroUsuarioId")

        if (outroUsuarioId == null) {
            Toast.makeText(this, "Erro: não foi informado com quem conversar.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Opcional: se a tela anterior mandar o nome, mostra na barra de topo
        val nomeContato = intent.getStringExtra("nomeContato")
        txtNomeContato.text = nomeContato ?: "Conversa"

        // Cria (ou abre) a conversa e começa a escutar mensagens
        lifecycleScope.launch {
            try {
                val id = ChatRepository.iniciarOuObterChat(outroUsuarioId)
                chatId = id

                listenerMensagens = ChatRepository.escutarMensagens(id) { mensagens ->
                    adapter.atualizarMensagens(mensagens)
                    if (mensagens.isNotEmpty()) {
                        recyclerMensagens.scrollToPosition(mensagens.size - 1)
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatActivity", "Erro ao iniciar chat", e)
                Toast.makeText(this@ChatActivity, "Erro ao abrir a conversa.", Toast.LENGTH_LONG).show()
            }
        }

        // Botão de enviar
        btnEnviar.setOnClickListener {
            val texto = edtMensagem.text.toString().trim()

            if (texto.isEmpty()) {
                return@setOnClickListener
            }

            val idAtual = chatId
            if (idAtual == null) {
                Toast.makeText(this, "A conversa ainda está carregando, aguarde.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    ChatRepository.enviarMensagem(idAtual, texto)
                    edtMensagem.setText("") // limpa o campo depois de enviar
                } catch (e: Exception) {
                    Log.e("ChatActivity", "Erro ao enviar mensagem", e)
                    Toast.makeText(this@ChatActivity, "Erro ao enviar mensagem.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Muito importante: remove o "ouvinte" quando a tela fecha,
    // pra não continuar consumindo dados/bateria em segundo plano
    override fun onDestroy() {
        super.onDestroy()
        listenerMensagens?.remove()
    }
}