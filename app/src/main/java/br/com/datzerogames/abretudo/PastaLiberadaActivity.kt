package br.com.datzerogames.abretudo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import java.util.concurrent.Executors

/**
 * Navega dentro de uma pasta liberada. Abrir um arquivo copia ele para a biblioteca,
 * então a pasta original nunca é alterada.
 */
class PastaLiberadaActivity : BaseActivity() {
    companion object {
        private const val EXTRA_RAIZ = "raiz"
        private const val EXTRA_DOCUMENTO = "documento"

        /** Abre a pasta liberada `raiz` na tela de navegação. */
        fun abrir(contexto: Context, raiz: Uri) {
            contexto.startActivity(
                Intent(contexto, PastaLiberadaActivity::class.java)
                    .putExtra(EXTRA_RAIZ, raiz.toString())
                    .putExtra(EXTRA_DOCUMENTO, DocumentsContract.getTreeDocumentId(raiz))
            )
        }
    }

    private lateinit var raiz: Uri
    private lateinit var lista: RecyclerView
    private lateinit var vazio: TextView
    private val executor = Executors.newSingleThreadExecutor()
    private val principal = Handler(Looper.getMainLooper())

    private val adapter = NavegacaoAdapter(
        aoClicar = { filho -> clicar(filho) },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pasta_liberada)

        raiz = Uri.parse(intent.getStringExtra(EXTRA_RAIZ))
        val documentoId = intent.getStringExtra(EXTRA_DOCUMENTO) ?: DocumentsContract.getTreeDocumentId(raiz)

        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        lista = findViewById(R.id.lista)
        vazio = findViewById(R.id.vazio)
        lista.layoutManager = LinearLayoutManager(this)
        lista.adapter = adapter

        val pasta = DocumentFile.fromTreeUri(this, DocumentsContract.buildDocumentUriUsingTree(raiz, documentoId))
        if (pasta == null || !pasta.exists()) {
            Toast.makeText(this, "O app perdeu o acesso a esta pasta. Libere-a de novo nas Configurações.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        title = pasta.name ?: "Pasta"
        mostrar(pasta)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun mostrar(pasta: DocumentFile) {
        val filhos = pasta.listFiles().sortedWith(compareBy({ !it.isDirectory }, { it.name?.lowercase() }))
        adapter.atualizar(filhos)
        vazio.visibility = if (filhos.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun clicar(filho: DocumentFile) {
        if (filho.isDirectory) {
            abrirPasta(filho)
        } else {
            copiarEAbrir(filho)
        }
    }

    private fun abrirPasta(pasta: DocumentFile) {
        startActivity(
            Intent(this, PastaLiberadaActivity::class.java)
                .putExtra(EXTRA_RAIZ, raiz.toString())
                .putExtra(EXTRA_DOCUMENTO, DocumentsContract.getDocumentId(pasta.uri))
        )
    }

    private fun copiarEAbrir(arquivo: DocumentFile) {
        Toast.makeText(this, "Abrindo...", Toast.LENGTH_SHORT).show()
        executor.execute {
            val doc = Biblioteca.importar(arquivo.uri)
            principal.post {
                if (doc == null) {
                    Toast.makeText(this, "Não foi possível abrir o arquivo.", Toast.LENGTH_LONG).show()
                } else {
                    Biblioteca.adicionar(doc)
                    abrirDocumento(doc)
                }
            }
        }
    }
}

/** Lista as pastas e arquivos dentro de uma pasta liberada. */
private class NavegacaoAdapter(
    private val aoClicar: (DocumentFile) -> Unit,
) : RecyclerView.Adapter<NavegacaoAdapter.Holder>() {

    private var itens: List<DocumentFile> = emptyList()

    fun atualizar(novos: List<DocumentFile>) {
        itens = novos
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = itens.size

    override fun onCreateViewHolder(pai: ViewGroup, tipo: Int): Holder {
        val view = LayoutInflater.from(pai.context).inflate(R.layout.item_linha, pai, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, posicao: Int) {
        val filho = itens[posicao]
        val contexto = holder.itemView.context
        holder.icone.setImageResource(
            if (filho.isDirectory) android.R.drawable.ic_menu_myplaces else android.R.drawable.ic_menu_agenda
        )
        holder.titulo.text = filho.name ?: "Sem nome"
        holder.subtitulo.text = if (filho.isDirectory) "Pasta" else Formatter.formatShortFileSize(contexto, filho.length())
        holder.favorito.visibility = View.GONE
        holder.mais.visibility = View.GONE
        holder.itemView.setOnClickListener { aoClicar(filho) }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val icone: ImageView = view.findViewById(R.id.icone)
        val titulo: TextView = view.findViewById(R.id.titulo)
        val subtitulo: TextView = view.findViewById(R.id.subtitulo)
        val favorito: ImageButton = view.findViewById(R.id.favorito)
        val mais: ImageButton = view.findViewById(R.id.mais)
    }
}
