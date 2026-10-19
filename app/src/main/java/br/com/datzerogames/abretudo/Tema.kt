package br.com.datzerogames.abretudo

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors

/** Um tema de cor. `estilo` é null em "Cores do sistema", que usa as cores do papel de parede. */
class CorTema(val nome: String, val hex: String, val estilo: Int?)

object Tema {
    const val MODO_SISTEMA = 0
    const val MODO_CLARO = 1
    const val MODO_ESCURO = 2

    private const val PREFERENCIAS = "ajustes"
    private const val CHAVE_COR = "cor"
    private const val CHAVE_MODO = "modo"

    val cores = listOf(
        CorTema("Tinta e Laranja", "#FF4303", R.style.Cor_TintaLaranja),
        CorTema("Caneta Azul", "#1E4D9B", R.style.Cor_CanetaAzul),
        CorTema("Carimbo Roxo", "#6B3FA0", R.style.Cor_CarimboRoxo),
        CorTema("Clipe Turquesa", "#17807E", R.style.Cor_ClipeTurquesa),
        CorTema("Envelope Pardo", "#1F5E6B", R.style.Cor_EnvelopePardo),
        CorTema("Lápis Grafite", "#4A5A66", R.style.Cor_LapisGrafite),
        CorTema("Papel Kraft", "#6B7A3A", R.style.Cor_PapelKraft),
        CorTema("Lacre de Cera", "#7A1F3D", R.style.Cor_LacreCera),
        CorTema("Marca-texto", "#1B2A4A", R.style.Cor_MarcaTexto),
        CorTema("Planilha Verde", "#1E7A46", R.style.Cor_PlanilhaVerde),
        CorTema("Post-it", "#D9573B", R.style.Cor_PostIt),
        CorTema("Selo Dourado", "#8A6A1F", R.style.Cor_SeloDourado),
        CorTema("Cores do sistema", "#7F7F7F", null),
    )

    fun corAtual(contexto: Context): Int =
        prefs(contexto).getInt(CHAVE_COR, 0).coerceIn(0, cores.lastIndex)

    /** Cor principal da tela atual, já com o tema escolhido aplicado. */
    fun corPrimaria(contexto: Context): Int {
        val valor = android.util.TypedValue()
        contexto.theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, valor, true)
        return valor.data
    }

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
        val estilo = cores[indice].estilo
        if (estilo != null) {
            atividade.theme.applyStyle(estilo, true)
        } else if (DynamicColors.isDynamicColorAvailable()) {
            DynamicColors.applyToActivityIfAvailable(atividade)
        }
        return indice
    }

    private fun prefs(contexto: Context) =
        contexto.getSharedPreferences(PREFERENCIAS, Context.MODE_PRIVATE)
}

/**
 * Pastas que o usuário liberou para o app ler (Storage Access Framework).
 * O app só acessa o que está dentro delas, e o acesso pode ser removido a qualquer momento.
 */
object PastasLiberadas {
    private const val PREFERENCIAS = "ajustes"
    private const val CHAVE = "liberadas"

    fun listar(contexto: Context): List<Uri> =
        prefs(contexto).getStringSet(CHAVE, emptySet()).orEmpty()
            .map { Uri.parse(it) }
            .sortedBy { it.toString() }

    fun liberar(contexto: Context, uri: Uri) {
        contexto.contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        val novas = prefs(contexto).getStringSet(CHAVE, emptySet()).orEmpty().toMutableSet()
        novas.add(uri.toString())
        prefs(contexto).edit().putStringSet(CHAVE, novas).apply()
    }

    fun remover(contexto: Context, uri: Uri) {
        try {
            contexto.contentResolver.releasePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (e: SecurityException) {
            // A permissão já tinha sido perdida; só remover da lista.
        }
        val novas = prefs(contexto).getStringSet(CHAVE, emptySet()).orEmpty().toMutableSet()
        novas.remove(uri.toString())
        prefs(contexto).edit().putStringSet(CHAVE, novas).apply()
    }

    private fun prefs(contexto: Context) =
        contexto.getSharedPreferences(PREFERENCIAS, Context.MODE_PRIVATE)
}
