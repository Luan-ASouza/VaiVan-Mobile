package com.example.vaivan.ui.motorista.rotas.navegacao

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.vaivan.R
import com.example.vaivan.data.local.entities.ParadaViagemEntity
import com.example.vaivan.data.repository.ViagemRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView

/**
 * Lista de passageiros de uma mesma parada, dentro do painel de embarque/
 * desembarque. Sempre recebe o estado atual (via [submitLista]) — nunca
 * guarda uma cópia própria desatualizada.
 */
class PassageiroParadaAdapter(
    private val onConfirmar: (ParadaViagemEntity) -> Unit,
    private val onNaoEmbarcou: (ParadaViagemEntity) -> Unit
) : RecyclerView.Adapter<PassageiroParadaAdapter.ParadaViewHolder>() {

    private var itens: List<ParadaViagemEntity> = emptyList()

    fun submitLista(novaLista: List<ParadaViagemEntity>) {
        itens = novaLista
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParadaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_passageiro_parada_navegacao, parent, false)
        return ParadaViewHolder(view)
    }

    override fun onBindViewHolder(holder: ParadaViewHolder, position: Int) {
        holder.bind(itens[position])
    }

    override fun getItemCount(): Int = itens.size

    inner class ParadaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val foto = itemView.findViewById<ShapeableImageView>(R.id.fotoProgressoPassageiro)
        private val nome = itemView.findViewById<TextView>(R.id.nomePassageiroItemParada)
        private val status = itemView.findViewById<TextView>(R.id.statusPassageiroItemParada)
        private val btnConfirmar = itemView.findViewById<MaterialButton>(R.id.btnConfirmarAcaoItemParada)
        private val btnNao = itemView.findViewById<MaterialButton>(R.id.btnNaoAcaoItemParada)

        fun bind(parada: ParadaViagemEntity) {
            nome.text = parada.nomePassageiro

            Glide.with(foto)
                .load(parada.fotoPassageiroUrl)
                .placeholder(R.drawable.ic_perfil)
                .error(R.drawable.ic_perfil)
                .circleCrop()
                .into(foto)

            val resolvido = parada.status != ViagemRepository.PARADA_AGUARDANDO &&
                parada.status != ViagemRepository.PARADA_AGUARDANDO_DESEMBARQUE

            status.text = textoStatus(parada)
            btnConfirmar.isEnabled = !resolvido
            btnNao.isEnabled = !resolvido

            if (parada.tipo == ViagemRepository.TIPO_EMBARQUE) {
                btnConfirmar.text = "Confirmar embarque"
                btnNao.text = "Não embarcou"
                btnNao.visibility = View.VISIBLE

                btnConfirmar.setOnClickListener { onConfirmar(parada) }
                btnNao.setOnClickListener { onNaoEmbarcou(parada) }
            } else {
                btnConfirmar.text = "Confirmar desembarque"
                btnNao.visibility = View.GONE

                btnConfirmar.setOnClickListener { onConfirmar(parada) }
            }
        }

        private fun textoStatus(parada: ParadaViagemEntity): String {
            val falta = parada.motivoAusencia != null &&
                (parada.status == ViagemRepository.PARADA_NAO_EMBARCOU ||
                    parada.status == ViagemRepository.PARADA_DESEMBARCADO)
            if (falta) return "Falta Avisada"

            return when (parada.status) {
                ViagemRepository.PARADA_EMBARCADO -> "Embarcado"
                ViagemRepository.PARADA_NAO_EMBARCOU -> "Não embarcou"
                ViagemRepository.PARADA_DESEMBARCADO -> "Desembarcado"
                else -> "Aguardando"
            }
        }
    }
}
