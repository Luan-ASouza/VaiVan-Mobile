package com.example.vaivan.ui.comum.chat

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.vaivan.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Lista das conversas REAIS do usuário logado (serve para motorista e responsável).
 * Cada conversa é um documento em "chats" que tem o uid do usuário em "participantes".
 * Há uma conversa por viagem e por aluno; o cartão mostra de qual aluno e rota ela é.
 */
open class ListaConversasFragment : Fragment() {

    private data class Conversa(
        val chatId: String,
        val outroUsuarioId: String,
        val ultimaMensagem: String,
        val enviadaEm: Long,
        val enviadaPor: String,
        val passageiroNome: String,
        val rotaNome: String
    )

    private lateinit var containerConversas: LinearLayout
    private lateinit var txtSemConversas: TextView
    private lateinit var progressConversas: ProgressBar

    private var listener: ListenerRegistration? = null
    private val nomesEmCache = mutableMapOf<String, String>()

    private val firestore by lazy { FirebaseFirestore.getInstance() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_lista_conversas, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        containerConversas = view.findViewById(R.id.containerConversas)
        txtSemConversas = view.findViewById(R.id.txtSemConversas)
        progressConversas = view.findViewById(R.id.progressConversas)

        escutarConversas()
    }

    override fun onDestroyView() {
        listener?.remove()
        listener = null
        super.onDestroyView()
    }

    // ---------------------------------------------------------
    // BUSCAR CONVERSAS
    // ---------------------------------------------------------

    private fun escutarConversas() {

        val meuUid = FirebaseAuth.getInstance().currentUser?.uid

        if (meuUid == null) {
            progressConversas.visibility = View.GONE
            txtSemConversas.text = "Sessão expirada. Faça login novamente."
            txtSemConversas.visibility = View.VISIBLE
            return
        }

        // Sem orderBy de propósito: ordenar aqui no app evita pedir índice ao Firestore
        listener = firestore.collection("chats")
            .whereArrayContains("participantes", meuUid)
            .addSnapshotListener { snapshot, erro ->

                if (!isAdded || view == null) return@addSnapshotListener

                progressConversas.visibility = View.GONE

                if (erro != null || snapshot == null) {
                    containerConversas.removeAllViews()
                    txtSemConversas.text =
                        "Não foi possível carregar as conversas.\n${erro?.message.orEmpty()}"
                    txtSemConversas.visibility = View.VISIBLE
                    return@addSnapshotListener
                }

                val conversas = snapshot.documents
                    .mapNotNull { documento ->

                        val participantes =
                            (documento.get("participantes") as? List<*>)
                                ?.filterIsInstance<String>()
                                ?: return@mapNotNull null

                        val outro = participantes.firstOrNull { it != meuUid }
                            ?: return@mapNotNull null

                        Conversa(
                            chatId = documento.id,
                            outroUsuarioId = outro,
                            ultimaMensagem = documento.getString("ultimaMensagem").orEmpty(),
                            enviadaEm = documento.getLong("ultimaMensagemEm") ?: 0L,
                            enviadaPor = documento.getString("ultimaMensagemPor").orEmpty(),
                            passageiroNome = documento.getString("passageiroNome").orEmpty(),
                            rotaNome = documento.getString("rotaNome").orEmpty()
                        )
                    }
                    .sortedByDescending { it.enviadaEm }

                mostrarConversas(conversas, meuUid)
            }
    }

    // ---------------------------------------------------------
    // MOSTRAR
    // ---------------------------------------------------------

    private fun mostrarConversas(conversas: List<Conversa>, meuUid: String) {

        containerConversas.removeAllViews()

        if (conversas.isEmpty()) {
            txtSemConversas.text =
                "Nenhuma conversa ainda.\nElas aparecem aqui depois que uma solicitação de vaga for aprovada."
            txtSemConversas.visibility = View.VISIBLE
            return
        }

        txtSemConversas.visibility = View.GONE

        conversas.forEach { conversa ->
            containerConversas.addView(criarCard(conversa, meuUid))
        }
    }

    private fun criarCard(conversa: Conversa, meuUid: String): View {

        val card = layoutInflater.inflate(R.layout.item_conversa, containerConversas, false)

        val txtNome = card.findViewById<TextView>(R.id.txtNomeConversa)
        val txtInicial = card.findViewById<TextView>(R.id.txtInicialConversa)
        val txtContexto = card.findViewById<TextView>(R.id.txtContextoConversa)

        // De qual aluno e de qual rota é esta conversa
        val contexto = listOf(
            conversa.passageiroNome.takeIf { it.isNotBlank() }?.let { "Aluno: $it" },
            conversa.rotaNome.takeIf { it.isNotBlank() }
        ).filterNotNull().joinToString(" · ")

        if (contexto.isBlank()) {
            txtContexto.visibility = View.GONE
        } else {
            txtContexto.text = contexto
            txtContexto.visibility = View.VISIBLE
        }

        card.findViewById<TextView>(R.id.txtUltimaMensagemConversa).text =
            when {
                conversa.ultimaMensagem.isBlank() -> "Nenhuma mensagem ainda"
                conversa.enviadaPor == meuUid -> "Você: ${conversa.ultimaMensagem}"
                else -> conversa.ultimaMensagem
            }

        card.findViewById<TextView>(R.id.txtHoraConversa).text =
            formatarMomento(conversa.enviadaEm)

        fun aplicarNome(nome: String) {
            txtNome.text = nome
            txtInicial.text = nome.firstOrNull()?.uppercase() ?: "?"
        }

        val nomeGuardado = nomesEmCache[conversa.outroUsuarioId]

        if (nomeGuardado != null) {
            aplicarNome(nomeGuardado)
        } else {
            txtNome.text = "Carregando..."

            viewLifecycleOwner.lifecycleScope.launch {
                val nome = buscarNome(conversa.outroUsuarioId)
                nomesEmCache[conversa.outroUsuarioId] = nome
                aplicarNome(nome)
            }
        }

        card.setOnClickListener {
            val intent = Intent(requireContext(), ChatActivity::class.java)
            intent.putExtra("chatId", conversa.chatId)
            intent.putExtra("outroUsuarioId", conversa.outroUsuarioId)
            intent.putExtra("nomeContato", nomesEmCache[conversa.outroUsuarioId] ?: "Conversa")
            intent.putExtra("passageiroNome", conversa.passageiroNome)
            startActivity(intent)
        }

        return card
    }

    private suspend fun buscarNome(usuarioId: String): String =
        try {
            firestore.collection("usuarios")
                .document(usuarioId)
                .get()
                .await()
                .getString("nome")
                ?: "Usuário"
        } catch (e: Exception) {
            "Usuário"
        }

    /** Hoje mostra a hora; em outros dias, a data. */
    private fun formatarMomento(momento: Long): String {

        if (momento <= 0L) return ""

        val agora = Calendar.getInstance()
        val mensagem = Calendar.getInstance().apply { timeInMillis = momento }

        val ehHoje =
            agora.get(Calendar.YEAR) == mensagem.get(Calendar.YEAR) &&
                    agora.get(Calendar.DAY_OF_YEAR) == mensagem.get(Calendar.DAY_OF_YEAR)

        val formato = if (ehHoje) "HH:mm" else "dd/MM"

        return SimpleDateFormat(formato, Locale("pt", "BR")).format(Date(momento))
    }
}