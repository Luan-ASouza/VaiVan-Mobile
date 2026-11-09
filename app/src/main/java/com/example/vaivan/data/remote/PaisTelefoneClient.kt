package com.example.vaivan.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class PaisTelefone(
    val nome: String,
    val codigo: String
)

/** Lista de países + código de discagem internacional, via REST Countries
 * (API pública, sem autenticação/chave). Usada no seletor de país da etapa
 * de identificação do cadastro, no lugar de uma lista fixa digitada à mão. */
class PaisTelefoneClient {

    suspend fun listarPaises(): List<PaisTelefone> = withContext(Dispatchers.IO) {

        var conexao: HttpURLConnection? = null

        try {
            val url = URL("https://restcountries.com/v3.1/all?fields=name,idd")

            conexao = url.openConnection() as HttpURLConnection
            conexao.requestMethod = "GET"
            conexao.connectTimeout = 5000
            conexao.readTimeout = 5000

            val resultado = BufferedReader(InputStreamReader(conexao.inputStream)).use { it.readText() }
            val json = JSONArray(resultado)

            (0 until json.length()).mapNotNull { i ->
                val pais = json.getJSONObject(i)
                val nome = pais.optJSONObject("name")?.optString("common")
                    ?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null

                val root = pais.optJSONObject("idd")?.optString("root", "").orEmpty()
                if (root.isBlank()) return@mapNotNull null

                PaisTelefone(nome, root)
            }.sortedBy { it.nome }

        } finally {
            conexao?.disconnect()
        }
    }
}
