package com.example.vaivan.ui.comum.foto

import android.widget.ImageView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.vaivan.R
import com.example.vaivan.data.repository.PassageiroRepository
import kotlinx.coroutines.launch

/**
 * Foto circular de um passageiro em qualquer tela que o cite (mensalidades,
 * pagamentos, histórico, alunos da rota). O registro vem do cache do Room
 * ([PassageiroRepository.consultarPassageiroComCache]) e a imagem do cache
 * do Glide — abrir a mesma tela de novo não baixa nada outra vez.
 * Sem foto (ou sem id), mostra o avatar padrão.
 */
fun carregarFotoPassageiro(
    dono: LifecycleOwner,
    imagem: ImageView,
    passageiroId: String,
    repository: PassageiroRepository
) {
    if (passageiroId.isBlank()) {
        imagem.setImageResource(R.drawable.avatar_01)
        return
    }

    dono.lifecycleScope.launch {
        val passageiro = repository.consultarPassageiroComCache(passageiroId)

        Glide.with(imagem)
            .load(passageiro?.fotoUrl)
            .placeholder(R.drawable.avatar_01)
            .error(R.drawable.avatar_01)
            .circleCrop()
            .into(imagem)
    }
}
