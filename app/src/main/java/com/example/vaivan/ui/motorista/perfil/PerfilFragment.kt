package com.example.vaivan.ui.motorista.perfil

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
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bumptech.glide.Glide
import com.example.vaivan.R
import com.example.vaivan.core.util.ExclusaoContaHelper
import com.example.vaivan.data.local.VaivanDatabase // Substitui pelo teu banco do Room
import com.example.vaivan.data.remote.routes.GoogleRoutesClient
import com.example.vaivan.data.repository.DocumentoRepository
import com.example.vaivan.data.repository.PassageiroRepository
import com.example.vaivan.data.repository.RotaRepository
import com.example.vaivan.data.repository.UsuarioRepository
import com.example.vaivan.data.repository.VeiculoRepository
import com.example.vaivan.domain.usecase.conta.ExcluirContaUseCase
import com.example.vaivan.ui.comum.perfil.EditarPerfilActivity
import com.example.vaivan.ui.inicio.UsuarioViewModel
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class PerfilFragment : Fragment() {

    // 1. Declaras a variável do ViewModel
    private lateinit var viewModel: UsuarioViewModel

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

        viewModel = ViewModelProvider(
            this,
            viewModelFactory {
                initializer {
                    UsuarioViewModel(UsuarioRepository(database.usuarioDao()))
                }
            }
        )[UsuarioViewModel::class.java]

        // 5. Mapeias os teus elementos do XML
        val txtNome = view.findViewById<TextView>(R.id.txtNomeUsuarioCompleto)
        val txtNascimento = view.findViewById<TextView>(R.id.txtNascimento)
        val txtCPF = view.findViewById<TextView>(R.id.txtCPF)
        val imgAvatar = view.findViewById<ShapeableImageView>(R.id.imgUsuarioAvatar)
        val btnEditarPerfil = view.findViewById<View>(R.id.btnEditarPerfil)
        val btnExcluirConta = view.findViewById<View>(R.id.btnExcluirConta)

        val uid = FirebaseAuth.getInstance().currentUser?.uid

        // =========================================================
        // EDITAR PERFIL
        // =========================================================

        btnEditarPerfil.setOnClickListener {
            startActivity(Intent(requireContext(), EditarPerfilActivity::class.java))
        }

        // =========================================================
        // EXCLUIR CONTA
        // =========================================================

        btnExcluirConta.setOnClickListener {

            ExclusaoContaHelper.confirmarExclusaoConta(this) { motoristaId ->

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

                excluirContaUseCase.excluirContaMotorista(motoristaId)
            }
        }

        if (uid != null) {
            // 6. Escutas o Flow normalmente
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.observarPorId(uid).collect { responsavel ->
                        if (responsavel != null) {
                            txtNome.text = responsavel.nome
                            txtCPF.text = responsavel.cpf

                            responsavel.dataNascimento?.let {
                                if (it.isNotBlank()) {
                                    txtNascimento.text = responsavel.dataNascimento
                                }
                            }

                            Glide.with(imgAvatar)
                                .load(responsavel.fotoUrl)
                                .placeholder(R.drawable.ic_perfil)
                                .error(R.drawable.ic_perfil)
                                .circleCrop()
                                .into(imgAvatar)
                        }
                    }
                }
            }
        }
    }

}