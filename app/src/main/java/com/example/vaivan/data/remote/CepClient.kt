package com.example.vaivan.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class EnderecoViaCep(
    val cidade: String,
    val estado: String,
    val rua: String,
    val bairro: String
)

/** Consulta de endereço por CEP via ViaCEP (sem autenticação, API pública). */
class CepClient {

    /** Retorna `null` quando o CEP não existe (resposta `erro: true` da
     * ViaCEP). Erros de rede/parsing são propagados pra quem chamar. */
    suspend fun buscarEndereco(cep: String): EnderecoViaCep? = withContext(Dispatchers.IO) {

        var conexao: HttpURLConnection? = null

        try {
            val url = URL("https://viacep.com.br/ws/$cep/json/")

            conexao = url.openConnection() as HttpURLConnection
            conexao.requestMethod = "GET"
            conexao.connectTimeout = 5000
            conexao.readTimeout = 5000

            val resultado = BufferedReader(InputStreamReader(conexao.inputStream)).use { reader ->
                reader.readText()
            }

            val json = JSONObject(resultado)

            if (json.optBoolean("erro", false)) {
                return@withContext null
            }

            EnderecoViaCep(
                cidade = json.optString("localidade", ""),
                estado = json.optString("uf", ""),
                rua = json.optString("logradouro", ""),
                bairro = json.optString("bairro", "")
            )
        } finally {
            conexao?.disconnect()
        }
    }
}
