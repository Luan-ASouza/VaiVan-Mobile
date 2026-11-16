package com.example.vaivan.ui.comum.guia

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.view.View
import androidx.annotation.IdRes
import com.example.vaivan.R
import com.example.vaivan.ui.ajuda.PerfilAjuda
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetSequence
import com.google.firebase.auth.FirebaseAuth

/**
 * Guia de primeiro acesso (onboarding) das telas iniciais.
 *
 * Na primeira vez que o usuário abre a tela inicial, destaca os botões principais,
 * um de cada vez, com uma explicação curta. Usa a biblioteca TapTargetView
 * (padrão de "descoberta de funcionalidades" do Material Design; Apache 2.0).
 *
 * O "já viu" fica no SharedPreferences do próprio celular, com o id do usuário e o
 * perfil na chave. Não usa internet nem o Firestore. Se duas contas usam o mesmo
 * celular, cada uma vê o guia uma vez.
 *
 * Para ver de novo: menu Ajuda > "Rever o tour do app" (chama [mostrarDeNovo]).
 */
object GuiaPrimeiroAcesso {

    /** Um passo do guia: qual botão destacar e o que dizer sobre ele. */
    private data class Passo(
        @IdRes val viewId: Int,
        val titulo: String,
        val descricao: String
    )

    // =========================================================
    // ROTEIROS (os textos ficam todos aqui)
    // =========================================================

    private const val COMO_USAR = "\n\nToque no destaque para continuar ou fora dele para pular."

    private val passosResponsavel = listOf(
        Passo(
            R.id.navPassageiros,
            "Seus filhos",
            "Cadastre seu filho e o local de embarque. É aqui também que você busca uma van para ele." + COMO_USAR
        ),
        Passo(
            R.id.navRotas,
            "Transportes",
            "Acompanhe as vans do seu filho, os pedidos de vaga e a viagem em tempo real."
        ),
        Passo(
            R.id.navAssinaturas,
            "Conversas",
            "Fale com o motorista direto pelo app."
        ),
        Passo(
            R.id.btnMenu,
            "Menu",
            "Aqui ficam as mensalidades, os locais salvos, a ajuda e as configurações da sua conta."
        )
    )

    private val passosMotorista = listOf(
        Passo(
            R.id.navRotasMotorista,
            "Rotas",
            "Crie suas rotas e responda aos pedidos de vaga dos responsáveis." + COMO_USAR
        ),
        Passo(
            R.id.navVeiculosMotorista,
            "Veículos",
            "Cadastre a van que você usa nas rotas."
        ),
        Passo(
            R.id.navChatMotorista,
            "Conversas",
            "Fale com os responsáveis pelos alunos."
        ),
        Passo(
            R.id.btnMenu,
            "Menu",
            "Aqui ficam a Tabela de preços, a gestão de pagamentos, seus documentos e a ajuda. " +
                "Comece pela Tabela de preços: é com ela que os pais veem quanto custa a sua van."
        )
    )

    // =========================================================
    // "JÁ VIU?" (SharedPreferences, no próprio celular)
    // =========================================================

    private const val PREFS = "guia_primeiro_acesso"

    /** Evita abrir o guia duas vezes se a tela voltar ao primeiro plano no meio dele. */
    private var emAndamento = false

    private fun chave(perfil: PerfilAjuda): String {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        return "visto_${perfil.name.lowercase()}_$uid"
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun jaViu(context: Context, perfil: PerfilAjuda): Boolean =
        prefs(context).getBoolean(chave(perfil), false)

    private fun marcarComoVisto(context: Context, perfil: PerfilAjuda) {
        prefs(context).edit().putBoolean(chave(perfil), true).apply()
    }

    /** Usado pela tela de Ajuda: o guia aparece de novo ao voltar para a tela inicial. */
    fun mostrarDeNovo(context: Context, perfil: PerfilAjuda) {
        prefs(context).edit().remove(chave(perfil)).apply()
    }

    // =========================================================
    // EXIBIÇÃO
    // =========================================================

    /**
     * Chamado no onResume das telas iniciais. Se o usuário ainda não viu o guia,
     * espera a tela terminar de ser desenhada e começa a sequência.
     */
    fun iniciarSeNecessario(activity: Activity, perfil: PerfilAjuda) {
        if (emAndamento || jaViu(activity, perfil)) return

        // A biblioteca precisa saber ONDE cada botão está, então espera o desenho da tela
        activity.window.decorView.post { iniciar(activity, perfil) }
    }

    private fun iniciar(activity: Activity, perfil: PerfilAjuda) {
        if (emAndamento || activity.isFinishing || activity.isDestroyed) return

        val passos = if (perfil == PerfilAjuda.MOTORISTA) passosMotorista else passosResponsavel

        // Passo cujo botão não está visível é pulado, em vez de apontar para o lugar errado
        val alvos = passos.mapNotNull { passo ->
            val view = activity.findViewById<View>(passo.viewId)
            if (view == null || !view.isShown || view.width == 0) null
            else criarAlvo(view, passo)
        }
        if (alvos.isEmpty()) return

        emAndamento = true

        TapTargetSequence(activity)
            .targets(alvos)
            .continueOnCancel(false)          // tocar fora = pular o resto do guia
            .considerOuterCircleCanceled(true)
            .listener(object : TapTargetSequence.Listener {
                override fun onSequenceFinish() = concluir(activity, perfil)
                override fun onSequenceStep(lastTarget: TapTarget, targetClicked: Boolean) = Unit
                override fun onSequenceCanceled(lastTarget: TapTarget) = concluir(activity, perfil)
            })
            .start()
    }

    /** Terminou ou pulou: não mostra mais (até pedir de novo na Ajuda). */
    private fun concluir(activity: Activity, perfil: PerfilAjuda) {
        emAndamento = false
        marcarComoVisto(activity, perfil)
    }

    /** Visual nas cores do app: fundo cinza-escuro, destaque laranja, texto branco. */
    private fun criarAlvo(view: View, passo: Passo): TapTarget =
        TapTarget.forView(view, passo.titulo, passo.descricao)
            .outerCircleColor(R.color.dark_gray)
            .outerCircleAlpha(0.96f)
            .targetCircleColor(R.color.orange)
            .titleTextColor(R.color.orange)
            .titleTextSize(22)
            .descriptionTextColorInt(Color.WHITE)
            .descriptionTextSize(15)
            .descriptionTextAlpha(0.9f)
            .dimColor(R.color.black)
            .drawShadow(true)
            .cancelable(true)
            .tintTarget(false)
            .transparentTarget(true)
            .targetRadius(44)
            // O app desenha de ponta a ponta (edge-to-edge)
            .setDrawBehindStatusBar(true)
            .setDrawBehindNavigationBar(true)
}
