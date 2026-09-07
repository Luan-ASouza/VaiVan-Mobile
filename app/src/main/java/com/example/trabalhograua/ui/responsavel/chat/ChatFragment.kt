package com.example.trabalhograua.ui.responsavel.chat

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.trabalhograua.R
import com.example.trabalhograua.chat.ChatActivity

class ChatFragment : Fragment() {

    // UID do motorista de teste (Firebase Authentication)
    private val UID_MOTORISTA_TESTE = "kjqVvTT9n4NSfoeQiqJjUxgAhjJ3"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Card "Douglas Silva" agora abre a conversa de verdade
        val cardConversa1 = view.findViewById<View>(R.id.r24dtccizf3f)
        cardConversa1.setOnClickListener {
            val intent = Intent(requireContext(), ChatActivity::class.java)
            intent.putExtra("outroUsuarioId", UID_MOTORISTA_TESTE)
            intent.putExtra("nomeContato", "Motorista")
            startActivity(intent)
        }
    }
}