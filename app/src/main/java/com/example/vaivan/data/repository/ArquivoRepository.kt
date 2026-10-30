package com.example.vaivan.data.repository

import android.net.Uri
import com.example.vaivan.data.local.ArquivoLocalStorage
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * arquivoLocalStorage só é necessário para enviarEGuardarLocalmente/baixarEGuardarLocalmente;
 * uploads que não precisam de espelho local (ex.: documentos do motorista) podem
 * instanciar sem esse argumento.
 */
class ArquivoRepository(
    private val arquivoLocalStorage: ArquivoLocalStorage? = null
) {

    private val storage = FirebaseStorage.getInstance()

    fun referencia(caminhoStorage: String): StorageReference =
        storage.reference.child(caminhoStorage)

    /** Caminho padrão para documentos do motorista: documentos/{motoristaId}/{tipo}/{slot}.{extensao} */
    fun caminhoDocumentoMotorista(
        motoristaId: String,
        tipo: String,
        slot: String,
        extensao: String
    ): String = "documentos/$motoristaId/$tipo/$slot.$extensao"

    /** Inicia o upload e devolve a UploadTask, para quem chamou acompanhar progresso/cancelar. */
    fun iniciarUpload(
        uri: Uri,
        caminhoStorage: String,
        contentType: String? = null
    ): UploadTask {
        val referencia = referencia(caminhoStorage)
        val metadata = contentType?.let {
            StorageMetadata.Builder().setContentType(it).build()
        }
        return if (metadata != null) {
            referencia.putFile(uri, metadata)
        } else {
            referencia.putFile(uri)
        }
    }

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

        requireArquivoLocalStorage().salvar(
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
            requireArquivoLocalStorage().obterArquivo(nomeLocal)

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
            requireArquivoLocalStorage().excluir(nomeLocal)
        }
    }

    private fun requireArquivoLocalStorage(): ArquivoLocalStorage =
        arquivoLocalStorage
            ?: throw IllegalStateException(
                "ArquivoRepository foi criado sem ArquivoLocalStorage; " +
                        "use enviar()/excluir() sem espelho local."
            )
}