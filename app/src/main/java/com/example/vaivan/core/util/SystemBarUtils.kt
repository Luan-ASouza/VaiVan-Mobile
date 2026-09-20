package com.example.vaivan.core.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.example.vaivan.R

object SystemBarUtils {

    fun applyTopGap(root: View) {

        val topGap = root.findViewById<View?>(R.id.topGap)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->

            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            topGap?.updateLayoutParams {
                height = systemBars.top
            }

            insets
        }

        ViewCompat.requestApplyInsets(root)
    }

    fun applyBottomGap(root: View) {

        val bottomGap = root.findViewById<View?>(R.id.bottomGap)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->

            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            bottomGap?.updateLayoutParams {
                height = systemBars.bottom
            }

            insets
        }

        ViewCompat.requestApplyInsets(root)
    }

    fun applyTopAndBottomGaps(root: View) {

        val topGap = root.findViewById<View?>(R.id.topGap)
        val bottomGap = root.findViewById<View?>(R.id.bottomGap)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->

            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            topGap?.updateLayoutParams {
                height = systemBars.top
            }

            bottomGap?.updateLayoutParams {
                height = systemBars.bottom
            }

            insets
        }

        ViewCompat.requestApplyInsets(root)
    }
}