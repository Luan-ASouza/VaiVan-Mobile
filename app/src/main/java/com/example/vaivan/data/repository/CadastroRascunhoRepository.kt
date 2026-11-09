package com.example.vaivan.data.repository

import android.content.Context
import com.example.vaivan.data.local.entities.UsuarioEntity
import com.example.vaivan.data.models.CadastroUsuario

data class RascunhoCadastro(
    val cadastro: CadastroUsuario,
    /** Etapas já visitadas, em ordem (ex.: [IDENTIFICACAO, CREDENCIAIS_SENHA,
     * VERIFICACAO_EMAIL, INFORMACOES_PESSOAIS]) — a última é a etapa atual/
     * retomada. Usado pra reconstruir a pilha de navegação inteira ao
     * retomar, pra "voltar" dentro do assistente continuar funcionando
     * mesmo depois do app ter sido fechado. */
    val historico: List<String>
)

/**
 * Rascunho do cadastro em andamento, salvo localmente (SharedPreferences) a
 * cada etapa concluída — sem isso, fechar o app no meio do assistente perde
 * tudo que já foi digitado, já que o CadastroViewModel só vive na memória.
 *
 * Nunca vai pro Firestore (só o usuário final e completo vai, via
 * UsuarioRepository.salvarUsuario) e nunca guarda a senha — depois que a
 * conta é criada (etapa de credenciais), a senha não faz falta de novo pra
 * retomar o assistente em nenhuma etapa seguinte.
 */
class CadastroRascunhoRepository(context: Context) {

    companion object {
        const val ETAPA_IDENTIFICACAO = "IDENTIFICACAO"
        const val ETAPA_CREDENCIAIS_SENHA = "CREDENCIAIS_SENHA"
        const val ETAPA_VERIFICACAO_EMAIL = "VERIFICACAO_EMAIL"
        const val ETAPA_INFORMACOES_PESSOAIS = "INFORMACOES_PESSOAIS"
        const val ETAPA_DATA_NASCIMENTO = "DATA_NASCIMENTO"
        const val ETAPA_ENDERECO = "ENDERECO"
        const val ETAPA_CONSENTIMENTO = "CONSENTIMENTO"

        private const val CHAVE_HISTORICO = "historico"
    }

    private val prefs = context.getSharedPreferences("cadastro_rascunho", Context.MODE_PRIVATE)

    /** [proximaEtapa] é anexada ao histórico já salvo (começando implicitamente
     * em [ETAPA_IDENTIFICACAO] se ainda não houver nada salvo). */
    fun salvar(cadastro: CadastroUsuario, proximaEtapa: String) {
        val historicoAtual = historico() ?: listOf(ETAPA_IDENTIFICACAO)
        val novoHistorico = historicoAtual + proximaEtapa
        val usuario = cadastro.usuario

        prefs.edit()
            .putString(CHAVE_HISTORICO, novoHistorico.joinToString(","))
            .putString("email", usuario.email)
            .putString("telefone", usuario.telefone)
            .putBoolean("emailConfirmado", usuario.emailConfirmado)
            .putString("nome", usuario.nome)
            .putString("sobrenome", usuario.sobrenome)
            .putString("cpf", usuario.cpf)
            .putString("dataNascimento", usuario.dataNascimento)
            .putString("cep", usuario.cep)
            .putString("estado", usuario.estado)
            .putString("cidade", usuario.cidade)
            .putString("bairro", usuario.bairro)
            .putString("rua", usuario.rua)
            .putString("numero", usuario.numero)
            .putString("complemento", usuario.complemento)
            .apply()
    }

    fun carregar(): RascunhoCadastro? {
        val historico = historico() ?: return null

        val usuario = UsuarioEntity(
            email = prefs.getString("email", "").orEmpty(),
            telefone = prefs.getString("telefone", "").orEmpty(),
            emailConfirmado = prefs.getBoolean("emailConfirmado", false),
            nome = prefs.getString("nome", "").orEmpty(),
            sobrenome = prefs.getString("sobrenome", "").orEmpty(),
            cpf = prefs.getString("cpf", "").orEmpty(),
            dataNascimento = prefs.getString("dataNascimento", null),
            cep = prefs.getString("cep", "").orEmpty(),
            estado = prefs.getString("estado", "").orEmpty(),
            cidade = prefs.getString("cidade", "").orEmpty(),
            bairro = prefs.getString("bairro", "").orEmpty(),
            rua = prefs.getString("rua", "").orEmpty(),
            numero = prefs.getString("numero", "").orEmpty(),
            complemento = prefs.getString("complemento", null)
        )

        return RascunhoCadastro(CadastroUsuario(usuario = usuario), historico)
    }

    fun limpar() {
        prefs.edit().clear().apply()
    }

    private fun historico(): List<String>? =
        prefs.getString(CHAVE_HISTORICO, null)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
}
