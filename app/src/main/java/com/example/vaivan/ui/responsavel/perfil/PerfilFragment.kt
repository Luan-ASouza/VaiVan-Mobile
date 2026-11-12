package com.example.vaivan.ui.responsavel.perfil

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.vaivan.R
import com.example.vaivan.core.notificacao.TokenDispositivo
import com.example.vaivan.core.util.ExclusaoContaHelper
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.remote.routes.GoogleRoutesClient
import com.example.vaivan.data.repository.DocumentoRepository
import com.example.vaivan.data.repository.PassageiroRepository
import com.example.vaivan.data.repository.RotaRepository
import com.example.vaivan.data.repository.UsuarioRepository
import com.example.vaivan.data.repository.VeiculoRepository
import com.example.vaivan.domain.usecase.conta.ExcluirContaUseCase
import com.example.vaivan.ui.comum.perfil.PerfilCampos
import com.example.vaivan.ui.inicio.LoginActivity
import com.example.vaivan.ui.inicio.UsuarioViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PerfilFragment : Fragment() {

    private val viewModel: UsuarioViewModel by viewModels {
        viewModelFactory {
            initializer {
                val db = VaivanDatabase.getInstance(requireContext())
                UsuarioViewModel(UsuarioRepository(db.usuarioDao()))
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_perfil, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val database = VaivanDatabase.getInstance(requireContext())

        // Cabeçalho + linhas de campos (cada uma abre a sua tela de edição).
        val campos = PerfilCampos(this, view)

        // =========================================================
        // EXCLUIR CONTA
        // =========================================================

        view.findViewById<View>(R.id.btnExcluirConta).setOnClickListener {

            ExclusaoContaHelper.confirmarExclusaoConta(this) { uid ->

                val excluirContaUseCase = ExcluirContaUseCase(
                    usuarioRepository = UsuarioRepository(database.usuarioDao()),
                    rotaRepository = RotaRepository(
                        database.rotaDao(),
                        database.paradaRotaDao(),
                        GoogleRoutesClient(requireContext())
                    ),
                    veiculoRepository = VeiculoRepository(database.veiculoDao()),
                    documentoRepository = DocumentoRepository(database.documentoDao()),
                    passageiroRepository = PassageiroRepository(database.passageiroDao())
                )

                excluirContaUseCase.excluirContaResponsavel(uid)
            }
        }

        // =========================================================
        // DESCONECTAR
        // =========================================================

        view.findViewById<View>(R.id.btnDesconectar).setOnClickListener {

            viewLifecycleOwner.lifecycleScope.launch {

                // Limpa o Room fora da Main Thread
                withContext(Dispatchers.IO) {
                    database.clearAllTables()
                }

                // Para de receber notificações desta conta neste aparelho
                TokenDispositivo.removerDesteDispositivo()

                FirebaseAuth.getInstance().signOut()

                // Volta para o Login, sem deixar telas antigas na pilha
                val intent = Intent(requireContext(), LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
        }

        // =========================================================
        // OBSERVAR USUÁRIO
        // =========================================================

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.observarPorId(uid).collect { usuario ->
                    if (usuario != null) campos.preencher(usuario)
                }
            }
        }
    }
}
