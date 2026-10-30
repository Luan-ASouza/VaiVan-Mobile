package com.example.vaivan.data.repository

import android.net.Uri
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.example.vaivan.data.local.dao.DocumentoDao
import com.example.vaivan.data.local.entities.DocumentoEntity
import com.example.vaivan.data.models.StatusDocumento
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Segue o mesmo padrão de MotoristaRepository: Firestore = fonte da
 * verdade, Room (DocumentoDao) = cache local que a UI observa.
 *
 * Porta de entrada única para documentos de motorista (CNH, comprovante de
 * residência) e de veículo (CRLV, autorização). As Activities não devem
 * falar diretamente com FirebaseFirestore/FirebaseStorage para documentos;
 * usam os métodos daqui.
 */
class DocumentoRepository(
    private val documentoDao: DocumentoDao,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val arquivoRepository: ArquivoRepository = ArquivoRepository()
) {
    private val collection = firestore.collection("documentos")
    private var listenerRegistration: ListenerRegistration? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        const val TIPO_CNH = "CNH"
        const val TIPO_COMPROVANTE_RESIDENCIA = "COMPROVANTE_RESIDENCIA"
        const val TIPO_SELFIE = "SELFIE"

        const val SLOT_FRENTE = "frente"
        const val SLOT_VERSO = "verso"
        const val SLOT_DIGITAL = "digital"
        const val SLOT_COMPROVANTE = "arquivo"
        const val SLOT_SELFIE = "foto"

        const val MODALIDADE_CNH_FISICA = "FISICA"
        const val MODALIDADE_CNH_DIGITAL = "DIGITAL"

        fun idDocumentoMotorista(motoristaId: String, tipo: String) = "${motoristaId}_$tipo"

        fun extensaoPara(mimeType: String): String = when (mimeType) {
            "application/pdf" -> "pdf"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
    }

    fun observarTodosDocumentos(): Flow<List<DocumentoEntity>> = documentoDao.getAll()

    fun observarPorId(id: String): Flow<DocumentoEntity?> = documentoDao.getById(id)

    fun observarPorMotorista(motoristaId: String): Flow<List<DocumentoEntity>> =
        documentoDao.getByMotorista(motoristaId)

    fun observarCnh(motoristaId: String): Flow<DocumentoEntity?> =
        documentoDao.getById(idDocumentoMotorista(motoristaId, TIPO_CNH))

    fun observarComprovante(motoristaId: String): Flow<DocumentoEntity?> =
        documentoDao.getById(idDocumentoMotorista(motoristaId, TIPO_COMPROVANTE_RESIDENCIA))

    // =========================================================
    // UPLOAD (Storage) — caminho e tarefa ficam centralizados aqui;
    // quem chama acompanha progresso/cancela pela UploadTask devolvida,
    // e usa a referencia (não a task) para obter a downloadUrl depois.
    // =========================================================

    data class UploadEmAndamento(
        val referencia: StorageReference,
        val tarefa: UploadTask
    )

    fun iniciarUploadCnh(
        motoristaId: String,
        slot: String,
        uri: Uri,
        mimeType: String
    ): UploadEmAndamento {
        val caminho = arquivoRepository.caminhoDocumentoMotorista(
            motoristaId, TIPO_CNH, slot, extensaoPara(mimeType)
        )
        val referencia = arquivoRepository.referencia(caminho)
        return UploadEmAndamento(referencia, arquivoRepository.iniciarUpload(uri, caminho, mimeType))
    }

    fun iniciarUploadComprovante(
        motoristaId: String,
        uri: Uri,
        mimeType: String
    ): UploadEmAndamento {
        val caminho = arquivoRepository.caminhoDocumentoMotorista(
            motoristaId, TIPO_COMPROVANTE_RESIDENCIA, SLOT_COMPROVANTE, extensaoPara(mimeType)
        )
        val referencia = arquivoRepository.referencia(caminho)
        return UploadEmAndamento(referencia, arquivoRepository.iniciarUpload(uri, caminho, mimeType))
    }

    fun iniciarUploadSelfie(
        motoristaId: String,
        uri: Uri,
        mimeType: String
    ): UploadEmAndamento {
        val caminho = arquivoRepository.caminhoDocumentoMotorista(
            motoristaId, TIPO_SELFIE, SLOT_SELFIE, extensaoPara(mimeType)
        )
        val referencia = arquivoRepository.referencia(caminho)
        return UploadEmAndamento(referencia, arquivoRepository.iniciarUpload(uri, caminho, mimeType))
    }

    suspend fun excluirArquivoCnh(motoristaId: String, slot: String, mimeType: String) {
        val caminho = arquivoRepository.caminhoDocumentoMotorista(
            motoristaId, TIPO_CNH, slot, extensaoPara(mimeType)
        )
        arquivoRepository.excluir(caminho)
    }

    suspend fun excluirArquivoComprovante(motoristaId: String, mimeType: String) {
        val caminho = arquivoRepository.caminhoDocumentoMotorista(
            motoristaId, TIPO_COMPROVANTE_RESIDENCIA, SLOT_COMPROVANTE, extensaoPara(mimeType)
        )
        arquivoRepository.excluir(caminho)
    }

    // =========================================================
    // CNH e COMPROVANTE — 1 documento por requisito (Firestore + Room)
    // =========================================================

    suspend fun salvarCnhFisica(
        motoristaId: String,
        urlFrente: String,
        urlVerso: String,
        dataEnvio: String
    ) {
        salvar(
            DocumentoEntity(
                id = idDocumentoMotorista(motoristaId, TIPO_CNH),
                tipo = TIPO_CNH,
                arquivo = urlFrente,
                arquivoVerso = urlVerso,
                modalidadeCnh = MODALIDADE_CNH_FISICA,
                dataEnvio = dataEnvio,
                statusValidacao = StatusDocumento.EM_ANALISE.name,
                motoristaId = motoristaId,
                veiculoId = ""
            )
        )
    }

    suspend fun salvarCnhDigital(
        motoristaId: String,
        urlPdf: String,
        dataEnvio: String
    ) {
        salvar(
            DocumentoEntity(
                id = idDocumentoMotorista(motoristaId, TIPO_CNH),
                tipo = TIPO_CNH,
                arquivo = urlPdf,
                arquivoVerso = null,
                modalidadeCnh = MODALIDADE_CNH_DIGITAL,
                dataEnvio = dataEnvio,
                statusValidacao = StatusDocumento.EM_ANALISE.name,
                motoristaId = motoristaId,
                veiculoId = ""
            )
        )
    }

    suspend fun salvarComprovante(
        motoristaId: String,
        url: String,
        dataEnvio: String
    ) {
        salvar(
            DocumentoEntity(
                id = idDocumentoMotorista(motoristaId, TIPO_COMPROVANTE_RESIDENCIA),
                tipo = TIPO_COMPROVANTE_RESIDENCIA,
                arquivo = url,
                dataEnvio = dataEnvio,
                statusValidacao = StatusDocumento.EM_ANALISE.name,
                motoristaId = motoristaId,
                veiculoId = ""
            )
        )
    }

    suspend fun salvarSelfie(
        motoristaId: String,
        url: String,
        dataEnvio: String
    ) {
        salvar(
            DocumentoEntity(
                id = idDocumentoMotorista(motoristaId, TIPO_SELFIE),
                tipo = TIPO_SELFIE,
                arquivo = url,
                dataEnvio = dataEnvio,
                statusValidacao = StatusDocumento.EM_ANALISE.name,
                motoristaId = motoristaId,
                veiculoId = ""
            )
        )
    }

    fun iniciarSincronizacao() {
        listenerRegistration?.remove()
        // A consulta precisa ser filtrada por motorista: as regras só permitem
        // ler os documentos do próprio usuário (ou tudo, no caso do admin).
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        listenerRegistration = collection.whereEqualTo("motoristaId", uid).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            scope.launch {
                val itens = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(DocumentoEntity::class.java)?.copy(
                        id = doc.id,
                        lastUpdated = System.currentTimeMillis()
                    )
                }
                documentoDao.upsertAll(itens)
            }
        }
    }

    fun pararSincronizacao() {
        listenerRegistration?.remove()
        listenerRegistration = null
    }

    suspend fun sincronizarUmaVez() {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val snapshot = collection.whereEqualTo("motoristaId", uid).get().await()
        val itens = snapshot.documents.mapNotNull { doc ->
            doc.toObject(DocumentoEntity::class.java)?.copy(
                id = doc.id,
                lastUpdated = System.currentTimeMillis()
            )
        }
        documentoDao.upsertAll(itens)
    }

    suspend fun salvar(item: DocumentoEntity) {
        val docRef = if (item.id.isBlank()) {
            collection.document()
        } else {
            collection.document(item.id)
        }
        val comId = item.copy(id = docRef.id, lastUpdated = System.currentTimeMillis())
        docRef.set(comId).await()
        documentoDao.upsert(comId)
    }

    suspend fun excluir(id: String) {
        collection.document(id).delete().await()
        documentoDao.deleteById(id)
    }
    fun observarPorVeiculo(veiculoId: String): Flow<List<DocumentoEntity>> =
        documentoDao.getByVeiculo(veiculoId)

    fun salvarAsync(
        item: DocumentoEntity,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        scope.launch {
            try {
                salvar(item)
                kotlinx.coroutines.withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(Dispatchers.Main) { onError(e) }
            }
        }
    }
}