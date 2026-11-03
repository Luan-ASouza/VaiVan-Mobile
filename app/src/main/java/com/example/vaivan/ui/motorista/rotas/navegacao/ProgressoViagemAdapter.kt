package com.example.vaivan.ui.motorista.rotas.navegacao

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.vaivan.R
import com.example.vaivan.data.local.entities.ParadaViagemEntity
import com.example.vaivan.data.repository.ViagemRepository
import com.google.android.material.imageview.ShapeableImageView

/**
 * Uma linha exibida no Bottom Sheet de progresso: um passageiro, com UM status
 * só que evolui durante a viagem (Aguardando -> Na van -> Desceu, ou Não
 * embarcou) — nunca duas linhas pro mesmo passageiro, embarque e desembarque
 * combinados.
 */
internal data class LinhaProgresso(
    val ordemEmbarque: Int,
    val ordemDesembarque: Int,
    val titulo: String,
    val subtitulo: String,
    val fotoUrl: String?,
    val status: String,
    val corStatus: Int
)

/**
 * Lista completa (somente leitura) de todos os passageiros da viagem, usada
 * no Bottom Sheet arrastável — diferente do card de chegada, aqui não há
 * ações, só o acompanhamento visual do progresso.
 */
class ProgressoViagemAdapter : RecyclerView.Adapter<ProgressoViagemAdapter.ProgressoViewHolder>() {

    private var itens: List<LinhaProgresso> = emptyList()

    fun submitLista(novaLista: List<ParadaViagemEntity>) {
        itens = novaLista
            .groupBy { it.passageiroId }
            .mapNotNull { (_, grupo) ->
                val embarque = grupo.firstOrNull { it.tipo == ViagemRepository.TIPO_EMBARQUE } ?: return@mapNotNull null
                val desembarque = grupo.firstOrNull { it.tipo == ViagemRepository.TIPO_DESEMBARQUE } ?: return@mapNotNull null

                val (texto, cor) = when {
                    desembarque.status == ViagemRepository.PARADA_DESEMBARCADO ->
                        "Desceu" to R.color.status_success
                    embarque.status == ViagemRepository.PARADA_NAO_EMBARCOU ->
                        "Não embarcou" to R.color.status_danger
                    embarque.status == ViagemRepository.PARADA_EMBARCADO ->
                        "Na van" to R.color.status_success
                    else -> "Aguardando" to R.color.light_gray
                }

                // Mostra o endereço do destino ainda pendente; quando os dois
                // já foram resolvidos, mostra o do desembarque (o final).
                val pendente = if (embarque.status == ViagemRepository.PARADA_AGUARDANDO) embarque else desembarque

                LinhaProgresso(
                    ordemEmbarque = embarque.ordem,
                    ordemDesembarque = desembarque.ordem,
                    titulo = embarque.nomePassageiro,
                    subtitulo = pendente.endereco.ifBlank { pendente.nomeLocal },
                    fotoUrl = embarque.fotoPassageiroUrl,
                    status = texto,
                    corStatus = cor
                )
            }
            .sortedWith(compareBy({ it.ordemEmbarque }, { it.ordemDesembarque }))

        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProgressoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_progresso_passageiro, parent, false)
        return ProgressoViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProgressoViewHolder, position: Int) {
        holder.bind(itens[position])
    }

    override fun getItemCount(): Int = itens.size

    class ProgressoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val foto = itemView.findViewById<ShapeableImageView>(R.id.fotoProgressoPassageiro)
        private val nome = itemView.findViewById<TextView>(R.id.nomeProgressoPassageiro)
        private val endereco = itemView.findViewById<TextView>(R.id.enderecoProgressoPassageiro)
        private val status = itemView.findViewById<TextView>(R.id.statusProgressoPassageiro)

        internal fun bind(linha: LinhaProgresso) {
            nome.text = linha.titulo
            endereco.text = linha.subtitulo

            status.text = linha.status
            status.setTextColor(ContextCompat.getColor(itemView.context, linha.corStatus))

            Glide.with(itemView)
                .load(linha.fotoUrl)
                .placeholder(R.drawable.ic_perfil)
                .error(R.drawable.ic_perfil)
                .circleCrop()
                .into(foto)
        }
    }
}
