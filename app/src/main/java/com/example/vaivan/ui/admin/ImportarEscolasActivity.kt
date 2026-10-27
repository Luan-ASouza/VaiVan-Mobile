package com.example.vaivan.ui.admin

import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.vaivan.R
import com.example.vaivan.core.util.TextoUtil
import com.example.vaivan.data.models.Escola
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * TELA TEMPORÁRIA: importa as escolas de Caxias do Sul (INEP) para o Firestore.
 *
 * - Lê res/raw/escolas_caxias.tsv
 * - Para as escolas sem coordenada, descobre pelo endereço (Geocoder do Android)
 * - Grava em escolas/{codigoInep}. Quem já existe NÃO é sobrescrito
 *   (assim correções manuais feitas no console do Firebase são preservadas).
 *
 * Depois de importar, esta tela e o arquivo .tsv podem ser apagados.
 */
class ImportarEscolasActivity : AppCompatActivity() {

    private lateinit var txtStatus: TextView
    private lateinit var progress: ProgressBar
    private lateinit var btnIniciar: Button

    private val colecao by lazy {
        FirebaseFirestore.getInstance().collection("escolas")
    }

    private data class LinhaEscola(
        val codigoInep: String,
        val nome: String,
        val nomeOriginal: String,
        val endereco: String,
        val cep: String,
        val telefone: String,
        val dependencia: String,
        val latitude: Double?,
        val longitude: Double?
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_importar_escolas)

        txtStatus = findViewById(R.id.txtStatusImportacao)
        progress = findViewById(R.id.progressImportacao)
        btnIniciar = findViewById(R.id.btnIniciarImportacao)

        btnIniciar.setOnClickListener { iniciar() }
    }

    // ---------------------------------------------------------
    // FLUXO PRINCIPAL
    // ---------------------------------------------------------

    private fun iniciar() {

        if (FirebaseAuth.getInstance().currentUser == null) {
            status("Você precisa estar logado no app. Entre com qualquer conta e volte aqui.")
            return
        }

        btnIniciar.isEnabled = false

        lifecycleScope.launch {
            try {
                val linhas = lerArquivo()
                status("Arquivo lido: ${linhas.size} escolas.\nVerificando as que já existem no Firestore...")

                val existentes = withContext(Dispatchers.IO) {
                    colecao.get().await().documents.map { it.id }.toSet()
                }

                val novas = linhas.filter { it.codigoInep !in existentes }
                val puladas = linhas.size - novas.size

                if (novas.isEmpty()) {
                    status("Nada a fazer: todas as ${linhas.size} escolas já estão no Firestore.")
                    return@launch
                }

                // 1) Descobrir coordenadas das escolas que vieram sem
                val semCoordenada = novas.filter { it.latitude == null || it.longitude == null }
                val geocodificadas = mutableMapOf<String, LatLng>()
                val falhas = mutableListOf<String>()

                progress.max = semCoordenada.size.coerceAtLeast(1)
                progress.progress = 0

                semCoordenada.forEachIndexed { indice, linha ->
                    status(
                        "Buscando coordenadas pelo endereço...\n" +
                                "${indice + 1} de ${semCoordenada.size}\n${linha.nome}"
                    )

                    val ponto = geocodificarEscola(linha)

                    if (ponto != null) {
                        geocodificadas[linha.codigoInep] = ponto
                    } else {
                        falhas.add(linha.nome)
                        Log.w("IMPORTACAO", "Sem coordenada: ${linha.codigoInep} ${linha.nome}")
                    }

                    progress.progress = indice + 1
                    delay(120)
                }

                // 2) Gravar no Firestore (em lotes)
                status("Gravando ${novas.size} escolas no Firestore...")

                val agora = System.currentTimeMillis()

                novas.chunked(300).forEach { lote ->
                    withContext(Dispatchers.IO) {
                        val batch = FirebaseFirestore.getInstance().batch()

                        lote.forEach { l ->
                            val ponto = geocodificadas[l.codigoInep]

                            val lat: Double
                            val lng: Double
                            val fonte: String

                            if (l.latitude != null && l.longitude != null) {
                                lat = l.latitude
                                lng = l.longitude
                                fonte = "INEP"
                            } else if (ponto != null) {
                                lat = ponto.latitude
                                lng = ponto.longitude
                                fonte = "GEOCODER"
                            } else {
                                lat = 0.0
                                lng = 0.0
                                fonte = "NENHUMA"
                            }

                            val escola = Escola(
                                id = l.codigoInep,
                                nome = l.nome,
                                nomeNormalizado = TextoUtil.normalizar(l.nome),
                                endereco = montarEndereco(l),
                                cidade = "Caxias do Sul",
                                latitude = lat,
                                longitude = lng,
                                criadoPor = "IMPORTACAO_INEP",
                                criadoEm = agora,
                                codigoInep = l.codigoInep,
                                nomeOriginal = l.nomeOriginal,
                                cep = l.cep,
                                telefone = l.telefone,
                                dependencia = l.dependencia,
                                fonteCoordenadas = fonte
                            )

                            batch.set(colecao.document(l.codigoInep), escola)
                        }

                        batch.commit().await()
                    }
                }

                // 3) Resumo
                val doInep = novas.size - semCoordenada.size
                val resumo = StringBuilder()
                resumo.append("CONCLUÍDO\n\n")
                resumo.append("Escolas gravadas: ${novas.size}\n")
                resumo.append("Já existiam (puladas): $puladas\n\n")
                resumo.append("Coordenadas do INEP: $doInep\n")
                resumo.append("Descobertas pelo endereço: ${geocodificadas.size}\n")
                resumo.append("Sem coordenada: ${falhas.size}\n")

                if (falhas.isNotEmpty()) {
                    resumo.append("\nEscolas SEM coordenada (precisam de ajuste manual no Firebase):\n")
                    falhas.forEach { resumo.append("• $it\n") }
                }

                status(resumo.toString())

            } catch (e: Exception) {
                Log.e("IMPORTACAO", "Erro na importação", e)
                status("ERRO: ${e.message}\n\nSe for PERMISSION_DENIED, as regras do Firestore não permitem gravar em 'escolas'.")
            } finally {
                btnIniciar.isEnabled = true
            }
        }
    }

    // ---------------------------------------------------------
    // LEITURA DO ARQUIVO
    // ---------------------------------------------------------

    private suspend fun lerArquivo(): List<LinhaEscola> = withContext(Dispatchers.IO) {

        val linhas = resources.openRawResource(R.raw.escolas_caxias)
            .bufferedReader(Charsets.UTF_8)
            .readLines()
            .filter { it.isNotBlank() }

        // A primeira linha é o cabeçalho
        linhas.drop(1).mapNotNull { linha ->
            val c = linha.split("\t")
            if (c.size < 10) return@mapNotNull null

            LinhaEscola(
                codigoInep = c[0].trim(),
                nome = c[1].trim(),
                nomeOriginal = c[2].trim(),
                endereco = c[3].trim(),
                cep = c[4].trim(),
                telefone = c[5].trim(),
                dependencia = c[6].trim(),
                latitude = c[8].trim().toDoubleOrNull(),
                longitude = c[9].trim().toDoubleOrNull()
            )
        }
    }

    private fun montarEndereco(l: LinhaEscola): String =
        if (l.cep.isNotBlank()) "${l.endereco} - CEP ${l.cep}" else l.endereco

    // ---------------------------------------------------------
    // GEOCODIFICAÇÃO (endereço -> coordenada)
    // ---------------------------------------------------------

    private suspend fun geocodificarEscola(l: LinhaEscola): LatLng? {

        // 1ª tentativa: endereço completo
        val porEndereco = geocodificar("${l.endereco}, Caxias do Sul, RS, ${l.cep}, Brasil")
        if (porEndereco != null) return porEndereco

        // 2ª tentativa: só o CEP (aproxima a rua)
        if (l.cep.isNotBlank()) {
            return geocodificar("CEP ${l.cep}, Caxias do Sul, RS, Brasil")
        }

        return null
    }

    private suspend fun geocodificar(consulta: String): LatLng? {

        val geocoder = Geocoder(this, Locale("pt", "BR"))

        val resultado: List<Address> = try {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

                suspendCancellableCoroutine<List<Address>> { continuacao ->
                    geocoder.getFromLocationName(
                        consulta,
                        1,
                        object : Geocoder.GeocodeListener {

                            override fun onGeocode(addresses: MutableList<Address>) {
                                continuacao.resume(addresses)
                            }

                            override fun onError(errorMessage: String?) {
                                continuacao.resume(emptyList())
                            }
                        }
                    )
                }

            } else {

                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(consulta, 1) ?: emptyList()
                }
            }

        } catch (e: Exception) {
            emptyList()
        }

        val endereco = resultado.firstOrNull() ?: return null
        val ponto = LatLng(endereco.latitude, endereco.longitude)

        // Descarta resultados fora de Caxias do Sul (geocoder errou a cidade)
        return if (dentroDeCaxias(ponto)) ponto else null
    }

    private fun dentroDeCaxias(p: LatLng): Boolean =
        p.latitude in -29.45..-28.80 && p.longitude in -51.40..-50.75

    // ---------------------------------------------------------
    // AUXILIAR
    // ---------------------------------------------------------

    private fun status(texto: String) {
        runOnUiThread { txtStatus.text = texto }
    }
}