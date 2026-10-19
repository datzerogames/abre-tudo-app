package br.com.datzerogames.abretudo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
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
        title = "Configurações"
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
        val pastas = PastasLiberadas.listar(this)

        linha(android.R.drawable.ic_menu_gallery, "Tema de cores", Tema.cores[Tema.corAtual(this)].nome) { escolherTema() }
        linha(android.R.drawable.ic_menu_view, "Claro ou escuro", rotuloModo(Tema.modo(this))) { escolherModo() }
        linha(
            android.R.drawable.ic_menu_myplaces,
            "Pastas liberadas",
            if (pastas.isEmpty()) "Nenhuma pasta liberada" else "${pastas.size} pasta(s) liberada(s)",
        ) { gerenciarPastas() }
        linha(android.R.drawable.ic_menu_delete, "Limpar histórico", "Apaga a lista de recentes") { confirmarLimparHistorico() }
        linha(android.R.drawable.ic_lock_idle_lock, "Privacidade", "Seus arquivos não saem do aparelho") { mostrarPrivacidade() }
        linha(android.R.drawable.ic_dialog_info, "Sobre o app", "Versão ${versao()}") { mostrarSobre() }
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
            .setTitle("Claro ou escuro")
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

    /** Lista as pastas liberadas. Tocar em uma remove o acesso; a última opção libera outra. */
    private fun gerenciarPastas() {
        val pastas = PastasLiberadas.listar(this)
        val nomes = pastas.map { DocumentFile.fromTreeUri(this, it)?.name ?: "Pasta" } + "Liberar nova pasta"
        AlertDialog.Builder(this)
            .setTitle("Pastas liberadas")
            .setItems(nomes.toTypedArray()) { _, escolhido ->
                if (escolhido == pastas.size) {
                    liberarPasta.launch(null)
                } else {
                    confirmarRemoverPasta(pastas[escolhido], nomes[escolhido])
                }
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun confirmarRemoverPasta(uri: android.net.Uri, nome: String) {
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

    /** Uma linha do estilo dos cartões: bloco preto com ícone, título, subtítulo e seta. */
    private fun linha(icone: Int, titulo: String, subtitulo: String, aoClicar: () -> Unit) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_config, conteudo, false)
        view.findViewById<ImageView>(R.id.icone).setImageResource(icone)
        view.findViewById<TextView>(R.id.titulo).text = titulo
        view.findViewById<TextView>(R.id.subtitulo).text = subtitulo
        view.setOnClickListener { aoClicar() }
        conteudo.addView(view)
    }
}
