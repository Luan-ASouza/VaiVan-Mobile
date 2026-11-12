package com.example.vaivan.core.notificacao

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Recebe os pushes do Firebase. O servidor envia só "dados" (titulo, texto, categoria...),
 * e é este serviço que desenha a notificação, para ela ter sempre o visual do VaiVan,
 * com o app aberto ou fechado.
 */
class VaiVanMessagingService : FirebaseMessagingService() {

    /** O Firebase trocou o token do aparelho: registra o novo. */
    override fun onNewToken(token: String) {
        super.onNewToken(token)

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                TokenDispositivo.gravar(uid, token)
            } catch (e: Exception) {
                Log.w(TAG, "Não foi possível gravar o novo token", e)
            }
        }
    }

    override fun onMessageReceived(mensagem: RemoteMessage) {
        super.onMessageReceived(mensagem)

        val dados = mensagem.data

        // Viagem encerrada: só some com o cartão fixo, sem notificação nova.
        if (dados["acao"] == "encerrar") {
            dados["grupo"]?.let { NotificadorVaiVan.encerrar(this, it) }
            return
        }

        // Quem está com a conversa aberta já vê a mensagem na tela.
        val chatId = dados["chatId"]
        if (chatId != null && chatId == chatAberto) return

        // Aceita também o "envio de teste" do Console (que traz title/body em notification).
        val titulo = dados["titulo"] ?: mensagem.notification?.title ?: return
        val texto = dados["texto"] ?: mensagem.notification?.body ?: ""

        NotificadorVaiVan.exibir(
            context = this,
            titulo = titulo,
            texto = texto,
            categoria = dados["categoria"],
            grupo = dados["grupo"],
            dados = dados
        )
    }

    companion object {
        private const val TAG = "VaiVanMessaging"

        /** Conversa visível agora (definida pela tela do chat em onResume/onPause). */
        @Volatile
        var chatAberto: String? = null
    }
}