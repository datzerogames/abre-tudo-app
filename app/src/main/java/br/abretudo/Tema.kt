package br.abretudo

import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

class CorTema(val nome: String, val hex: String, val estilo: Int)

object Tema {
    const val MODO_SISTEMA = 0
    const val MODO_CLARO = 1
    const val MODO_ESCURO = 2

    private const val PREFERENCIAS = "tema"
    private const val CHAVE_COR = "cor"
    private const val CHAVE_MODO = "modo"

    val cores = listOf(
        CorTema("Azul Oceano", "#1565C0", R.style.Cor_AzulOceano),
        CorTema("Verde Floresta", "#2E7D32", R.style.Cor_VerdeFloresta),
        CorTema("Vermelho Cereja", "#C62828", R.style.Cor_VermelhoCereja),
        CorTema("Roxo Ametista", "#6A1B9A", R.style.Cor_RoxoAmetista),
        CorTema("Laranja Pôr do Sol", "#E65100", R.style.Cor_LaranjaPorDoSol),
        CorTema("Rosa Flamingo", "#AD1457", R.style.Cor_RosaFlamingo),
        CorTema("Turquesa", "#00838F", R.style.Cor_Turquesa),
        CorTema("Índigo", "#283593", R.style.Cor_Indigo),
        CorTema("Marrom Café", "#5D4037", R.style.Cor_MarromCafe),
        CorTema("Verde Limão", "#558B2F", R.style.Cor_VerdeLimao),
        CorTema("Grafite", "#37474F", R.style.Cor_Grafite),
        CorTema("Âmbar", "#B26A00", R.style.Cor_Ambar),
    )

    fun corAtual(contexto: Context): Int =
        prefs(contexto).getInt(CHAVE_COR, 0).coerceIn(0, cores.lastIndex)

    fun definirCor(contexto: Context, indice: Int) {
        prefs(contexto).edit().putInt(CHAVE_COR, indice).apply()
    }

    fun modo(contexto: Context): Int = prefs(contexto).getInt(CHAVE_MODO, MODO_SISTEMA)

    fun definirModo(contexto: Context, modo: Int) {
        prefs(contexto).edit().putInt(CHAVE_MODO, modo).apply()
        aplicarModo(contexto)
    }

    fun aplicarModo(contexto: Context) {
        AppCompatDelegate.setDefaultNightMode(
            when (modo(contexto)) {
                MODO_CLARO -> AppCompatDelegate.MODE_NIGHT_NO
                MODO_ESCURO -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    /** Aplica a cor escolhida na tela e devolve o índice dela. Chamar antes do super.onCreate. */
    fun aplicar(atividade: Activity): Int {
        val indice = corAtual(atividade)
        atividade.setTheme(R.style.Base_AbreTudo)
        atividade.theme.applyStyle(cores[indice].estilo, true)
        return indice
    }

    private fun prefs(contexto: Context) =
        contexto.getSharedPreferences(PREFERENCIAS, Context.MODE_PRIVATE)
}
