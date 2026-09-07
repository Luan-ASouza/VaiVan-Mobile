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
import com.example.trabalhograua.ui.motorista.veiculos.CadastroVeiculoActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class ValidaCnhActivity : AppCompatActivity() {

    private lateinit var storage: FirebaseStorage
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var uriFrente: Uri? = null
    private var uriVerso: Uri? = null

    // Launchers para abrir a galeria
    private val launcherFrente = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            uriFrente = it
            findViewById<TextView>(R.id.txtNomeArquivoFrente).text = "Frente selecionada"
            findViewById<Button>(R.id.btnEnviarArquivoFrente).text = "Editar envio"
            findViewById<Button>(R.id.btnEnviarArquivoFrente).setTextColor(
                ContextCompat.getColor(this, R.color.white)
            )
            findViewById<Button>(R.id.btnEnviarArquivoFrente).background = getDrawable(R.drawable.btn_gray_filled)
        }
    }

    private val launcherVerso = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            uriVerso = it
            findViewById<TextView>(R.id.txtNomeArquivoVerso).text = "Verso selecionado"
            findViewById<Button>(R.id.btnEnviarArquivoVerso).setTextColor(
                ContextCompat.getColor(this, R.color.white)
            )
            findViewById<Button>(R.id.btnEnviarArquivoVerso).background = getDrawable(R.drawable.btn_gray_filled)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_valida_cnh)

        storage = FirebaseStorage.getInstance()
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val btnFrente = findViewById<Button>(R.id.btnEnviarArquivoFrente)
        val btnVerso = findViewById<Button>(R.id.btnEnviarArquivoVerso)
        val btnEnviarContinuar = findViewById<Button>(R.id.btnEnviarContinuarCnh)
        val scrollView = findViewById<ScrollView>(
            R.id.scrollValidacaoEmail
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

        btnFrente.setOnClickListener {
            launcherFrente.launch("image/*")
        }
        btnVerso.setOnClickListener {
            launcherVerso.launch("image/*")
            btnVerso.text = "Editar envio"
            btnVerso.background = getDrawable(R.drawable.btn_gray_filled)
        }

        btnEnviarContinuar.setOnClickListener {
            if (uriFrente != null && uriVerso != null) {
                uploadImagens()
            } else {
                Toast.makeText(this, "Selecione as duas imagens", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun uploadImagens() {
        val userId = auth.currentUser?.uid ?: return

        val refFrente = storage.reference
            .child("documentos/CNH/$userId/frente.jpg")

        val refVerso = storage.reference
            .child("documentos/CNH/$userId/verso.jpg")

        refFrente.putFile(uriFrente!!)
            .addOnSuccessListener {
                Toast.makeText(this, "Frente enviada!", Toast.LENGTH_SHORT).show()

                refFrente.downloadUrl
                    .addOnSuccessListener { urlFrente ->

                        Toast.makeText(
                            this,
                            "URL da frente encontrada!",
                            Toast.LENGTH_SHORT
                        ).show()

                        refVerso.putFile(uriVerso!!)
                            .addOnSuccessListener {

                                Toast.makeText(
                                    this,
                                    "Verso enviado!",
                                    Toast.LENGTH_SHORT
                                ).show()

                                refVerso.downloadUrl
                                    .addOnSuccessListener { urlVerso ->

                                        Toast.makeText(
                                            this,
                                            "URL do verso encontrada!",
                                            Toast.LENGTH_SHORT
                                        ).show()

                                        salvarDadosNoFirestore(
                                            urlFrente.toString(),
                                            urlVerso.toString()
                                        )
                                    }
                                    .addOnFailureListener { erro ->
                                        Toast.makeText(
                                            this,
                                            "Erro ao pegar URL do verso: ${erro.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                            }
                            .addOnFailureListener { erro ->
                                Toast.makeText(
                                    this,
                                    "Erro ao enviar verso: ${erro.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                    }
                    .addOnFailureListener { erro ->
                        Toast.makeText(
                            this,
                            "Erro ao pegar URL da frente: ${erro.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .addOnFailureListener { erro ->
                Toast.makeText(
                    this,
                    "Erro ao enviar frente: ${erro.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun salvarDadosNoFirestore(urlFrente: String, urlVerso: String) {
        val userId = auth.currentUser?.uid ?: return
        val dados = mapOf(
            "cnhFrenteUrl" to urlFrente,
            "cnhVersoUrl" to urlVerso,
            "statusValidacao" to "pendente",
            "dataEnvio" to FieldValue.serverTimestamp()
        )

        db.collection("responsaveis")
            .document(userId)
            .collection("documentos")
            .document("cnh")
            .set(dados)
            .addOnSuccessListener {
                Toast.makeText(this, "Documentos enviados com sucesso!", Toast.LENGTH_SHORT).show()
                // Ir para a próxima tela
                startActivity(Intent(this, CadastroVeiculoActivity::class.java))
            }
            .addOnFailureListener {
                Toast.makeText(this, "Erro ao salvar dados", Toast.LENGTH_SHORT).show()
            }
    }
}