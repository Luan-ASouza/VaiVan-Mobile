package com.example.vaivan.ui.motorista.rotas.navegacao

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.vaivan.R
import com.example.vaivan.data.local.entities.ParadaViagemEntity
import com.example.vaivan.data.repository.ViagemRepository
import com.google.android.material.button.MaterialButton

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

        private val nome = itemView.findViewById<TextView>(R.id.nomePassageiroItemParada)
        private val status = itemView.findViewById<TextView>(R.id.statusPassageiroItemParada)
        private val btnConfirmar = itemView.findViewById<MaterialButton>(R.id.btnConfirmarAcaoItemParada)
        private val btnNao = itemView.findViewById<MaterialButton>(R.id.btnNaoAcaoItemParada)

        fun bind(parada: ParadaViagemEntity) {
            nome.text = parada.nomePassageiro

            val resolvido = parada.status != ViagemRepository.PARADA_AGUARDANDO &&
                parada.status != ViagemRepository.PARADA_AGUARDANDO_DESEMBARQUE

            status.text = textoStatus(parada.status)
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

        private fun textoStatus(valor: String): String = when (valor) {
            ViagemRepository.PARADA_EMBARCADO -> "Embarcado"
            ViagemRepository.PARADA_NAO_EMBARCOU -> "Não embarcou"
            ViagemRepository.PARADA_DESEMBARCADO -> "Desembarcado"
            else -> "Aguardando"
        }
    }
}
