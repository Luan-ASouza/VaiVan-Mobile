package com.example.trabalhograua.cadastro.motorista.documentos

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.trabalhograua.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class EnvioComprovanteResidenciaActivity : AppCompatActivity() {

    private lateinit var storage: FirebaseStorage
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var uriComprovante: Uri? = null

    // Launcher para selecionar o comprovante
    private val launcherComprovante =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->

            uri?.let {

                uriComprovante = it

                findViewById<TextView>(
                    R.id.txtNomeArquivoComprovante
                ).text = "Comprovante selecionado"

                findViewById<Button>(
                    R.id.btnEnviarArquivoComprovante
                ).text = "Editar envio"

                findViewById<Button>(
                    R.id.btnEnviarArquivoComprovante
                ).setTextColor(
                    ContextCompat.getColor(this, R.color.white)
                )

                findViewById<Button>(
                    R.id.btnEnviarArquivoComprovante
                ).background =
                    getDrawable(R.drawable.btn_gray_filled)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_envio_comprovante_residencia)

        storage = FirebaseStorage.getInstance()
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val btnComprovante = findViewById<Button>(
            R.id.btnEnviarArquivoComprovante
        )

        val btnContinuar = findViewById<Button>(
            R.id.btnEnviarContinuarComprovante
        )

        val scrollView = findViewById<ScrollView>(
            R.id.scrollComprovanteResidencia
        )

        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { view, insets ->

            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            view.setPadding(
                view.paddingLeft,
                bars.top,
                view.paddingRight,
                bars.bottom
            )

            insets
        }

        // Selecionar comprovante
        btnComprovante.setOnClickListener {
            launcherComprovante.launch("image/*")
        }

        // Enviar
        btnContinuar.setOnClickListener {

            if (uriComprovante != null) {
                uploadComprovante()
            } else {
                Toast.makeText(
                    this,
                    "Selecione o comprovante de residência",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun uploadComprovante() {

        val userId = auth.currentUser?.uid

        if (userId == null) {
            Toast.makeText(
                this,
                "Usuário não encontrado",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val refComprovante = storage.reference
            .child("documentos/ComprovanteResidencia/$userId/comprovante.jpg")

        refComprovante
            .putFile(uriComprovante!!)
            .addOnSuccessListener {

                Toast.makeText(
                    this,
                    "Comprovante enviado!",
                    Toast.LENGTH_SHORT
                ).show()

                refComprovante.downloadUrl
                    .addOnSuccessListener { url ->

                        salvarDadosNoFirestore(
                            url.toString()
                        )
                    }
                    .addOnFailureListener { erro ->

                        Toast.makeText(
                            this,
                            "Erro ao pegar URL: ${erro.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .addOnFailureListener { erro ->

                Toast.makeText(
                    this,
                    "Erro ao enviar comprovante: ${erro.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun salvarDadosNoFirestore(url: String) {

        val userId = auth.currentUser?.uid

        if (userId == null) {
            Toast.makeText(
                this,
                "Usuário não encontrado",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val dados = mapOf(
            "comprovanteUrl" to url,
            "statusValidacao" to "em_analise",
            "dataEnvio" to FieldValue.serverTimestamp()
        )

        db.collection("responsaveis")
            .document(userId)
            .collection("documentos")
            .document("comprovanteResidencia")
            .set(dados)
            .addOnSuccessListener {

                Toast.makeText(
                    this,
                    "Comprovante enviado com sucesso!",
                    Toast.LENGTH_SHORT
                ).show()

                // Ir para a próxima tela
                VerificarProgressoDocumentos(this).verificar()

                finish()
            }
            .addOnFailureListener { erro ->

                Toast.makeText(
                    this,
                    "Erro ao salvar dados: ${erro.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }
}