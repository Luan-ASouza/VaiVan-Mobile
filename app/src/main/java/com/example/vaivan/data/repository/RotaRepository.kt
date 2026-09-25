package com.example.vaivan.data.repository

import android.util.Log
import com.example.vaivan.data.local.dao.ParadaRotaDao
import com.example.vaivan.data.local.dao.RotaDao
import com.example.vaivan.data.local.entities.ParadaRotaEntity
import com.example.vaivan.data.local.entities.RotaEntity
import com.example.vaivan.data.remote.routes.GoogleRoutesClient
import com.example.vaivan.data.remote.routes.PontoRota
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Firestore = fonte da verdade
 * Room = cache local observado pela UI
 *
 * Nomenclatura:
 *
 * observar   → Room → Flow
 * consultar  → Firebase, consulta pontual
 * sincronizar → Firebase → Room
 * salvar     → Firebase + Room
 * excluir    → Firebase + Room
 *
 * O Repository executa as operações de dados.
 * O SyncManager controla o ciclo de vida dos listeners.
 */
class RotaRepository(
    private val rotaDao: RotaDao,
    private val paradaRotaDao: ParadaRotaDao,
    private val routesClient: GoogleRoutesClient,
    private val firestore: FirebaseFirestore =
        FirebaseFirestore.getInstance()
) {

    private val collectionRotas =
        firestore.collection("rotas")

    private val collectionParadas =
        firestore.collection("paradas_rota")

    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.IO
        )

    private var listenerRotas: ListenerRegistration? = null


    // =========================================================
    // OBSERVAÇÃO
    // =========================================================

    /** Observa as rotas de um motorista no Room. */
    fun observarRotasPorMotorista(
        motoristaId: String
    ): Flow<List<RotaEntity>> {

        return rotaDao.getByMotorista(
            motoristaId
        )
    }

    /** Observa uma rota específica no Room. */
    fun observarRotaPorId(
        rotaId: String
    ): Flow<RotaEntity?> {

        return rotaDao.getById(
            rotaId
        )
    }

    /** Observa as paradas de uma rota no Room. */
    fun observarParadas(
        rotaId: String
    ): Flow<List<ParadaRotaEntity>> {

        return paradaRotaDao.getByRota(
            rotaId
        )
    }


    // =========================================================
    // CONSULTAS
    // =========================================================

    /** Consulta uma rota diretamente no Firebase. */
    suspend fun consultarRota(
        rotaId: String
    ): RotaEntity? {

        val document =
            collectionRotas
                .document(rotaId)
                .get()
                .await()

        if (!document.exists()) {
            return null
        }

        return document
            .toObject(RotaEntity::class.java)
            ?.copy(
                id = document.id
            )
    }

    /** Consulta as paradas de uma rota diretamente no Firebase. */
    suspend fun consultarParadas(
        rotaId: String
    ): List<ParadaRotaEntity> {

        val snapshot =
            collectionParadas
                .whereEqualTo(
                    "rotaId",
                    rotaId
                )
                .get()
                .await()

        return snapshot.documents.mapNotNull { document ->

            document
                .toObject(
                    ParadaRotaEntity::class.java
                )
                ?.copy(
                    id = document.id
                )
        }
    }

    /** Consulta os passageiros de uma rota diretamente no Firebase. */
    suspend fun consultarParadasPorPassageiro(
        passageiroId: String
    ): List<ParadaRotaEntity> {

        return collectionParadas
            .whereEqualTo(
                "passageiroId",
                passageiroId
            )
            .get()
            .await()
            .documents
            .mapNotNull { document ->

                document
                    .toObject(
                        ParadaRotaEntity::class.java
                    )
                    ?.copy(
                        id = document.id
                    )
            }
    }


    // =========================================================
    // SINCRONIZAÇÃO
    // =========================================================

    /** Inicia a sincronização das rotas de um motorista. */
    fun iniciarSincronizacaoDasRotasDoMotorista(
        motoristaId: String
    ): ListenerRegistration {

        listenerRotas?.remove()

        listenerRotas =
            collectionRotas
                .whereEqualTo(
                    "motoristaId",
                    motoristaId
                )
                .addSnapshotListener { snapshot, error ->

                    if (
                        error != null ||
                        snapshot == null
                    ) {
                        return@addSnapshotListener
                    }

                    scope.launch {
                        sincronizarSnapshot(
                            snapshot
                        )
                    }
                }

        return listenerRotas!!
    }

    /** Aplica no Room somente as alterações recebidas do Firebase. */
    private suspend fun sincronizarSnapshot(
        snapshot: QuerySnapshot
    ) {

        for (change in snapshot.documentChanges) {

            val document =
                change.document

            val rotaId =
                document.id

            when (change.type) {

                DocumentChange.Type.ADDED,
                DocumentChange.Type.MODIFIED -> {

                    val rota =
                        document
                            .toObject(
                                RotaEntity::class.java
                            )
                            .copy(
                                id = rotaId,
                                lastUpdated =
                                    System.currentTimeMillis()
                            )

                    rotaDao.upsert(
                        rota
                    )
                }

                DocumentChange.Type.REMOVED -> {

                    rotaDao.deleteById(
                        rotaId
                    )
                }
            }
        }
    }

    fun pararSincronizacao() {

        listenerRotas?.remove()

        listenerRotas = null
    }

    /** Faz uma sincronização pontual das rotas do motorista. */
    suspend fun sincronizarRotasDoMotoristaUmaVez(
        motoristaId: String
    ) {

        val snapshot =
            collectionRotas
                .whereEqualTo(
                    "motoristaId",
                    motoristaId
                )
                .get()
                .await()

        val rotas =
            snapshot.documents.mapNotNull { document ->

                document
                    .toObject(
                        RotaEntity::class.java
                    )
                    ?.copy(
                        id = document.id,
                        lastUpdated =
                            System.currentTimeMillis()
                    )
            }

        rotaDao.upsertAll(
            rotas
        )
    }


    // =========================================================
    // CRIAR ROTA
    // =========================================================

    suspend fun criarRota(
        motoristaId: String,
        veiculoId: String?,
        nome: String,
        origemNome: String,
        origemLatitude: Double,
        origemLongitude: Double,
        destinoNome: String,
        destinoEndereco: String,
        destinoLatitude: Double,
        destinoLongitude: Double,
        turno: String,
        diasSemana: List<String>,
        capacidadeTotal: Int
    ): RotaEntity = withContext(Dispatchers.IO) {

        val documentReference =
            collectionRotas.document()

        val agora =
            System.currentTimeMillis()

        val origem =
            PontoRota(
                id = "origem",
                nome = origemNome,
                endereco = "",
                latitude = origemLatitude,
                longitude = origemLongitude
            )

        val destino =
            PontoRota(
                id = "destino",
                nome = destinoNome,
                endereco = destinoEndereco,
                latitude = destinoLatitude,
                longitude = destinoLongitude
            )

        val resultadoInicial =
            try {

                routesClient.calcularMelhorRota(
                    origem = origem,
                    destino = destino,
                    paradas = emptyList()
                )

            } catch (e: Exception) {

                Log.e(
                    "RotaRepository",
                    "Erro ao calcular rota inicial",
                    e
                )

                null
            }

        val rota =
            RotaEntity(
                id = documentReference.id,

                motoristaId = motoristaId,
                veiculoId = veiculoId,

                nome = nome,

                origemNome = origemNome,
                origemLatitude = origemLatitude,
                origemLongitude = origemLongitude,

                destinoNome = destinoNome,
                destinoEndereco = destinoEndereco,
                destinoLatitude = destinoLatitude,
                destinoLongitude = destinoLongitude,

                turno = turno,
                diasSemana =
                    diasSemana.joinToString(","),

                capacidadeTotal =
                    capacidadeTotal,

                vagasOcupadas = 0,

                distanciaMetros =
                    resultadoInicial
                        ?.distanciaMetros
                        ?: 0,

                duracaoSegundos =
                    resultadoInicial
                        ?.duracaoSegundos
                        ?: 0,

                duracaoSemTrafegoSegundos =
                    resultadoInicial
                        ?.duracaoSemTrafegoSegundos
                        ?: 0,

                polylineEncoded =
                    resultadoInicial
                        ?.polylineEncoded
                        ?: "",

                status = "ATIVA",

                calculadoEm = agora,

                lastUpdated = agora
            )

        // Firestore = fonte da verdade
        documentReference
            .set(rota)
            .await()

        // Room = cache local
        rotaDao.upsert(
            rota
        )

        rota
    }


    // =========================================================
    // BUSCA TEMPORÁRIA DE ROTAS
    // =========================================================

    suspend fun buscarRotasDisponiveis(
        textoBusca: String,
        turno: String?
    ): List<RotaEntity> =
        withContext(Dispatchers.IO) {

            var query: Query =
                collectionRotas
                    .whereEqualTo(
                        "status",
                        "ATIVA"
                    )

            if (!turno.isNullOrBlank()) {

                query =
                    query.whereEqualTo(
                        "turno",
                        turno
                    )
            }

            val snapshot =
                query
                    .get()
                    .await()

            val rotas =
                snapshot.documents
                    .mapNotNull { document ->

                        document
                            .toObject(
                                RotaEntity::class.java
                            )
                            ?.copy(
                                id = document.id
                            )
                    }
                    .filter { rota ->

                        rota.vagasOcupadas <
                                rota.capacidadeTotal
                    }

            if (textoBusca.isBlank()) {

                rotas

            } else {

                rotas.filter { rota ->

                    rota.destinoNome.contains(
                        textoBusca,
                        ignoreCase = true
                    )
                }
            }
        }


    // =========================================================
    // OPERAÇÕES PARA USE CASE
    // =========================================================

    suspend fun buscarRotaNoFirestore(
        rotaId: String
    ): RotaEntity {

        return consultarRota(
            rotaId
        ) ?: throw IllegalStateException(
            "Rota não encontrada."
        )
    }

    suspend fun buscarParadasNoFirestore(
        rotaId: String
    ): List<ParadaRotaEntity> {

        return consultarParadas(
            rotaId
        )
    }


    suspend fun salvarRotaRecalculada(
        rota: RotaEntity
    ) {

        collectionRotas
            .document(rota.id)
            .set(rota)
            .await()

        rotaDao.upsert(
            rota
        )
    }


    suspend fun salvarParadasRecalculadas(
        rotaId: String,
        paradas: List<ParadaRotaEntity>
    ) {

        paradas.forEach { parada ->

            collectionParadas
                .document(parada.id)
                .set(parada)
                .await()
        }

        paradaRotaDao.deleteByRota(
            rotaId
        )

        paradaRotaDao.upsertAll(
            paradas
        )
    }


    fun novoIdParada(): String {

        return collectionParadas
            .document()
            .id
    }


    // =========================================================
    // MODELO AUXILIAR
    // =========================================================

    data class ParadaCandidata(

        val passageiroId: String,

        val nomePassageiro: String,

        val localId: String,

        val nomeLocal: String,

        val endereco: String,

        val latitude: Double,

        val longitude: Double
    )
}