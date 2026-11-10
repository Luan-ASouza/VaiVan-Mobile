package com.example.vaivan.core.util;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

public class MascaraUtil {

    public static TextWatcher inserir(final String mascara,
                                      final EditText editText) {

        return new TextWatcher() {

            boolean isUpdating;
            String ultimosDigitos = "";

            @Override
            public void beforeTextChanged(CharSequence s,
                                          int start,
                                          int count,
                                          int after) {
            }

            @Override
            public void onTextChanged(CharSequence s,
                                      int start,
                                      int before,
                                      int count) {

                if (isUpdating) {
                    isUpdating = false;
                    return;
                }

                String digitos = s.toString()
                        .replaceAll("[^\\d]", "");

                // "before > count" é o apagar (removeu mais do que inseriu). Se mesmo
                // assim a quantidade de dígitos não mudou, o que foi apagado foi só um
                // separador (".", "-", " ") que a própria máscara tinha inserido -- sem
                // isso, o backspace nessa posição parece não fazer nada (a máscara
                // recoloca o separador do mesmo jeito) e o cursor pula pro fim. Apaga
                // também o último dígito, pra cada backspace remover sempre algo de
                // verdade.
                boolean apagando = before > count;

                if (apagando &&
                        digitos.length() > 0 &&
                        digitos.length() == ultimosDigitos.length()) {

                    digitos = digitos.substring(0, digitos.length() - 1);
                }

                String mask = montarMascara(mascara, digitos);

                ultimosDigitos = digitos;
                isUpdating = true;

                editText.setText(mask);
                editText.setSelection(mask.length());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
    }

    private static String montarMascara(String mascara, String digitos) {

        StringBuilder mask = new StringBuilder();
        int i = 0;

        for (char m : mascara.toCharArray()) {

            if (m != '#') {
                mask.append(m);
                continue;
            }

            if (i >= digitos.length()) {
                break;
            }

            mask.append(digitos.charAt(i));
            i++;
        }

        return mask.toString();
    }
}
