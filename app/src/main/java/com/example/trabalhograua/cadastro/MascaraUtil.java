package com.example.trabalhograua.cadastro;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

public class MascaraUtil {

    public static TextWatcher inserir(final String mascara,
                                      final EditText editText) {

        return new TextWatcher() {

            boolean isUpdating;
            String old = "";

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

                String str = s.toString()
                        .replaceAll("[^\\d]", "");

                String mask = "";

                if (isUpdating) {

                    old = str;
                    isUpdating = false;

                    return;
                }

                int i = 0;

                for (char m : mascara.toCharArray()) {

                    if (m != '#') {

                        mask += m;

                        continue;
                    }

                    try {

                        mask += str.charAt(i);

                    } catch (Exception e) {

                        break;
                    }

                    i++;
                }

                isUpdating = true;

                editText.setText(mask);

                editText.setSelection(mask.length());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
    }
}