package com.example.vaivan.data.local

import android.content.Context
import android.net.Uri
import java.io.File

class ArquivoLocalStorage(
    private val context: Context
) {

    private val pastaArquivos =
        File(context.filesDir, "arquivos")

    init {
        if (!pastaArquivos.exists()) {
            pastaArquivos.mkdirs()
        }
    }

    fun obterArquivo(nome: String): File {
        return File(pastaArquivos, nome)
    }

    fun existe(nome: String): Boolean {
        return obterArquivo(nome).exists()
    }

    fun salvar(uri: Uri, nome: String): File {
        val arquivo = obterArquivo(nome)

        context.contentResolver.openInputStream(uri)?.use { entrada ->
            arquivo.outputStream().use { saida ->
                entrada.copyTo(saida)
            }
        } ?: throw IllegalArgumentException(
            "Não foi possível abrir o arquivo."
        )

        return arquivo
    }

    fun excluir(nome: String): Boolean {
        return obterArquivo(nome).delete()
    }
}