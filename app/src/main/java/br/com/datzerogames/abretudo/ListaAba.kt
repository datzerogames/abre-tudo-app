package br.com.datzerogames.abretudo

import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Base das abas que mostram uma lista. Cuida dos cliques e da mensagem de lista vazia;
 * cada aba diz só quais itens mostrar.
 */
abstract class ListaAba(layout: Int) : Fragment(layout), Aba {
    protected lateinit var lista: RecyclerView
    protected lateinit var vazio: TextView

    private val adapter = ItensAdapter(
        aoClicar = { clicar(it) },
        aoSegurar = { segurar(it) },
        aoFavoritar = {
            Biblioteca.alternarFavorito(it)
            atualizar()
        },
    )

    override fun onViewCreated(view: View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lista = view.findViewById(R.id.lista)
        vazio = view.findViewById(R.id.vazio)
        lista.layoutManager = LinearLayoutManager(requireContext())
        lista.adapter = adapter
        onConfigurar(view)
        atualizar()
    }

    /** Ajustes extras da tela, feitos antes da primeira atualização da lista. */
    open fun onConfigurar(view: View) {}

    abstract fun itens(): List<Item>

    abstract fun mensagemVazio(): String

    override fun atualizar() {
        if (view == null) return
        val lista = itens()
        adapter.atualizar(lista)
        vazio.text = mensagemVazio()
        vazio.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun clicar(item: Item) {
        when (item) {
            is Item.Arquivo -> requireActivity().abrirDocumento(item.doc)
            is Item.PastaLiberada -> PastaLiberadaActivity.abrir(requireContext(), item.uri)
            is Item.FerramentaItem -> (requireActivity() as MainActivity).iniciarFerramenta(item.ferramenta)
            is Item.Acao -> (requireActivity() as MainActivity).liberarNovaPasta()
            is Item.Titulo -> Unit
        }
    }

    private fun segurar(item: Item) {
        when (item) {
            is Item.Arquivo -> requireActivity().opcoesDocumento(item.doc) { atualizar() }
            is Item.PastaLiberada -> confirmarRemocaoDaPasta(item)
            else -> Unit
        }
    }

    private fun confirmarRemocaoDaPasta(item: Item.PastaLiberada) {
        val nome = androidx.documentfile.provider.DocumentFile.fromTreeUri(requireContext(), item.uri)?.name ?: "Pasta"
        AlertDialog.Builder(requireContext())
            .setMessage("Remover acesso à pasta $nome? Os arquivos dentro dela não são apagados.")
            .setPositiveButton("Remover") { _, _ ->
                PastasLiberadas.remover(requireContext(), item.uri)
                atualizar()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}

/** Aba Início: atalhos, depois os arquivos recentes. */
class InicioFragment : ListaAba(R.layout.fragment_inicio) {
    override fun onConfigurar(view: View) {
        val principal = requireActivity() as MainActivity
        view.findViewById<View>(R.id.atalhoJuntar).setOnClickListener { principal.iniciarFerramenta(Ferramenta.JUNTAR) }
        view.findViewById<View>(R.id.atalhoDividir).setOnClickListener { principal.iniciarFerramenta(Ferramenta.DIVIDIR) }
        view.findViewById<View>(R.id.atalhoComprimir).setOnClickListener { principal.iniciarFerramenta(Ferramenta.COMPRIMIR) }
        view.findViewById<View>(R.id.atalhoFotos).setOnClickListener { principal.iniciarFerramenta(Ferramenta.FOTOS_PDF) }
        view.findViewById<View>(R.id.verTudo).setOnClickListener { principal.irPara(R.id.nav_arquivos) }
    }

    override fun itens(): List<Item> = Biblioteca.recentes().take(5).map { Item.Arquivo(it) }

    override fun mensagemVazio(): String =
        "Nada aberto ainda.\nOs arquivos que você abrir ou receber pelo WhatsApp, e-mail e outros apps aparecem aqui."
}

/** Aba Arquivos: busca, ordenação, pastas liberadas e todos os arquivos. */
class ArquivosFragment : ListaAba(R.layout.fragment_arquivos) {
    private var ordem = Ordem.NOME
    private lateinit var busca: com.google.android.material.textfield.TextInputEditText
    private lateinit var botaoOrdem: com.google.android.material.button.MaterialButton
    private lateinit var total: TextView

    override fun onConfigurar(view: View) {
        busca = view.findViewById(R.id.busca)
        botaoOrdem = view.findViewById(R.id.ordem)
        total = view.findViewById(R.id.totalArquivos)
        busca.doAfterTextChanged { atualizar() }
        botaoOrdem.setOnClickListener {
            ordem = ordem.proxima()
            atualizar()
        }
    }

    override fun itens(): List<Item> {
        if (!::busca.isInitialized) return emptyList()
        val termo = busca.text?.toString()?.trim().orEmpty()
        val docs = ordem.ordenar(
            Biblioteca.todos().filter { termo.isEmpty() || it.nome.contains(termo, ignoreCase = true) }
        )
        val lista = mutableListOf<Item>()
        if (termo.isEmpty()) {
            lista.add(Item.Titulo("Pastas liberadas"))
            PastasLiberadas.listar(requireContext()).forEach { lista.add(Item.PastaLiberada(it)) }
            lista.add(Item.Acao("Liberar pasta"))
        }
        docs.forEach { lista.add(Item.Arquivo(it)) }
        return lista
    }

    override fun atualizar() {
        super.atualizar()
        if (view == null || !::busca.isInitialized) return
        botaoOrdem.text = "Ordenar: ${ordem.rotulo}"
        total.text = "Total: ${Biblioteca.todos().size}"
    }

    override fun mensagemVazio(): String =
        if (busca.text.isNullOrBlank()) "Nenhum arquivo ainda.\nAbra um arquivo pelo botão + ou libere uma pasta acima."
        else "Nenhum arquivo encontrado com esse nome."
}

/** Aba Favoritos. */
class FavoritosFragment : ListaAba(R.layout.fragment_lista) {
    override fun itens(): List<Item> = Ordem.NOME.ordenar(Biblioteca.favoritos()).map { Item.Arquivo(it) }

    override fun mensagemVazio(): String =
        "Sem favoritos.\nToque na estrela de um arquivo para guardá-lo aqui."
}

/** Aba Ferramentas, agrupadas como no app original. */
class FerramentasFragment : ListaAba(R.layout.fragment_lista) {
    override fun itens(): List<Item> =
        Ferramenta.entries.groupBy { it.grupo }.flatMap { (grupo, ferramentas) ->
            listOf<Item>(Item.Titulo(grupo)) + ferramentas.map { Item.FerramentaItem(it) }
        }

    override fun mensagemVazio(): String = ""
}
