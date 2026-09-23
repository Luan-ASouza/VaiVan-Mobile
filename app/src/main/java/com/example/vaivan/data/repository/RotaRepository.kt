package com.example.vaivan.data.repository

import android.util.Log
import com.example.vaivan.data.local.dao.ParadaRotaDao
import com.example.vaivan.data.local.dao.RotaDao
import com.example.vaivan.data.local.entities.ParadaRotaEntity
import com.example.vaivan.data.local.entities.RotaEntity
import com.example.vaivan.data.remote.routes.GoogleRoutesClient
import com.example.vaivan.data.remote.routes.PontoRota
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Firestore = fonte da verdade
 * Room = cache local das rotas que pertencem ao motorista
 *
 * Buscas temporárias, como "rotas disponíveis", podem consultar
 * diretamente o Firestore e não precisam passar pelo Room.
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

    private var listenerRotas: ListenerRegistration? = null

    private val scope =
        CoroutineScope(Dispatchers.IO)


    // =========================================================
    // OBSERVAÇÃO DO ROOM
    // =========================================================

    fun observarRotasPorMotorista(
        motoristaId: String
    ): Flow<List<RotaEntity>> {

        return rotaDao.getByMotorista(motoristaId)
    }


    fun observarRotaPorId(
        rotaId: String
    ): Flow<RotaEntity?> {

        return rotaDao.getById(rotaId)
    }


    fun observarParadas(
        rotaId: String
    ): Flow<List<ParadaRotaEntity>> {

        return paradaRotaDao.getByRota(rotaId)
    }


    // =========================================================
    // SINCRONIZAÇÃO FIRESTORE → ROOM
    // =========================================================

    fun iniciarSincronizacao(
        motoristaId: String
    ) {

        listenerRotas?.remove()

        listenerRotas =
            collectionRotas
                .whereEqualTo(
                    "motoristaId",
                    motoristaId
                )
                .addSnapshotListener { snapshot, error ->

                    if (error != null || snapshot == null) {
                        return@addSnapshotListener
                    }

                    scope.launch {

                        snapshot.documentChanges.forEach { change ->

                            when (change.type) {

                                com.google.firebase.firestore.DocumentChange.Type.ADDED,
                                com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {

                                    val rota =
                                        change.document
                                            .toObject(RotaEntity::class.java)
                                            .copy(
                                                id = change.document.id,
                                                lastUpdated =
                                                    System.currentTimeMillis()
                                            )

                                    rotaDao.upsert(rota)
                                }

                                com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {

                                    rotaDao.deleteById(
                                        change.document.id
                                    )
                                }
                            }
                        }
                    }
                }
    }


    fun pararSincronizacao() {

        listenerRotas?.remove()

        listenerRotas = null
    }


    suspend fun sincronizarRotasPorMotorista(
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
                    .toObject(RotaEntity::class.java)
                    ?.copy(
                        id = document.id,
                        lastUpdated =
                            System.currentTimeMillis()
                    )
            }

        rotaDao.upsertAll(rotas)
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
                diasSemana = diasSemana.joinToString(","),

                capacidadeTotal = capacidadeTotal,
                vagasOcupadas = 0,

                distanciaMetros =
                    resultadoInicial?.distanciaMetros ?: 0,

                duracaoSegundos =
                    resultadoInicial?.duracaoSegundos ?: 0,

                duracaoSemTrafegoSegundos =
                    resultadoInicial?.duracaoSemTrafegoSegundos ?: 0,

                polylineEncoded =
                    resultadoInicial?.polylineEncoded ?: "",

                status = "ATIVA",

                calculadoEm = agora,
                lastUpdated = agora
            )

        // Firestore = fonte da verdade
        documentReference
            .set(rota)
            .await()

        // Room = cache local
        rotaDao.upsert(rota)

        rota
    }


    // =========================================================
    // BUSCA TEMPORÁRIA DE ROTAS
    // =========================================================

    suspend fun buscarRotasDisponiveis(
        textoBusca: String,
        turno: String?
    ): List<RotaEntity> = withContext(Dispatchers.IO) {

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
                        .toObject(RotaEntity::class.java)
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
    // OPERAÇÕES NECESSÁRIAS PARA O USE CASE
    // =========================================================

    suspend fun buscarParadasPorPassageiro(
        passageiroId: String
    ): List<ParadaRotaEntity> {

        return firestore
            .collection("paradas_rota")
            .whereEqualTo("passageiroId", passageiroId)
            .get()
            .await()
            .documents
            .mapNotNull { document ->

                document.toObject(
                    ParadaRotaEntity::class.java
                )?.copy(
                    id = document.id
                )
            }
    }

    suspend fun buscarRotaNoFirestore(
        rotaId: String
    ): RotaEntity {

        val snapshot =
            collectionRotas
                .document(rotaId)
                .get()
                .await()

        if (!snapshot.exists()) {

            throw IllegalStateException(
                "Rota não encontrada."
            )
        }

        return snapshot
            .toObject(RotaEntity::class.java)
            ?.copy(
                id = snapshot.id
            )
            ?: throw IllegalStateException(
                "Não foi possível ler a rota."
            )
    }


    suspend fun buscarParadasNoFirestore(
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


    suspend fun salvarRotaRecalculada(
        rota: RotaEntity
    ) {

        collectionRotas
            .document(rota.id)
            .set(rota)
            .await()

        rotaDao.upsert(rota)
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