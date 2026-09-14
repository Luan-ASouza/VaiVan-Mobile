package com.example.trabalhograua.ui.responsavel.perfil

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider // Nativo do Android
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.trabalhograua.MainActivity
import com.example.trabalhograua.R
import com.example.trabalhograua.data.local.VaivanDatabase // Substitui pelo teu banco do Room
import com.example.trabalhograua.data.repository.ResponsavelRepository
import com.example.trabalhograua.ui.responsavel.ResponsavelViewModel
import com.example.trabalhograua.ui.responsavel.passageiros.AdicionarLocalActivity
import com.example.trabalhograua.ui.responsavel.passageiros.AdicionarPassageiroActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class PerfilFragment : Fragment() {

    // 1. Declaras a variável do ViewModel
    private lateinit var viewModel: ResponsavelViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_perfil, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 2. Inicializas o teu Repositório manualmente aqui dentro
        // (Precisas de passar o DAO do Room. Ajusta os nomes para os do teu projeto)
        val database = VaivanDatabase.getInstance(requireContext())
        val responsavelDao = database.responsavelDao()
        val repository = ResponsavelRepository(responsavelDao)

        // 3. Criar a Factory nativa (sem bibliotecas extras) para construir o ViewModel
        val factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return ResponsavelViewModel(repository) as T
            }
        }

        // 4. Instancias o ViewModel de forma tradicional e nativa
        viewModel = ViewModelProvider(this, factory)[ResponsavelViewModel::class.java]

        // 5. Mapeias os teus elementos do XML
        val txtNome = view.findViewById<TextView>(R.id.txtNomeUsuarioCompleto)
        val txtNascimento = view.findViewById<TextView>(R.id.txtNascimento)
        val txtCPF = view.findViewById<TextView>(R.id.txtCPF)
        val btnLocais = view.findViewById<TextView>(R.id.btnLocais)
        val btnDesconectar = view.findViewById<MaterialButton>(R.id.btnDesconectar)

        val uid = FirebaseAuth.getInstance().currentUser?.uid

        btnLocais.setOnClickListener {
            startActivity(Intent(requireContext(), AdicionarLocalActivity::class.java))
        }

        btnDesconectar.setOnClickListener {
            val database = VaivanDatabase.getInstance(requireContext())

            viewLifecycleOwner.lifecycleScope.launch {

                // Executa a limpeza do Room em uma thread de banco
                withContext(Dispatchers.IO) {
                    database.clearAllTables() }

                // Desconecta do Firebase
                FirebaseAuth.getInstance().signOut()

                // Volta para o Login e remove a Home da pilha
                val intent = Intent(requireContext(), MainActivity::class.java)
                intent.flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK

                startActivity(intent)
            }

                if (uid != null) {
                    // 6. Escutas o Flow normalmente
                    viewLifecycleOwner.lifecycleScope.launch {
                        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.observarPorId(uid).collect { responsavel ->
                                if (responsavel != null) {
                                    txtNome.text = responsavel.nome
                                    txtCPF.text = responsavel.cpf

                                    responsavel.dataNascimento?.let { timestamp ->
                                        val date = timestamp.toDate()
                                        val formato = SimpleDateFormat("dd / MM / yyyy", Locale.getDefault())
                                        txtNascimento.text = formato.format(date)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
}