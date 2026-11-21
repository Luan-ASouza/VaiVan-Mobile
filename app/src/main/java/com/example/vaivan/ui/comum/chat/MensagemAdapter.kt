package com.example.vaivan.ui.comum.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.vaivan.R
import com.example.vaivan.core.util.DataUtil
import com.google.firebase.auth.FirebaseAuth

/**
 * Mensagens da conversa com um separador ("Hoje", "Ontem", data) sempre que
 * o dia muda. Nas recebidas, a foto do contato só aparece na primeira de uma
 * sequência do mesmo remetente no mesmo dia.
 */
class MensagemAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class Item {
        data class Separador(val rotulo: String) : Item()
        data class DaConversa(val mensagem: Mensagem, val mostrarFoto: Boolean) : Item()
    }

    companion object {
        private const val TIPO_ENVIADA = 1
        private const val TIPO_RECEBIDA = 2
        private const val TIPO_SEPARADOR = 3
    }

    private val itens = mutableListOf<Item>()
    private val meuUid: String? get() = FirebaseAuth.getInstance().currentUser?.uid

    /** Fundo da bolha enviada (TemaPerfil.bolhaEnviada). Definir antes da
     * primeira mensagem: é aplicado quando a linha é criada. */
    var bolhaEnviada: Int = R.drawable.bg_bolha_enviada

    /** Foto do contato nas mensagens recebidas (null = ícone padrão). */
    var fotoContato: String? = null
        set(valor) {
            if (field == valor) return
            field = valor
            notifyDataSetChanged()
        }

    fun atualizarMensagens(novaLista: List<Mensagem>) {
        itens.clear()
        var anterior: Mensagem? = null

        for (mensagem in novaLista) {
            val novoDia = anterior == null || !DataUtil.mesmoDia(anterior.enviadoEm, mensagem.enviadoEm)
            if (novoDia && mensagem.enviadoEm > 0L) {
                itens.add(Item.Separador(DataUtil.rotuloDiaChat(mensagem.enviadoEm)))
            }
            val mostrarFoto = novoDia || anterior?.remetenteId != mensagem.remetenteId
            itens.add(Item.DaConversa(mensagem, mostrarFoto))
            anterior = mensagem
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int =
        when (val item = itens[position]) {
            is Item.Separador -> TIPO_SEPARADOR
            is Item.DaConversa ->
                if (item.mensagem.remetenteId == meuUid) TIPO_ENVIADA else TIPO_RECEBIDA
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TIPO_SEPARADOR -> SeparadorViewHolder(
                inflater.inflate(R.layout.item_separador_data_chat, parent, false)
            )
            TIPO_ENVIADA -> MensagemViewHolder(
                inflater.inflate(R.layout.item_mensagem_enviada, parent, false).apply {
                    findViewById<View>(R.id.bolhaMensagem).setBackgroundResource(bolhaEnviada)
                }
            )
            else -> MensagemViewHolder(
                inflater.inflate(R.layout.item_mensagem_recebida, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = itens[position]) {
            is Item.Separador -> (holder as SeparadorViewHolder).bind(item.rotulo)
            is Item.DaConversa -> (holder as MensagemViewHolder).bind(item.mensagem, item.mostrarFoto, fotoContato)
        }
    }

    override fun getItemCount(): Int = itens.size

    private class SeparadorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txtData: TextView = itemView.findViewById(R.id.txtDataSeparador)

        fun bind(rotulo: String) {
            txtData.text = rotulo
        }
    }

    private class MensagemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txtMensagem: TextView = itemView.findViewById(R.id.txtMensagem)
        private val txtHora: TextView = itemView.findViewById(R.id.txtHoraMensagem)
        private val imgFoto: ImageView? = itemView.findViewById(R.id.imgFotoMensagem)

        fun bind(mensagem: Mensagem, mostrarFoto: Boolean, fotoContato: String?) {
            txtMensagem.text = mensagem.texto
            txtHora.text = if (mensagem.enviadoEm > 0L) DataUtil.hora(mensagem.enviadoEm) else ""

            // Só existe nas recebidas; INVISIBLE mantém o alinhamento das bolhas
            imgFoto?.let { foto ->
                foto.visibility = if (mostrarFoto) View.VISIBLE else View.INVISIBLE
                if (mostrarFoto) {
                    Glide.with(foto)
                        .load(fotoContato)
                        .placeholder(R.drawable.ic_perfil)
                        .error(R.drawable.ic_perfil)
                        .circleCrop()
                        .into(foto)
                }
            }
        }
    }
}
