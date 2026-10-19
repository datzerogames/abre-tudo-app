package br.com.datzerogames.abretudo

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import androidx.core.widget.doAfterTextChanged
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

/**
 * Base das abas que mostram uma lista. Cuida dos cliques e do estado de lista vazia;
 * cada aba diz só quais itens mostrar.
 */
abstract class ListaAba(layout: Int) : Fragment(layout), Aba {
    protected lateinit var lista: RecyclerView
    protected lateinit var vazio: View

    protected val adapter = ItensAdapter(
        aoClicar = { clicar(it) },
        aoSegurar = { segurar(it) },
        aoFavoritar = {
            Biblioteca.alternarFavorito(it)
            atualizar()
        },
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lista = view.findViewById(R.id.lista)
        vazio = view.findViewById(R.id.vazio)
        lista.layoutManager = configurarLayout()
        lista.adapter = adapter
        onConfigurar(view)
        atualizar()
    }

    /** Ajustes extras da tela, feitos antes da primeira atualização da lista. */
    open fun onConfigurar(view: View) {}

    open fun configurarLayout(): RecyclerView.LayoutManager = LinearLayoutManager(requireContext())

    abstract fun itens(): List<Item>

    /** Título e texto da lista vazia, separados por quebra de linha. Vazio = sem estado vazio. */
    abstract fun mensagemVazio(): String

    open fun iconeVazio(): Int = android.R.drawable.btn_star_big_off

    override fun atualizar() {
        if (view == null) return
        val itens = itens()
        adapter.atualizar(itens)
        val mensagem = mensagemVazio()
        val partes = mensagem.split("\n", limit = 2)
        vazio.findViewById<TextView>(R.id.vazioTitulo).text = partes[0]
        vazio.findViewById<TextView>(R.id.vazioTexto).text = partes.getOrElse(1) { "" }
        vazio.findViewById<ImageView>(R.id.vazioIcone).setImageResource(iconeVazio())
        vazio.visibility = if (itens.isEmpty() && mensagem.isNotEmpty()) View.VISIBLE else View.GONE
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
        val nome = DocumentFile.fromTreeUri(requireContext(), item.uri)?.name ?: "Pasta"
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

/** Aba Início: atalhos, cartão de boas-vindas, contadores e os recentes. */
class InicioFragment : ListaAba(R.layout.fragment_inicio) {
    override fun onConfigurar(view: View) {
        val principal = requireActivity() as MainActivity
        val cor = Tema.corPrimaria(requireContext())

        listOf(R.id.iconeEscanear, R.id.iconeJuntar, R.id.iconeComprimir, R.id.iconeConverter, R.id.iconeAssinar)
            .forEach { view.findViewById<ImageView>(it).setColorFilter(cor) }
        view.findViewById<TextView>(R.id.verTudo).setTextColor(cor)
        view.findViewById<View>(R.id.barraRecentes).setBackgroundColor(cor)

        // O cartão de boas-vindas usa a cor do tema, com um tom mais claro na ponta.
        val hero = view.findViewById<View>(R.id.cartaoBoasVindas)
        hero.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(cor, ColorUtils.blendARGB(cor, android.graphics.Color.WHITE, 0.2f)),
        ).apply { cornerRadius = 28 * resources.displayMetrics.density }

        view.findViewById<View>(R.id.atalhoEscanear).setOnClickListener {
            principal.iniciarFerramenta(Ferramenta.ESCANEAR)
        }
        view.findViewById<View>(R.id.atalhoJuntar).setOnClickListener { principal.iniciarFerramenta(Ferramenta.JUNTAR) }
        view.findViewById<View>(R.id.atalhoComprimir).setOnClickListener { principal.iniciarFerramenta(Ferramenta.COMPRIMIR) }
        view.findViewById<View>(R.id.atalhoConverter).setOnClickListener {
            Toast.makeText(requireContext(), "Converter chega numa próxima versão.", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<View>(R.id.atalhoAssinar).setOnClickListener { principal.iniciarFerramenta(Ferramenta.ASSINAR) }
        view.findViewById<MaterialButton>(R.id.abrirArquivo).setOnClickListener { principal.abrirArquivo() }
        view.findViewById<View>(R.id.verTudo).setOnClickListener { principal.irPara(R.id.nav_arquivos) }
    }

    override fun itens(): List<Item> = Biblioteca.recentes().take(5).map { Item.Arquivo(it) }

    override fun atualizar() {
        super.atualizar()
        if (view == null) return
        val todos = Biblioteca.todos()
        val pdfs = todos.count { it.tipo == "application/pdf" }
        view?.findViewById<TextView>(R.id.contaPdf)?.text = pdfs.toString()
        view?.findViewById<TextView>(R.id.contaDocumentos)?.text = (todos.size - pdfs).toString()
    }

    override fun mensagemVazio(): String =
        "Nada aberto ainda.\nOs arquivos que você abrir ou receber pelo WhatsApp, e-mail e outros apps aparecem aqui."
}

/** Aba Arquivos: pastas liberadas, filtros por tipo, ordenação e busca. */
class ArquivosFragment : ListaAba(R.layout.fragment_arquivos) {
    private var ordem = Ordem.DATA
    private var categoria = Categoria.TODOS
    private lateinit var botaoOrdem: MaterialButton
    private lateinit var total: TextView

    override fun onConfigurar(view: View) {
        val cor = Tema.corPrimaria(requireContext())
        view.findViewById<View>(R.id.barra).setBackgroundColor(cor)
        view.findViewById<TextView>(R.id.adicionarPasta).setTextColor(cor)
        view.findViewById<TextView>(R.id.adicionarPasta).setOnClickListener {
            (requireActivity() as MainActivity).liberarNovaPasta()
        }
        view.findViewById<MaterialButton>(R.id.escolherPasta).setOnClickListener {
            (requireActivity() as MainActivity).liberarNovaPasta()
        }

        botaoOrdem = view.findViewById(R.id.ordem)
        total = view.findViewById(R.id.totalArquivos)
        botaoOrdem.setOnClickListener {
            ordem = ordem.proxima()
            atualizar()
        }
        view.findViewById<View>(R.id.atualizar).setOnClickListener { atualizar() }

        val filtros: ChipGroup = view.findViewById(R.id.filtros)
        Categoria.values().forEach { cat ->
            val chip = Chip(requireContext()).apply {
                text = cat.rotulo
                isCheckable = true
                isChecked = cat == categoria
                tag = cat
            }
            filtros.addView(chip)
        }
        filtros.setOnCheckedStateChangeListener { grupo, ids ->
            val escolhido = ids.firstOrNull()?.let { grupo.findViewById<Chip>(it)?.tag as? Categoria }
            categoria = escolhido ?: Categoria.TODOS
            atualizar()
        }
    }

    private fun termoBusca(): String = (requireActivity() as MainActivity).termoBusca.trim()

    private fun docsFiltrados() = ordem.ordenar(
        Biblioteca.todos().filter { categoria.inclui(it) && (termoBusca().isEmpty() || it.nome.contains(termoBusca(), ignoreCase = true)) }
    )

    override fun itens(): List<Item> {
        if (!::botaoOrdem.isInitialized) return emptyList()
        val lista = mutableListOf<Item>()
        if (termoBusca().isEmpty() && categoria == Categoria.TODOS) {
            PastasLiberadas.listar(requireContext()).forEach { lista.add(Item.PastaLiberada(it)) }
        }
        docsFiltrados().forEach { lista.add(Item.Arquivo(it)) }
        return lista
    }

    override fun atualizar() {
        super.atualizar()
        if (view == null || !::botaoOrdem.isInitialized) return
        botaoOrdem.text = "Ordenar: ${ordem.rotulo}"
        total.text = "Total: ${docsFiltrados().size}"
    }

    override fun mensagemVazio(): String = when {
        termoBusca().isNotEmpty() -> "Nenhum arquivo encontrado.\nTente outro nome."
        categoria != Categoria.TODOS -> "Nenhum arquivo deste tipo.\nEscolha outro filtro ou adicione um arquivo."
        else -> "Nenhum arquivo ainda.\nAbra um arquivo pelo botão + ou libere uma pasta acima."
    }

    override fun iconeVazio(): Int = android.R.drawable.ic_menu_agenda
}

/** Aba Favoritos. */
class FavoritosFragment : ListaAba(R.layout.fragment_lista) {
    override fun itens(): List<Item> = Ordem.NOME.ordenar(Biblioteca.favoritos()).map { Item.Arquivo(it) }

    override fun mensagemVazio(): String =
        "Sem favoritos\nToque na estrela de um arquivo para guardá-lo aqui."
}

/** Aba Ferramentas: grade de três colunas, agrupada por tipo, como no original. */
class FerramentasFragment : ListaAba(R.layout.fragment_lista) {
    override fun configurarLayout(): RecyclerView.LayoutManager {
        val grade = GridLayoutManager(requireContext(), 3)
        grade.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(posicao: Int): Int = if (adapter.ehTitulo(posicao)) 3 else 1
        }
        return grade
    }

    override fun itens(): List<Item> =
        Ferramenta.entries.groupBy { it.grupo }.flatMap { (grupo, ferramentas) ->
            listOf<Item>(Item.Titulo(grupo)) + ferramentas.map { Item.FerramentaItem(it) }
        }

    override fun mensagemVazio(): String = ""
}
