package com.example.vaivan.data.repository

import android.net.Uri
import com.example.vaivan.data.local.dao.ParadaRotaDao
import com.example.vaivan.data.local.dao.PassageiroDao
import com.example.vaivan.data.local.dao.SolicitacaoInclusaoDao
import com.example.vaivan.data.local.entities.PassageiroEntity
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.util.Log

/*
 * Nomenclatura:
 *
 * observar  → Room → Flow
 * consultar → Firebase, consulta pontual
 * sincronizar → Firebase → Room, mantendo os dados atualizados
 * salvar    → Firebase + Room
 * excluir   → Firebase + Room
 *
 * O Firebase é a fonte de verdade dos dados compartilhados.
 * O Room funciona como cache local observado pela UI.
 *
 * O Repository executa as operações de dados.
 * O SyncManager controla o ciclo de vida dos listeners.
 */

class PassageiroRepository(
    private val passageiroDao: PassageiroDao,
    private val firestore: FirebaseFirestore =
        FirebaseFirestore.getInstance(),
    private val arquivoRepository: ArquivoRepository = ArquivoRepository(),
    // Opcionais: só quem realmente aciona excluirPassageiro precisa passar,
    // pra limpeza em cascata também refletir no Room na hora (sem eles, a
    // exclusão no Firestore ainda acontece normalmente — quem cacheia essas
    // tabelas se atualiza pelo próprio listener quando existir um ativo).
    private val paradaRotaDao: ParadaRotaDao? = null,
    private val solicitacaoInclusaoDao: SolicitacaoInclusaoDao? = null
) {

    private val collection =
        firestore.collection("passageiros")

    private val collectionParadasRota =
        firestore.collection("paradas_rota")

    private val collectionSolicitacoes =
        firestore.collection("solicitacoes_inclusao")

    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.IO
        )


    // =========================================================
    // OBSERVAÇÃO
    // =========================================================

    /** Observa todos os passageiros armazenados no Room. */
    fun observarPassageiros():
            Flow<List<PassageiroEntity>> {

        return passageiroDao.getAll()
    }

    /** Observa um passageiro específico pelo ID no Room. */
    fun observarPassageiro(
        id: String
    ): Flow<PassageiroEntity?> {

        return passageiroDao.getById(id)
    }

    /** Observa os passageiros de um responsável no Room. */
    fun observarPassageirosDoResponsavel(
        responsavelId: String
    ): Flow<List<PassageiroEntity>> {

        return passageiroDao.getByResponsavel(
            responsavelId
        )
    }

    /** Observa os passageiros de uma rota no Room. */
    fun observarPassageirosDaRota(
        rotaId: String
    ): Flow<List<PassageiroEntity>> {

        return passageiroDao.getByRota(
            rotaId
        )
    }


    // =========================================================
    // CONSULTAS
    // =========================================================

    /** Consulta um passageiro diretamente no Firebase. */
    suspend fun consultarPassageiro(
        id: String
    ): PassageiroEntity? {

        val document =
            collection
                .document(id)
                .get()
                .await()

        if (!document.exists()) {
            return null
        }

        return document
            .toObject(PassageiroEntity::class.java)
            ?.copy(
                id = document.id
            )
    }

    /** Consulta no Firebase os passageiros de um responsável. */
    suspend fun consultarPassageirosDoResponsavel(
        responsavelId: String
    ): List<PassageiroEntity> {

        val snapshot =
            collection
                .whereEqualTo(
                    "responsavelId",
                    responsavelId
                )
                .get()
                .await()

        return snapshot.documents.mapNotNull { document ->
            document
                .toObject(PassageiroEntity::class.java)
                ?.copy(
                    id = document.id
                )
        }
    }

    /** Sincronização pontual (pull-to-refresh) — mesma ideia de
     * RotaRepository.sincronizarRotasDoMotoristaUmaVez: não depende do
     * listener global (SyncManager) estar ativo nesse momento. */
    suspend fun sincronizarPassageirosDoResponsavelUmaVez(responsavelId: String) {
        passageiroDao.upsertAll(consultarPassageirosDoResponsavel(responsavelId))
    }


    // =========================================================
    // SINCRONIZAÇÃO
    // =========================================================

    /** Inicia a sincronização dos passageiros de um responsável. */
    fun iniciarSincronizacaoDosPassageirosDoResponsavel(
        responsavelId: String
    ): ListenerRegistration {

        Log.d(
            "PASSAGEIRO_SYNC",
            "Iniciando listener. responsavelId=$responsavelId"
        )

        return collection
            .whereEqualTo(
                "responsavelId",
                responsavelId
            )
            .addSnapshotListener { snapshot, error ->

                if (error != null) {

                    Log.e(
                        "PASSAGEIRO_SYNC",
                        "ERRO ao sincronizar passageiros",
                        error
                    )

                    return@addSnapshotListener
                }

                if (snapshot == null) {

                    Log.d(
                        "PASSAGEIRO_SYNC",
                        "Snapshot veio NULL"
                    )

                    return@addSnapshotListener
                }

                Log.d(
                    "PASSAGEIRO_SYNC",
                    "Firebase retornou ${snapshot.documents.size} passageiros"
                )

                snapshot.documents.forEach { document ->

                    Log.d(
                        "PASSAGEIRO_SYNC",
                        "id=${document.id} | " +
                                "responsavelId=${document.getString("responsavelId")}"
                    )
                }

                scope.launch {
                    sincronizarSnapshot(snapshot)
                }
            }
    }

    /** Aplica no Room somente as alterações recebidas do Firebase. */
    private suspend fun sincronizarSnapshot(
        snapshot: QuerySnapshot
    ) {

        for (change in snapshot.documentChanges) {

            val document = change.document
            val passageiroId = document.id

            when (change.type) {

                DocumentChange.Type.ADDED,
                DocumentChange.Type.MODIFIED -> {

                    val passageiro =
                        document
                            .toObject(
                                PassageiroEntity::class.java
                            )
                            .copy(
                                id = passageiroId,
                                lastUpdated =
                                    System.currentTimeMillis()
                            )

                    passageiroDao.upsert(passageiro)
                }

                DocumentChange.Type.REMOVED -> {
                    passageiroDao.deleteById(
                        passageiroId
                    )
                }
            }
        }
    }


    // =========================================================
    // ESCRITA
    // =========================================================

    /** Salva o passageiro no Firebase e atualiza o Room. */
    suspend fun salvarPassageiro(
        passageiro: PassageiroEntity
    ): String {

        val documentReference =
            if (passageiro.id.isBlank()) {
                collection.document()
            } else {
                collection.document(
                    passageiro.id
                )
            }

        val passageiroSalvo =
            passageiro.copy(
                id = documentReference.id,
                lastUpdated =
                    System.currentTimeMillis()
            )

        documentReference
            .set(passageiroSalvo)
            .await()

        passageiroDao.upsert(
            passageiroSalvo
        )

        return passageiroSalvo.id
    }


    // =========================================================
    // FOTO
    // =========================================================

    /** Envia a nova foto pro Storage e atualiza o fotoUrl do passageiro. Recebe
     * a entity já carregada em memória (quem chama já acabou de salvá-la) —
     * evita reler do Firestore só pra reaplicar o mesmo dado. */
    suspend fun atualizarFoto(passageiro: PassageiroEntity, uri: Uri): String {
        val url = arquivoRepository.enviar(
            uri,
            arquivoRepository.caminhoFotoPassageiro(passageiro.responsavelId ?: "", passageiro.id)
        )
        salvarPassageiro(passageiro.copy(fotoUrl = url))
        return url
    }

    /** Remove a foto do Storage e limpa o fotoUrl do passageiro. */
    suspend fun removerFoto(passageiro: PassageiroEntity) {
        if (passageiro.fotoUrl == null) return

        arquivoRepository.excluir(
            arquivoRepository.caminhoFotoPassageiro(passageiro.responsavelId ?: "", passageiro.id)
        )
        salvarPassageiro(passageiro.copy(fotoUrl = null))
    }

    // =========================================================
    // EXCLUSÃO
    // =========================================================

    /** Exclui o passageiro do Firebase e do Room — em cascata, também remove
     * os vínculos dele com rotas (paradas_rota) e qualquer solicitação de
     * inclusão ainda pendente, pra não deixar nada órfão. Nunca toca em
     * ParadaViagemEntity/histórico (já protegido por regra no Firestore). */
    suspend fun excluirPassageiro(
        id: String
    ) {
        val passageiro = consultarPassageiro(id)

        val paradas = collectionParadasRota
            .whereEqualTo("passageiroId", id)
            .get()
            .await()

        // A regra de solicitacoes_inclusao só permite leitura quando
        // responsavelId/motoristaId == uid atual — pra uma QUERY (não um get
        // de documento único), o Firestore só valida isso se o campo também
        // estiver nos filtros da própria query (não dá pra confiar só nos
        // dados que ela retornaria). Sem o whereEqualTo abaixo, a query
        // inteira é negada (PERMISSION_DENIED) mesmo quando os documentos
        // pertencem ao usuário.
        val solicitacoesPendentes = collectionSolicitacoes
            .whereEqualTo("passageiroId", id)
            .whereEqualTo("status", "PENDENTE")
            .whereEqualTo(
                "responsavelId",
                com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
            )
            .get()
            .await()

        Log.d(
            "PASSAGEIRO_EXCLUSAO",
            "passageiro.responsavelId=${passageiro?.responsavelId} | " +
                "uidAtual=${com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid} | " +
                "paradas=${paradas.documents.map { it.id }} | " +
                "solicitacoesPendentes=${solicitacoesPendentes.documents.map { it.id to it.getString("responsavelId") }}"
        )

        val batch = firestore.batch()
        paradas.documents.forEach { batch.delete(it.reference) }
        solicitacoesPendentes.documents.forEach { batch.delete(it.reference) }
        batch.delete(collection.document(id))

        try {
            batch.commit().await()
        } catch (erro: Exception) {
            Log.e("PASSAGEIRO_EXCLUSAO", "Falha ao commitar batch de exclusão", erro)
            // Mensagem enriquecida pra aparecer direto no Toast de erro da UI
            // (ListaPassageirosFragment.excluirPassageiro) — sem precisar de
            // Logcat/Android Studio pra diagnosticar em campo.
            throw Exception(
                "uidAtual=${com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid} " +
                    "responsavelIdDoPassageiro=${passageiro?.responsavelId} " +
                    "qtdParadas=${paradas.documents.size} " +
                    "qtdSolicitacoesPendentes=${solicitacoesPendentes.documents.size} — ${erro.message}",
                erro
            )
        }

        paradaRotaDao?.deleteByPassageiro(id)
        solicitacaoInclusaoDao?.deletePendentesPorPassageiro(id)
        passageiroDao.deleteById(id)

        val responsavelId = passageiro?.responsavelId
        if (passageiro?.fotoUrl != null && !responsavelId.isNullOrBlank()) {
            runCatching {
                arquivoRepository.excluir(arquivoRepository.caminhoFotoPassageiro(responsavelId, id))
            }
        }
    }
}