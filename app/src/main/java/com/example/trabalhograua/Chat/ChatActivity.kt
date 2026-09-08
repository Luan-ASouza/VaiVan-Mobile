package com.example.trabalhograua.chat

import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.trabalhograua.R
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private lateinit var activityChat: LinearLayout
    private lateinit var recyclerMensagens: RecyclerView
    private lateinit var edtMensagem: EditText
    private lateinit var btnEnviar: ImageButton
    private lateinit var txtNomeContato: TextView
    private lateinit var barraMensagem: ConstraintLayout

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
        barraMensagem = findViewById(R.id.barraMensagem)

        // --------------------------------------------------
        // WINDOW INSETS
        // --------------------------------------------------

        ViewCompat.setOnApplyWindowInsetsListener(activityChat) { view, insets ->

            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            val ime = insets.getInsets(
                WindowInsetsCompat.Type.ime()
            )

            val tecladoAberto = insets.isVisible(
                WindowInsetsCompat.Type.ime()
            )

            /*
             * Quando o teclado estiver aberto:
             *
             *     ime.bottom = altura do teclado
             *
             * Quando estiver fechado:
             *
             *     systemBars.bottom = barra de navegação
             *
             * Usamos o maior valor para garantir que
             * a barra de mensagem nunca fique escondida.
             */

            val bottomInset = if (tecladoAberto) {
                ime.bottom
            } else {
                systemBars.bottom
            }

            /*
             * Mantém a barra superior abaixo da status bar.
             */
            view.setPadding(
                view.paddingLeft,
                systemBars.top,
                view.paddingRight,
                0
            )

            /*
             * AQUI está o principal:
             *
             * adicionamos o espaço do teclado na parte
             * inferior da Activity.
             *
             * Como a barra de mensagem está no final
             * do LinearLayout, ela sobe junto.
             */
            view.setPadding(
                view.paddingLeft,
                systemBars.top,
                view.paddingRight,
                bottomInset
            )

            insets
        }

        // --------------------------------------------------
        // RECYCLERVIEW
        // --------------------------------------------------

        recyclerMensagens.layoutManager =
            LinearLayoutManager(this)

        recyclerMensagens.adapter = adapter

        // --------------------------------------------------
        // PEGAR USUÁRIO
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

        // --------------------------------------------------
        // NOME DO CONTATO
        // --------------------------------------------------

        val nomeContato =
            intent.getStringExtra("nomeContato")

        txtNomeContato.text =
            nomeContato ?: "Conversa"

        // --------------------------------------------------
        // CARREGAR CHAT
        // --------------------------------------------------

        lifecycleScope.launch {

            try {

                val id =
                    ChatRepository.iniciarOuObterChat(
                        outroUsuarioId
                    )

                chatId = id

                listenerMensagens =
                    ChatRepository.escutarMensagens(id) { mensagens ->

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
                    "Erro ao abrir a conversa.",
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
                        "Erro ao enviar mensagem.",
                        Toast.LENGTH_SHORT
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