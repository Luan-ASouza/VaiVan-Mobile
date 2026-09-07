package com.example.trabalhograua.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.trabalhograua.R
import com.google.firebase.auth.FirebaseAuth

class MensagemAdapter(
    private val mensagens: MutableList<Mensagem> = mutableListOf()
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TIPO_ENVIADA = 1
        private const val TIPO_RECEBIDA = 2
    }

    // Guarda as mensagens novas e atualiza a lista na tela
    fun atualizarMensagens(novaLista: List<Mensagem>) {
        mensagens.clear()
        mensagens.addAll(novaLista)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        val meuUid = FirebaseAuth.getInstance().currentUser?.uid
        return if (mensagens[position].remetenteId == meuUid) {
            TIPO_ENVIADA
        } else {
            TIPO_RECEBIDA
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TIPO_ENVIADA) {
            val view = inflater.inflate(R.layout.item_mensagem_enviada, parent, false)
            MensagemViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_mensagem_recebida, parent, false)
            MensagemViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val mensagem = mensagens[position]
        (holder as MensagemViewHolder).bind(mensagem)
    }

    override fun getItemCount(): Int = mensagens.size

    class MensagemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txtMensagem: TextView = itemView.findViewById(R.id.txtMensagem)

        fun bind(mensagem: Mensagem) {
            txtMensagem.text = mensagem.texto
        }
    }
}