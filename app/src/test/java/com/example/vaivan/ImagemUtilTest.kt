package com.example.vaivan

import com.example.vaivan.core.util.ImagemUtil
import org.junit.Assert.assertEquals
import org.junit.Test

class ImagemUtilTest {

    @Test
    fun `inSampleSize nao reduz abaixo do lado maximo`() {
        // 4000x3000 → /2 = 2000 (≥1920), /4 = 1000 (<1920) → fica em 2
        assertEquals(2, ImagemUtil.calcularInSampleSize(4000, 3000, 1920))
        // 4000x3000 com limite 720 → /4 = 1000, /8 = 500 → fica em 4
        assertEquals(4, ImagemUtil.calcularInSampleSize(4000, 3000, 720))
    }

    @Test
    fun `imagem menor que o limite nao e amostrada`() {
        assertEquals(1, ImagemUtil.calcularInSampleSize(600, 400, 720))
    }

    @Test
    fun `dimensoes finais mantem proporcao e respeitam o limite`() {
        assertEquals(1920 to 1440, ImagemUtil.dimensoesFinais(4000, 3000, 1920))
        assertEquals(540 to 720, ImagemUtil.dimensoesFinais(1500, 2000, 720))
    }

    @Test
    fun `imagem menor que o limite nao e ampliada`() {
        assertEquals(600 to 400, ImagemUtil.dimensoesFinais(600, 400, 720))
    }
}
