package br.abretudo

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import java.util.concurrent.Executors

class MainActivity : BaseActivity() {
    private lateinit var abas: TabLayout
    private lateinit var lista: RecyclerView
    private lateinit var vazio: TextView
    private lateinit var adicionar: FloatingActionButton

    private val documentos = DocumentoAdapter(
        aoAbrir = { abrirDocumento(it) },
        aoFavoritar = {
            Biblioteca.alternarFavorito(it)
            atualizar()
        },
        aoSegurar = { opcoesDocumento(it) { atualizar() } },
    )

    private val pastas = PastaAdapter(
        aoAbrir = {
            startActivity(Intent(this, ListaPastaActivity::class.java).putExtra(ListaPastaActivity.EXTRA_PASTA, it.id))
        },
        aoSegurar = { confirmarExcluirPasta(it) { atualizar() } },
    )

    private val escolherArquivos =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { importar(it) }

    private val executor = Executors.newSingleThreadExecutor()
    private val principal = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))

        abas = findViewById(R.id.abas)
        lista = findViewById(R.id.lista)
        vazio = findViewById(R.id.vazio)
        adicionar = findViewById(R.id.adicionar)

        lista.layoutManager = LinearLayoutManager(this)
        abas.addTab(abas.newTab().setText("Recentes"))
        abas.addTab(abas.newTab().setText("Favoritos"))
        abas.addTab(abas.newTab().setText("Pastas"))
        abas.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) = atualizar()
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        adicionar.setOnClickListener { escolherArquivos.launch(arrayOf("*/*")) }

        // Só na primeira abertura: ao girar a tela, o mesmo arquivo não deve ser importado de novo.
        if (savedInstanceState == null) receberArquivos(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receberArquivos(intent)
    }

    override fun onResume() {
        super.onResume()
        atualizar()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.novaPasta -> {
            novaPasta { atualizar() }
            true
        }
        R.id.temas -> {
            startActivity(Intent(this, TemasActivity::class.java))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun receberArquivos(intent: Intent) {
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(uriUnica(intent))
            Intent.ACTION_SEND_MULTIPLE -> uriVarias(intent)
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            else -> return
        }
        intent.action = null
        importar(uris)
    }

    @Suppress("DEPRECATION")
    private fun uriUnica(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun uriVarias(intent: Intent): List<Uri> =
        (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)).orEmpty()

    private fun importar(uris: List<Uri>) {
        if (uris.isEmpty()) return
        executor.execute {
            val importados = uris.mapNotNull { Biblioteca.importar(it) }
            principal.post {
                importados.forEach { Biblioteca.adicionar(it) }
                atualizar()
                val mensagem = when (importados.size) {
                    0 -> "Não foi possível abrir o arquivo."
                    1 -> "Arquivo adicionado."
                    else -> "${importados.size} arquivos adicionados."
                }
                Toast.makeText(this, mensagem, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun atualizar() {
        when (abas.selectedTabPosition) {
            0 -> mostrarDocumentos(
                Biblioteca.todos(),
                "Nenhum arquivo ainda.\nToque em + ou compartilhe um arquivo com o Abre Tudo.",
            )
            1 -> mostrarDocumentos(
                Biblioteca.favoritos(),
                "Nenhum favorito.\nToque na estrela de um arquivo para marcá-lo.",
            )
            else -> mostrarPastas()
        }
    }

    private fun mostrarDocumentos(docs: List<Documento>, mensagemVazio: String) {
        if (lista.adapter !== documentos) lista.adapter = documentos
        documentos.atualizar(docs)
        vazio.text = mensagemVazio
        vazio.visibility = if (docs.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun mostrarPastas() {
        val lista = Biblioteca.pastas()
        if (this.lista.adapter !== pastas) this.lista.adapter = pastas
        pastas.atualizar(lista)
        vazio.text = "Nenhuma pasta ainda.\nCrie uma pelo menu para organizar seus arquivos."
        vazio.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
    }
}
