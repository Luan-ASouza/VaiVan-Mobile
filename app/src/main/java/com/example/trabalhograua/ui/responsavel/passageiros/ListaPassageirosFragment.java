package com.example.trabalhograua.ui.responsavel.passageiros;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.example.trabalhograua.DadosGlobais;
import com.example.trabalhograua.R;

public class ListaPassageirosFragment extends Fragment {

    private TextView txtNome2;

    public ListaPassageirosFragment() {
        // Construtor vazio obrigatório
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        return inflater.inflate(
                R.layout.fragment_lista_passageiros,
                container,
                false
        );
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        CardView btnNovoPassageiro = view.findViewById(R.id.btnNovoPassageiro);
        txtNome2 = view.findViewById(R.id.txtNome2);

        btnNovoPassageiro.setOnClickListener(v -> {

            Intent intent = new Intent(
                    requireContext(),
                    AdicionarPassageiroActivity.class
            );

            startActivity(intent);
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!DadosGlobais.listaPassageiros.isEmpty()) {

            String ultimoCadastrado =
                    DadosGlobais.listaPassageiros.get(
                            DadosGlobais.listaPassageiros.size() - 1
                    );

            txtNome2.setText(ultimoCadastrado);
        }
    }
}