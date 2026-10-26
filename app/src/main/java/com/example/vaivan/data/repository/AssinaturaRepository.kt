package com.example.vaivan.data.repository

import android.util.Log
import com.example.vaivan.data.models.Assinatura
import com.example.vaivan.data.models.Pagamento
import com.example.vaivan.data.models.Plano
import com.example.vaivan.data.models.PlanoCatalogo
import com.example.vaivan.data.models.StatusAssinatura
import com.example.vaivan.data.models.StatusPagamento
import com.example.vaivan.data.models.TipoPagamento
import com.example.vaivan.domain.usecase.assinatura.AssinaturaPolicy
import com.example.vaivan.domain.usecase.assinatura.AssinaturaPolicy.Estado
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Lançada quando a assinatura do motorista não permite a ação pedida. */
class AssinaturaBloqueadaException(message: String) : IllegalStateException(message)

/**
 * Planos e assinaturas dos motoristas.
 *
 * Diferente dos outros repositórios, este lê direto do Firestore (sem cópia no Room): o dado
 * decide permissões e precisa ser o do servidor. O SDK do Firestore já guarda um cache
 * offline próprio.
 *
 * ATENÇÃO (ambiente acadêmico): o pagamento é SIMULADO. Num sistema real, o gateway
 * (PIX/cartão) avisaria um servidor (webhook) e só o servidor mudaria o status da assinatura.
 * Aqui o próprio app confirma o pagamento, e as regras do Firestore refletem isso.
 */
class AssinaturaRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val colAssinaturas = firestore.collection("assinaturas")
    private val colPagamentos = firestore.collection("pagamentos")
    private val colPlanos = firestore.collection("planos")
    private val colRotas = firestore.collection("rotas")

    sealed class ResultadoTroca {
        /** Troca já aplicada (upgrade sem diferença a pagar). */
        object Aplicada : ResultadoTroca()

        /** Upgrade: foi criada uma cobrança proporcional que precisa ser paga. */
        class CobrancaCriada(val pagamento: Pagamento) : ResultadoTroca()

        /** Downgrade agendado para a próxima renovação. */
        class Agendada(val paraQuando: Long) : ResultadoTroca()

        /** O motorista desistiu de um downgrade que estava agendado. */
        object AgendamentoRemovido : ResultadoTroca()
    }

    // =========================================================
    // OBSERVAÇÃO (tempo real)
    // =========================================================

    fun observarAssinatura(motoristaId: String): Flow<Assinatura?> =
        callbackFlow<Assinatura?> {
            val registro = colAssinaturas.document(motoristaId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Erro ao observar assinatura", error)
                        return@addSnapshotListener
                    }
                    val assinatura = if (snapshot != null && snapshot.exists()) {
                        snapshot.toObject(Assinatura::class.java)
                    } else {
                        null
                    }
                    trySend(assinatura)
                }
            awaitClose { registro.remove() }
        }

    fun observarPagamentos(motoristaId: String): Flow<List<Pagamento>> =
        callbackFlow<List<Pagamento>> {
            val registro = colPagamentos.whereEqualTo("motoristaId", motoristaId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) {
                        Log.e(TAG, "Erro ao observar pagamentos", error)
                        return@addSnapshotListener
                    }
                    val lista = snapshot.documents
                        .mapNotNull { doc ->
                            doc.toObject(Pagamento::class.java)?.copy(id = doc.id)
                        }
                        .sortedByDescending { it.criadoEm }
                    trySend(lista)
                }
            awaitClose { registro.remove() }
        }

    /** Planos do catálogo; se não houver nenhum no Firestore, usa o catálogo padrão do app. */
    fun observarPlanos(): Flow<List<Plano>> =
        callbackFlow<List<Plano>> {
            trySend(PlanoCatalogo.padrao)

            val registro = colPlanos.whereEqualTo("ativo", true)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) {
                        trySend(PlanoCatalogo.padrao)
                        return@addSnapshotListener
                    }
                    val lista = snapshot.documents
                        .mapNotNull { doc ->
                            doc.toObject(Plano::class.java)?.copy(id = doc.id)
                        }
                        .sortedBy { it.ordem }
                    trySend(if (lista.isEmpty()) PlanoCatalogo.padrao else lista)
                }
            awaitClose { registro.remove() }
        }

    // =========================================================
    // CONSULTAS
    // =========================================================

    suspend fun consultarAssinatura(motoristaId: String): Assinatura? {
        val doc = colAssinaturas.document(motoristaId).get().await()
        return if (doc.exists()) doc.toObject(Assinatura::class.java) else null
    }

    suspend fun obterPlano(planoId: String): Plano {
        val snapshot = try {
            colPlanos.document(planoId).get().await()
        } catch (e: Exception) {
            null
        }

        if (snapshot != null && snapshot.exists()) {
            val plano = snapshot.toObject(Plano::class.java)
            if (plano != null) return plano.copy(id = snapshot.id)
        }

        return PlanoCatalogo.porId(planoId)
            ?: throw IllegalStateException("Plano não encontrado.")
    }

    suspend fun contarRotasAtivas(motoristaId: String): Int =
        colRotas
            .whereEqualTo("motoristaId", motoristaId)
            .whereEqualTo("status", "ATIVA")
            .get()
            .await()
            .size()

    // =========================================================
    // VERIFICAÇÕES (usadas por criar rota, aceitar passageiro e busca)
    // =========================================================

    suspend fun exigirPodeCriarRota(motoristaId: String) {
        val assinatura = consultarAssinatura(motoristaId)
        val rotasAtivas = contarRotasAtivas(motoristaId)

        val decisao = AssinaturaPolicy.verificarCriarRota(
            assinatura, rotasAtivas, System.currentTimeMillis()
        )
        if (!decisao.permitido) throw AssinaturaBloqueadaException(decisao.motivo)
    }

    suspend fun exigirPodeAceitarPassageiro(motoristaId: String) {
        val assinatura = consultarAssinatura(motoristaId)

        val decisao = AssinaturaPolicy.verificarAceitarPassageiro(
            assinatura, System.currentTimeMillis()
        )
        if (!decisao.permitido) throw AssinaturaBloqueadaException(decisao.motivo)
    }

    /**
     * Dos motoristas informados, devolve apenas os que podem receber passageiros.
     * Usado para esconder da busca as rotas de motoristas inadimplentes ou sem plano.
     */
    suspend fun motoristasOperacionais(ids: Collection<String>): Set<String> = coroutineScope {
        val agora = System.currentTimeMillis()

        ids.filter { it.isNotBlank() }
            .toSet()
            .map { id ->
                async {
                    val assinatura = try {
                        consultarAssinatura(id)
                    } catch (e: Exception) {
                        Log.w(TAG, "Não foi possível ler a assinatura de $id", e)
                        null
                    }
                    if (AssinaturaPolicy.operacional(assinatura, agora)) id else null
                }
            }
            .awaitAll()
            .filterNotNull()
            .toSet()
    }

    // =========================================================
    // CONTRATAR
    // =========================================================

    /** Cria a assinatura (pendente) e a primeira cobrança. Devolve a cobrança a ser paga. */
    suspend fun contratarPlano(motoristaId: String, plano: Plano): Pagamento {
        val agora = System.currentTimeMillis()
        val atual = consultarAssinatura(motoristaId)

        when (AssinaturaPolicy.estado(atual, agora)) {
            Estado.ATIVA, Estado.EM_CARENCIA ->
                throw AssinaturaBloqueadaException("Você já possui uma assinatura ativa.")
            Estado.SUSPENSA ->
                throw AssinaturaBloqueadaException(
                    "Sua assinatura está suspensa. Pague a cobrança pendente para regularizar."
                )
            else -> Unit
        }

        val pendentes = cobrancasPendentes(motoristaId, tipo = null)

        val pagamentoId = colPagamentos.document().id
        val pagamento = Pagamento(
            id = pagamentoId,
            motoristaId = motoristaId,
            assinaturaId = motoristaId,
            tipo = TipoPagamento.MENSALIDADE,
            planoId = plano.id,
            planoNome = plano.nome,
            descricao = "Primeira mensalidade – ${plano.nome}",
            valorCentavos = plano.valorCentavos,
            status = StatusPagamento.PENDENTE,
            vencimento = agora,
            criadoEm = agora
        )

        val assinatura = Assinatura(
            motoristaId = motoristaId,
            planoId = plano.id,
            planoNome = plano.nome,
            valorCentavos = plano.valorCentavos,
            limiteRotas = plano.limiteRotas,
            ilimitado = plano.ilimitado,
            status = StatusAssinatura.PENDENTE,
            cicloInicio = 0L,
            proximaCobranca = 0L,
            cancelamentoAgendado = false,
            planoAgendadoId = null,
            criadoEm = if (atual != null && atual.criadoEm > 0L) atual.criadoEm else agora,
            atualizadoEm = agora
        )

        val batch = firestore.batch()
        pendentes.forEach { batch.update(it, "status", StatusPagamento.CANCELADO) }
        batch.set(colAssinaturas.document(motoristaId), assinatura)
        batch.set(colPagamentos.document(pagamentoId), pagamento)
        batch.set(
            novoEvento(motoristaId),
            evento("CONTRATADA", atual?.planoId, plano.id, plano.valorCentavos, agora)
        )
        batch.commit().await()

        return pagamento
    }

    // =========================================================
    // PAGAMENTO (SIMULADO)
    // =========================================================

    /** Confirma o pagamento simulado e aplica o efeito na assinatura. */
    suspend fun confirmarPagamentoSimulado(pagamento: Pagamento) {
        val agora = System.currentTimeMillis()

        val pagamentoRef = colPagamentos.document(pagamento.id)
        val atualNoServidor = pagamentoRef.get().await()
        if (atualNoServidor.getString("status") != StatusPagamento.PENDENTE) {
            throw IllegalStateException("Esta cobrança não está mais pendente.")
        }

        val assinatura = consultarAssinatura(pagamento.motoristaId)
            ?: throw IllegalStateException("Assinatura não encontrada.")
        val plano = obterPlano(pagamento.planoId)
        val assinaturaRef = colAssinaturas.document(pagamento.motoristaId)

        val batch = firestore.batch()

        batch.update(
            pagamentoRef,
            mapOf(
                "status" to StatusPagamento.PAGO,
                "pagoEm" to agora,
                "forma" to "PIX (simulado)"
            )
        )

        if (pagamento.tipo == TipoPagamento.UPGRADE) {
            // Troca o plano na hora; o ciclo e o vencimento continuam os mesmos.
            batch.update(
                assinaturaRef,
                mapOf(
                    "planoId" to plano.id,
                    "planoNome" to plano.nome,
                    "valorCentavos" to plano.valorCentavos,
                    "limiteRotas" to plano.limiteRotas,
                    "ilimitado" to plano.ilimitado,
                    "planoAgendadoId" to null,
                    "atualizadoEm" to agora
                )
            )
            batch.set(
                novoEvento(pagamento.motoristaId),
                evento("UPGRADE", assinatura.planoId, plano.id, pagamento.valorCentavos, agora)
            )
        } else {
            val (inicio, vencimento) = AssinaturaPolicy.novoCiclo(assinatura, agora)

            batch.update(
                assinaturaRef,
                mapOf(
                    "planoId" to plano.id,
                    "planoNome" to plano.nome,
                    "valorCentavos" to plano.valorCentavos,
                    "limiteRotas" to plano.limiteRotas,
                    "ilimitado" to plano.ilimitado,
                    "status" to StatusAssinatura.ATIVA,
                    "cicloInicio" to inicio,
                    "proximaCobranca" to vencimento,
                    "cancelamentoAgendado" to false,
                    "planoAgendadoId" to null,
                    "atualizadoEm" to agora
                )
            )

            val tipoEvento =
                if (assinatura.status == StatusAssinatura.PENDENTE) "ATIVADA" else "RENOVADA"
            batch.set(
                novoEvento(pagamento.motoristaId),
                evento(tipoEvento, assinatura.planoId, plano.id, pagamento.valorCentavos, agora)
            )
        }

        batch.commit().await()
    }

    // =========================================================
    // RENOVAÇÃO
    // =========================================================

    /**
     * Emite a cobrança da renovação quando o vencimento se aproxima (ou já passou).
     * Pode ser chamada quantas vezes quiser: a cobrança de um mesmo vencimento só é criada uma vez.
     */
    suspend fun gerarCobrancaDeRenovacaoSeNecessario(motoristaId: String) {
        val agora = System.currentTimeMillis()
        val assinatura = consultarAssinatura(motoristaId)

        if (assinatura == null || !AssinaturaPolicy.deveGerarCobrancaDeRenovacao(assinatura, agora)) {
            return
        }

        val referencia = AssinaturaPolicy.referenciaDoVencimento(assinatura.proximaCobranca)
        val cobrancaId = "${motoristaId}_$referencia"

        val existentes = colPagamentos
            .whereEqualTo("motoristaId", motoristaId)
            .get()
            .await()
            .documents

        val jaExiste = existentes.any {
            it.id == cobrancaId && it.getString("status") != StatusPagamento.CANCELADO
        }
        if (jaExiste) return

        // Se há downgrade agendado, a renovação já vem com o plano (e o preço) novos.
        val alvoId = assinatura.planoAgendadoId ?: assinatura.planoId
        val planoAlvo = if (alvoId == assinatura.planoId) null else obterPlano(alvoId)

        val nomePlano = planoAlvo?.nome ?: assinatura.planoNome
        val valor = planoAlvo?.valorCentavos ?: assinatura.valorCentavos
        val dataTexto = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
            .format(Date(assinatura.proximaCobranca))

        val cobranca = Pagamento(
            id = cobrancaId,
            motoristaId = motoristaId,
            assinaturaId = motoristaId,
            tipo = TipoPagamento.MENSALIDADE,
            planoId = alvoId,
            planoNome = nomePlano,
            descricao = "Mensalidade – $nomePlano (vence em $dataTexto)",
            valorCentavos = valor,
            status = StatusPagamento.PENDENTE,
            vencimento = assinatura.proximaCobranca,
            criadoEm = agora
        )

        colPagamentos.document(cobrancaId).set(cobranca).await()
    }

    // =========================================================
    // TROCA DE PLANO
    // =========================================================

    suspend fun solicitarTrocaDePlano(motoristaId: String, novo: Plano): ResultadoTroca {
        val agora = System.currentTimeMillis()

        val assinatura = consultarAssinatura(motoristaId)
            ?: throw AssinaturaBloqueadaException("Você ainda não tem uma assinatura.")

        if (AssinaturaPolicy.estado(assinatura, agora) != Estado.ATIVA) {
            throw AssinaturaBloqueadaException("Regularize sua assinatura antes de trocar de plano.")
        }
        if (assinatura.cancelamentoAgendado) {
            throw AssinaturaBloqueadaException("Desfaça o cancelamento antes de trocar de plano.")
        }

        val assinaturaRef = colAssinaturas.document(motoristaId)

        // Clicou no plano que já tem.
        if (novo.id == assinatura.planoId) {
            if (assinatura.planoAgendadoId != null) {
                assinaturaRef.update(
                    mapOf("planoAgendadoId" to null, "atualizadoEm" to agora)
                ).await()
                return ResultadoTroca.AgendamentoRemovido
            }
            throw AssinaturaBloqueadaException("Este já é o seu plano atual.")
        }

        // UPGRADE: vale na hora, paga-se só a diferença proporcional.
        if (novo.valorCentavos > assinatura.valorCentavos) {
            val proRata = AssinaturaPolicy.proRataCentavos(
                assinatura.valorCentavos, novo.valorCentavos, assinatura.proximaCobranca, agora
            )

            if (proRata <= 0L) {
                assinaturaRef.update(
                    mapOf(
                        "planoId" to novo.id,
                        "planoNome" to novo.nome,
                        "valorCentavos" to novo.valorCentavos,
                        "limiteRotas" to novo.limiteRotas,
                        "ilimitado" to novo.ilimitado,
                        "planoAgendadoId" to null,
                        "atualizadoEm" to agora
                    )
                ).await()
                return ResultadoTroca.Aplicada
            }

            val pendentes = cobrancasPendentes(motoristaId, TipoPagamento.UPGRADE)
            val pagamentoId = colPagamentos.document().id
            val pagamento = Pagamento(
                id = pagamentoId,
                motoristaId = motoristaId,
                assinaturaId = motoristaId,
                tipo = TipoPagamento.UPGRADE,
                planoId = novo.id,
                planoNome = novo.nome,
                descricao = "Upgrade para ${novo.nome} (valor proporcional)",
                valorCentavos = proRata,
                status = StatusPagamento.PENDENTE,
                vencimento = agora,
                criadoEm = agora
            )

            val batch = firestore.batch()
            pendentes.forEach { batch.update(it, "status", StatusPagamento.CANCELADO) }
            batch.set(colPagamentos.document(pagamentoId), pagamento)
            batch.update(assinaturaRef, mapOf("planoAgendadoId" to null, "atualizadoEm" to agora))
            batch.commit().await()

            return ResultadoTroca.CobrancaCriada(pagamento)
        }

        // DOWNGRADE: vale só na próxima renovação.
        val rotasAtivas = contarRotasAtivas(motoristaId)
        if (!novo.ilimitado && rotasAtivas > novo.limiteRotas) {
            throw AssinaturaBloqueadaException(
                "Para mudar para o ${novo.nome}, reduza suas rotas ativas para " +
                        "${novo.limiteRotas} (hoje você tem $rotasAtivas)."
            )
        }

        val batch = firestore.batch()
        batch.update(assinaturaRef, mapOf("planoAgendadoId" to novo.id, "atualizadoEm" to agora))
        batch.set(
            novoEvento(motoristaId),
            evento("DOWNGRADE_AGENDADO", assinatura.planoId, novo.id, novo.valorCentavos, agora)
        )
        batch.commit().await()

        return ResultadoTroca.Agendada(assinatura.proximaCobranca)
    }

    // =========================================================
    // CANCELAMENTO
    // =========================================================

    /**
     * Cancela a assinatura. Se estiver em dia, o acesso continua até o fim do ciclo já pago
     * (devolve a data final). Se não estiver, cancela na hora (devolve null).
     */
    suspend fun cancelarAssinatura(motoristaId: String): Long? {
        val agora = System.currentTimeMillis()

        val assinatura = consultarAssinatura(motoristaId)
            ?: throw AssinaturaBloqueadaException("Você ainda não tem uma assinatura.")

        val estado = AssinaturaPolicy.estado(assinatura, agora)
        val pendentes = cobrancasPendentes(motoristaId, tipo = null)
        val assinaturaRef = colAssinaturas.document(motoristaId)

        val batch = firestore.batch()
        pendentes.forEach { batch.update(it, "status", StatusPagamento.CANCELADO) }

        val fimDaVigencia: Long?

        when (estado) {
            Estado.ATIVA -> {
                if (assinatura.cancelamentoAgendado) {
                    throw AssinaturaBloqueadaException("O cancelamento já está agendado.")
                }
                batch.update(
                    assinaturaRef,
                    mapOf(
                        "cancelamentoAgendado" to true,
                        "planoAgendadoId" to null,
                        "atualizadoEm" to agora
                    )
                )
                fimDaVigencia = assinatura.proximaCobranca
            }
            Estado.AGUARDANDO_PAGAMENTO, Estado.EM_CARENCIA, Estado.SUSPENSA -> {
                batch.update(
                    assinaturaRef,
                    mapOf(
                        "status" to StatusAssinatura.CANCELADA,
                        "planoAgendadoId" to null,
                        "atualizadoEm" to agora
                    )
                )
                fimDaVigencia = null
            }
            else -> throw AssinaturaBloqueadaException("Sua assinatura já não está ativa.")
        }

        batch.set(
            novoEvento(motoristaId),
            evento("CANCELAMENTO", assinatura.planoId, null, 0L, agora)
        )
        batch.commit().await()

        return fimDaVigencia
    }

    suspend fun desfazerCancelamento(motoristaId: String) {
        val agora = System.currentTimeMillis()

        val assinatura = consultarAssinatura(motoristaId)
            ?: throw AssinaturaBloqueadaException("Você ainda não tem uma assinatura.")

        if (AssinaturaPolicy.estado(assinatura, agora) != Estado.ATIVA ||
            !assinatura.cancelamentoAgendado
        ) {
            throw AssinaturaBloqueadaException("Não há cancelamento para desfazer.")
        }

        colAssinaturas.document(motoristaId)
            .update(mapOf("cancelamentoAgendado" to false, "atualizadoEm" to agora))
            .await()

        // A cobrança da próxima renovação foi cancelada junto; emite de novo, se for o caso.
        gerarCobrancaDeRenovacaoSeNecessario(motoristaId)
    }

    // =========================================================
    // AUXILIARES
    // =========================================================

    private suspend fun cobrancasPendentes(
        motoristaId: String,
        tipo: String?
    ): List<DocumentReference> =
        colPagamentos
            .whereEqualTo("motoristaId", motoristaId)
            .get()
            .await()
            .documents
            .filter {
                it.getString("status") == StatusPagamento.PENDENTE &&
                        (tipo == null || it.getString("tipo") == tipo)
            }
            .map { it.reference }

    private fun novoEvento(motoristaId: String): DocumentReference =
        colAssinaturas.document(motoristaId).collection("historico").document()

    private fun evento(
        tipo: String,
        de: String?,
        para: String?,
        valorCentavos: Long,
        agora: Long
    ): Map<String, Any?> = mapOf(
        "evento" to tipo,
        "de" to de,
        "para" to para,
        "valorCentavos" to valorCentavos,
        "em" to agora
    )

    private companion object {
        const val TAG = "AssinaturaRepository"
    }
}