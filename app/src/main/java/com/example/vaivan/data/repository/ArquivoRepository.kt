package com.example.vaivan.data.repository

import android.net.Uri
import com.example.vaivan.data.local.ArquivoLocalStorage
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.File

class ArquivoRepository(
    private val arquivoLocalStorage: ArquivoLocalStorage
) {

    private val storage = FirebaseStorage.getInstance()

    suspend fun enviar(
        uri: Uri,
        caminhoStorage: String
    ): String {

        val referencia = storage
            .reference
            .child(caminhoStorage)

        referencia.putFile(uri).await()

        return referencia.downloadUrl.await().toString()
    }

    suspend fun enviarEGuardarLocalmente(
        uri: Uri,
        caminhoStorage: String,
        nomeLocal: String
    ): String {

        val url = enviar(
            uri = uri,
            caminhoStorage = caminhoStorage
        )

        arquivoLocalStorage.salvar(
            uri = uri,
            nome = nomeLocal
        )

        return url
    }

    suspend fun baixarEGuardarLocalmente(
        caminhoStorage: String,
        nomeLocal: String
    ): File {

        val arquivoLocal =
            arquivoLocalStorage.obterArquivo(nomeLocal)

        storage
            .reference
            .child(caminhoStorage)
            .getFile(arquivoLocal)
            .await()

        return arquivoLocal
    }

    suspend fun excluir(
        caminhoStorage: String,
        nomeLocal: String? = null
    ) {

        storage
            .reference
            .child(caminhoStorage)
            .delete()
            .await()

        if (nomeLocal != null) {
            arquivoLocalStorage.excluir(nomeLocal)
        }
    }
}