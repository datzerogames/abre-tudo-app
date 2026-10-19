package br.com.datzerogames.abretudo

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.IOException
import java.util.concurrent.Executors

/** Uma aba que sabe se atualizar quando os dados mudam. */
interface Aba {
    fun atualizar()
}

class MainActivity : BaseActivity() {
    private lateinit var nav: BottomNavigationView
    private lateinit var adicionar: FloatingActionButton

    private var ferramentaPendente: Ferramenta? = null
    private var parametroPendente: String? = null

    private val executor = Executors.newSingleThreadExecutor()
    private val principal = Handler(Looper.getMainLooper())

    private val abrirArquivos =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { importar(it, abrirSeUnico = true) }

    private val escolherPdfs =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { executarFerramenta(it) }

    private val escolherImagens =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { executarFerramenta(it) }

    private val liberarPasta =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                PastasLiberadas.liberar(this, uri)
                Toast.makeText(this, "Pasta liberada", Toast.LENGTH_SHORT).show()
                atualizarAba()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))

        nav = findViewById(R.id.nav)
        adicionar = findViewById(R.id.adicionar)

        nav.setOnItemSelectedListener {
            mostrarAba(it.itemId)
            true
        }
        adicionar.setOnClickListener { menuAdicionar() }

        if (savedInstanceState == null) {
            nav.selectedItemId = R.id.nav_inicio
            // Só na primeira abertura: ao girar a tela, o mesmo arquivo não deve ser importado de novo.
            receberArquivos(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receberArquivos(intent)
    }

    override fun onResume() {
        super.onResume()
        atualizarAba()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.configuracoes -> {
            startActivity(Intent(this, ConfigActivity::class.java))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    /** Mostra uma das abas. Chamado pela barra de navegação e pelos atalhos da tela Início. */
    fun mostrarAba(id: Int) {
        val fragmento: Fragment = when (id) {
            R.id.nav_arquivos -> ArquivosFragment()
            R.id.nav_favoritos -> FavoritosFragment()
            R.id.nav_ferramentas -> FerramentasFragment()
            else -> InicioFragment()
        }
        supportFragmentManager.beginTransaction().replace(R.id.container, fragmento).commit()
        // O botão de adicionar só aparece nas abas em que faz sentido.
        adicionar.visibility = if (id == R.id.nav_favoritos || id == R.id.nav_ferramentas) View.GONE else View.VISIBLE
    }

    /** Troca de aba pelo código, como o atalho "Ver tudo" da tela Início. */
    fun irPara(id: Int) {
        nav.selectedItemId = id
    }

    fun atualizarAba() {
        (supportFragmentManager.findFragmentById(R.id.container) as? Aba)?.atualizar()
    }

    private fun menuAdicionar() {
        val opcoes = arrayOf("Escanear documento", "Abrir arquivo", "Criar PDF de imagens")
        AlertDialog.Builder(this)
            .setTitle("Abrir opções")
            .setItems(opcoes) { _, which ->
                when (which) {
                    0 -> Toast.makeText(this, "Escanear chega numa próxima versão.", Toast.LENGTH_SHORT).show()
                    1 -> abrirArquivos.launch(arrayOf("*/*"))
                    2 -> iniciarFerramenta(Ferramenta.FOTOS_PDF)
                }
            }
            .show()
    }

    /** Abre as pastas liberadas em outra tela (chamado pela aba Arquivos e pelas configurações). */
    fun liberarNovaPasta() = liberarPasta.launch(null)

    private fun receberArquivos(intent: Intent) {
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(uriUnica(intent))
            Intent.ACTION_SEND_MULTIPLE -> uriVarias(intent)
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            else -> return
        }
        val veioDeAbrirCom = intent.action == Intent.ACTION_VIEW
        intent.action = null
        importar(uris, abrirSeUnico = veioDeAbrirCom)
    }

    @Suppress("DEPRECATION")
    private fun uriUnica(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun uriVarias(intent: Intent): List<Uri> =
        (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)).orEmpty()

    /** Copia os arquivos para a biblioteca em segundo plano e depois mostra o resultado. */
    private fun importar(uris: List<Uri>, abrirSeUnico: Boolean) {
        if (uris.isEmpty()) return
        executor.execute {
            val importados = uris.mapNotNull { Biblioteca.importar(it) }
            principal.post {
                importados.forEach { Biblioteca.adicionar(it) }
                when {
                    importados.isEmpty() -> Toast.makeText(this, "Não foi possível abrir o arquivo.", Toast.LENGTH_LONG).show()
                    abrirSeUnico && importados.size == 1 -> abrirDocumento(importados.first())
                    importados.size == 1 -> Toast.makeText(this, "1 arquivo recebido", Toast.LENGTH_SHORT).show()
                    else -> Toast.makeText(this, "${importados.size} arquivos recebidos", Toast.LENGTH_SHORT).show()
                }
                if (nav.selectedItemId == R.id.nav_inicio) atualizarAba() else nav.selectedItemId = R.id.nav_inicio
            }
        }
    }

    /** Começa uma ferramenta: pergunta o que for preciso e depois pede os arquivos. */
    fun iniciarFerramenta(ferramenta: Ferramenta) {
        if (!ferramenta.implementada) {
            Toast.makeText(this, "${ferramenta.titulo} chega numa próxima versão.", Toast.LENGTH_SHORT).show()
            return
        }
        val pergunta = ferramenta.pergunta
        if (pergunta == null) {
            escolherArquivosPara(ferramenta, null)
        } else {
            perguntarTexto(pergunta) { escolherArquivosPara(ferramenta, it) }
        }
    }

    private fun escolherArquivosPara(ferramenta: Ferramenta, parametro: String?) {
        ferramentaPendente = ferramenta
        parametroPendente = parametro
        when (ferramenta.entrada) {
            Entrada.PDF_UM, Entrada.PDF_VARIOS -> escolherPdfs.launch(arrayOf("application/pdf"))
            Entrada.IMAGENS -> escolherImagens.launch(arrayOf("image/*"))
            Entrada.NENHUMA -> Unit
        }
    }

    private fun executarFerramenta(uris: List<Uri>) {
        val ferramenta = ferramentaPendente ?: return
        val parametro = parametroPendente
        ferramentaPendente = null
        parametroPendente = null
        if (uris.isEmpty()) return

        Toast.makeText(this, "Processando...", Toast.LENGTH_SHORT).show()
        val contexto = applicationContext
        executor.execute {
            try {
                val saidas = PdfFerramentas.executar(contexto, ferramenta, uris, parametro)
                principal.post {
                    val criados = saidas.map { Biblioteca.importarArquivo(it.arquivo, it.nome, it.tipo) }
                    criados.forEach { Biblioteca.adicionar(it) }
                    Toast.makeText(this, "${ferramenta.titulo}: ${criados.size} arquivo(s) criado(s).", Toast.LENGTH_LONG).show()
                    if (nav.selectedItemId == R.id.nav_inicio) atualizarAba() else nav.selectedItemId = R.id.nav_inicio
                }
            } catch (e: IllegalArgumentException) {
                principal.post { Toast.makeText(this, e.message ?: "Dados inválidos.", Toast.LENGTH_LONG).show() }
            } catch (e: IOException) {
                principal.post { Toast.makeText(this, "Não foi possível concluir. Verifique o arquivo e o espaço livre.", Toast.LENGTH_LONG).show() }
            } catch (e: RuntimeException) {
                principal.post { Toast.makeText(this, "Algo deu errado. Tente de novo.", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun perguntarTexto(pergunta: String, aoConfirmar: (String) -> Unit) {
        val campo = EditText(this).apply {
            hint = pergunta
            inputType = InputType.TYPE_CLASS_TEXT
            val margem = (16 * resources.displayMetrics.density).toInt()
            setPadding(margem, margem, margem, 0)
        }
        AlertDialog.Builder(this)
            .setTitle(pergunta)
            .setView(campo)
            .setPositiveButton("Continuar") { _, _ -> aoConfirmar(campo.text.toString().trim()) }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
