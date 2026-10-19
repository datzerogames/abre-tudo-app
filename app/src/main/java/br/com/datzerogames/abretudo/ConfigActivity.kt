package br.com.datzerogames.abretudo

import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.documentfile.provider.DocumentFile
import com.google.android.material.appbar.MaterialToolbar

class ConfigActivity : BaseActivity() {
    private lateinit var conteudo: LinearLayout

    private val liberarPasta =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                PastasLiberadas.liberar(this, uri)
                Toast.makeText(this, "Pasta liberada", Toast.LENGTH_SHORT).show()
                montar()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.configuracoes)
        conteudo = findViewById(R.id.conteudo)
        montar()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    /** Desenha de novo todas as linhas, para refletir as escolhas feitas. */
    private fun montar() {
        conteudo.removeAllViews()

        titulo("Aparência")
        linha("Tema de cores", Tema.cores[Tema.corAtual(this)].nome) { escolherTema() }
        linha("Modo", rotuloModo(Tema.modo(this))) { escolherModo() }

        titulo("Pastas liberadas")
        val pastas = PastasLiberadas.listar(this)
        if (pastas.isEmpty()) {
            linha("Nenhuma pasta liberada", "Libere uma pasta para o app encontrar seus arquivos.") {}
        }
        pastas.forEach { uri ->
            val nome = DocumentFile.fromTreeUri(this, uri)?.name ?: "Pasta"
            linha(nome, "Toque para remover o acesso") { confirmarRemoverPasta(uri, nome) }
        }
        linha("Liberar pasta", "Escolha uma pasta, por exemplo Documentos") {
            liberarPasta.launch(null)
        }

        titulo("Dados")
        linha("Limpar histórico", "Apaga a lista de recentes") { confirmarLimparHistorico() }

        titulo("Sobre")
        linha("Privacidade", "Seus arquivos não saem do aparelho") { mostrarPrivacidade() }
        linha("Sobre o app", "Versão ${versao()}") { mostrarSobre() }
    }

    private fun escolherTema() {
        val nomes = Tema.cores.map { it.nome }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Tema de cores")
            .setSingleChoiceItems(nomes, Tema.corAtual(this)) { dialogo, escolhido ->
                Tema.definirCor(this, escolhido)
                dialogo.dismiss()
                recreate()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun escolherModo() {
        val opcoes = arrayOf("Sistema", "Claro", "Escuro")
        AlertDialog.Builder(this)
            .setTitle("Modo")
            .setSingleChoiceItems(opcoes, Tema.modo(this)) { dialogo, escolhido ->
                Tema.definirModo(this, escolhido)
                dialogo.dismiss()
                montar()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun rotuloModo(modo: Int): String = when (modo) {
        Tema.MODO_CLARO -> "Claro"
        Tema.MODO_ESCURO -> "Escuro"
        else -> "Sistema"
    }

    private fun confirmarRemoverPasta(uri: Uri, nome: String) {
        AlertDialog.Builder(this)
            .setMessage("Remover acesso à pasta $nome? Os arquivos dentro dela não são apagados.")
            .setPositiveButton("Remover") { _, _ ->
                PastasLiberadas.remover(this, uri)
                montar()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmarLimparHistorico() {
        AlertDialog.Builder(this)
            .setTitle("Limpar histórico")
            .setMessage("A lista de recentes será apagada. Seus arquivos, favoritos e pastas continuam como estão.")
            .setPositiveButton("Limpar") { _, _ ->
                Biblioteca.limparRecentes()
                Toast.makeText(this, "Histórico limpo", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarPrivacidade() {
        AlertDialog.Builder(this)
            .setTitle("Privacidade")
            .setMessage(
                "O Abre Tudo funciona sem internet e não envia seus arquivos para nenhum servidor. " +
                    "Tudo é processado no próprio aparelho. O app só acessa os arquivos que você abre, " +
                    "recebe de outros apps ou as pastas que você libera, e você pode remover esse acesso quando quiser."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun mostrarSobre() {
        AlertDialog.Builder(this)
            .setTitle("Abre Tudo")
            .setMessage(
                "Leitor e organizador de documentos feito para o Brasil. " +
                    "Abra, organize e compartilhe PDF, Word, Excel, PowerPoint, fotos e muito mais.\n\n" +
                    "Versão ${versao()}"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun versao(): String = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()

    private fun titulo(texto: String) {
        conteudo.addView(TextView(this).apply {
            text = texto
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            setTextColor(obterCorPrimaria())
            setPadding(dp(16), dp(20), dp(16), dp(6))
        })
    }

    private fun linha(titulo: String, subtitulo: String, aoClicar: () -> Unit) {
        val bloco = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundResource(obterFundoSelecionavel())
            setOnClickListener { aoClicar() }
        }
        bloco.addView(TextView(this).apply {
            text = titulo
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
        })
        bloco.addView(TextView(this).apply {
            text = subtitulo
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
        })
        conteudo.addView(bloco)
    }

    private fun obterCorPrimaria(): Int {
        val valor = TypedValue()
        theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, valor, true)
        return valor.data
    }

    private fun obterFundoSelecionavel(): Int {
        val valor = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, valor, true)
        return valor.resourceId
    }

    private fun dp(valor: Int): Int = (valor * resources.displayMetrics.density).toInt()
}
