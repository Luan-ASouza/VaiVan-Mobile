package com.example.vaivan.ui.comum.locais

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.vaivan.R
import com.example.vaivan.core.util.TextoUtil
import com.example.vaivan.data.local.entities.LocalEntity

class LocaisSalvosAdapter(
    private val aoEditar: (LocalEntity) -> Unit
) : ListAdapter<LocalEntity, LocaisSalvosAdapter.ViewHolder>(Diff) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgIcone: ImageView = view.findViewById(R.id.imgIconeLocal)
        val txtNome: TextView = view.findViewById(R.id.txtNomeLocalSalvo)
        val txtEndereco: TextView = view.findViewById(R.id.txtEnderecoLocalSalvo)
        val btnEditar: View = view.findViewById(R.id.btnEditarLocalSalvo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_local_salvo, parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val local = getItem(position)

        holder.txtNome.text = local.nome
        holder.txtEndereco.text = local.endereco
        holder.imgIcone.setImageResource(
            if (TextoUtil.normalizar(local.nome).contains("casa")) R.drawable.ic_casa
            else R.drawable.ic_local
        )

        // A linha inteira e o lápis levam pra edição.
        holder.itemView.setOnClickListener { aoEditar(local) }
        holder.btnEditar.setOnClickListener { aoEditar(local) }
    }

    private object Diff : DiffUtil.ItemCallback<LocalEntity>() {
        override fun areItemsTheSame(a: LocalEntity, b: LocalEntity) = a.id == b.id
        override fun areContentsTheSame(a: LocalEntity, b: LocalEntity) =
            a.copy(lastUpdated = 0) == b.copy(lastUpdated = 0)
    }
}
