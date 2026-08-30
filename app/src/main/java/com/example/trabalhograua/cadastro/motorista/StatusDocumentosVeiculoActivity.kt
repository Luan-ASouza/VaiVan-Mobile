package com.example.trabalhograua.cadastro.motorista

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.trabalhograua.R
import com.example.trabalhograua.data.local.VaivanDatabase
import com.example.trabalhograua.data.repository.DocumentoRepository
import kotlinx.coroutines.launch

class StatusDocumentosVeiculoActivity : AppCompatActivity() {

    private lateinit var chipCrlv: TextView
    private lateinit var chipAutorizacao: TextView
    private lateinit var documentoRepository: DocumentoRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_status_documentos_veiculo)

        chipCrlv = findViewById(R.id.chipStatusCrlv)
        chipAutorizacao = findViewById(R.id.chipStatusAutorizacao)

        documentoRepository = DocumentoRepository(VaivanDatabase.getInstance(this).documentoDao())

        val veiculoId = intent.getStringExtra("veiculoId") ?: return

        documentoRepository.iniciarSincronizacao()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                documentoRepository.observarPorVeiculo(veiculoId).collect { documentos ->
                    documentos.find { it.tipo == "CRLV" }?.let {
                        aplicarStatus(chipCrlv, it.statusValidacao)
                    }
                    documentos.find { it.tipo == "AUTORIZACAO_TRANSPORTE_ESCOLAR" }?.let {
                        aplicarStatus(chipAutorizacao, it.statusValidacao)
                    }
                }
            }
        }
    }

    private fun aplicarStatus(chip: TextView, status: String) {
        when (status) {
            "APROVADO" -> {
                chip.text = "Aprovado"
                chip.backgroundTintList = getColorStateList(R.color.green)
            }
            "REJEITADO" -> {
                chip.text = "Rejeitado"
                chip.backgroundTintList = getColorStateList(R.color.red)
            }
            else -> {
                chip.text = "Em análise"
                chip.backgroundTintList = getColorStateList(R.color.orange)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        documentoRepository.pararSincronizacao()
    }
}