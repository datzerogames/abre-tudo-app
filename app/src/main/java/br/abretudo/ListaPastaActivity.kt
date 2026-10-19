package br.abretudo

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar

class ListaPastaActivity : BaseActivity() {
    companion object {
        const val EXTRA_PASTA = "pasta"
    }

    private var pasta: Pasta? = null
    private lateinit var lista: RecyclerView
    private lateinit var vazio: TextView

    private val documentos = DocumentoAdapter(
        aoAbrir = { abrirDocumento(it) },
        aoFavoritar = {
            Biblioteca.alternarFavorito(it)
            atualizar()
        },
        aoSegurar = { opcoesDocumento(it) { atualizar() } },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lista_pasta)

        val barra: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(barra)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        pasta = Biblioteca.pastaPorId(intent.getStringExtra(EXTRA_PASTA))
        if (pasta == null) {
            finish()
            return
        }
        title = pasta?.nome

        lista = findViewById(R.id.lista)
        vazio = findViewById(R.id.vazio)
        lista.layoutManager = LinearLayoutManager(this)
        lista.adapter = documentos
    }

    override fun onResume() {
        super.onResume()
        atualizar()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun atualizar() {
        val id = pasta?.id ?: return
        val docs = Biblioteca.daPasta(id)
        documentos.atualizar(docs)
        vazio.visibility = if (docs.isEmpty()) View.VISIBLE else View.GONE
    }
}
