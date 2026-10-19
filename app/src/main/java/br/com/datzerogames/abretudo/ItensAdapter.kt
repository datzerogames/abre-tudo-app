package br.com.datzerogames.abretudo

import android.net.Uri
import android.text.format.DateUtils
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.RecyclerView

/** Uma linha das listas do app. Cada aba mostra uma mistura desses tipos. */
sealed class Item {
    data class Titulo(val texto: String) : Item()
    data class Arquivo(val doc: Documento) : Item()
    data class PastaLiberada(val uri: Uri) : Item()
    data class FerramentaItem(val ferramenta: Ferramenta) : Item()
    data class Acao(val texto: String) : Item()
}

/** Filtro por tipo de arquivo, como os chips da aba Arquivos. */
enum class Categoria(val rotulo: String) {
    TODOS("Todos"),
    PDF("PDF"),
    WORD("Word"),
    EXCEL("Excel"),
    POWERPOINT("PowerPoint"),
    IMAGENS("Imagens"),
    OUTROS("Outros");

    fun inclui(doc: Documento): Boolean {
        val tipo = doc.tipo
        return when (this) {
            TODOS -> true
            PDF -> tipo == "application/pdf"
            WORD -> tipo.contains("msword") || tipo.contains("wordprocessingml") || tipo.contains("opendocument.text")
            EXCEL -> tipo.contains("excel") || tipo.contains("spreadsheetml") || tipo.contains("opendocument.spreadsheet") || tipo == "text/csv"
            POWERPOINT -> tipo.contains("powerpoint") || tipo.contains("presentationml") || tipo.contains("opendocument.presentation")
            IMAGENS -> tipo.startsWith("image/")
            OUTROS -> Categoria.values().filter { it != OUTROS && it != TODOS }.none { it.inclui(doc) }
        }
    }
}

/** Sigla que aparece no bloco preto de cada arquivo. */
fun rotuloDoc(doc: Documento): String = when (Categoria.values().firstOrNull { it != Categoria.TODOS && it != Categoria.OUTROS && it.inclui(doc) }) {
    Categoria.PDF -> "PDF"
    Categoria.WORD -> "DOC"
    Categoria.EXCEL -> "XLS"
    Categoria.POWERPOINT -> "PPT"
    Categoria.IMAGENS -> "IMG"
    else -> "ARQ"
}

/** Ícone de cada ferramenta, usado no cartão da aba Ferramentas. */
fun iconeDaFerramenta(ferramenta: Ferramenta): Int = when (ferramenta) {
    Ferramenta.JUNTAR -> android.R.drawable.ic_menu_sort_by_size
    Ferramenta.DIVIDIR -> android.R.drawable.ic_menu_crop
    Ferramenta.COMPRIMIR, Ferramenta.COMPRIMIR_ENVIO -> android.R.drawable.arrow_down_float
    Ferramenta.ORGANIZAR -> android.R.drawable.ic_menu_rotate
    Ferramenta.SENHA -> android.R.drawable.ic_lock_idle_lock
    Ferramenta.MARCA -> android.R.drawable.ic_menu_view
    Ferramenta.NUMERAR -> android.R.drawable.ic_menu_sort_alphabetically
    Ferramenta.ASSINAR, Ferramenta.FORMULARIO, Ferramenta.OCR -> android.R.drawable.ic_menu_edit
    Ferramenta.ESCANEAR -> android.R.drawable.ic_menu_camera
    Ferramenta.FOTOS_PDF -> android.R.drawable.ic_menu_gallery
    Ferramenta.PDF_FOTOS -> android.R.drawable.ic_menu_camera
    Ferramenta.BOLETO -> android.R.drawable.ic_menu_agenda
    Ferramenta.RG_CPF -> android.R.drawable.ic_menu_myplaces
    Ferramenta.TRADUZIR -> android.R.drawable.ic_menu_mylocation
    Ferramenta.LER_VOZ -> android.R.drawable.ic_lock_silent_mode_off
}

class ItensAdapter(
    private val aoClicar: (Item) -> Unit,
    private val aoSegurar: (Item) -> Unit,
    private val aoFavoritar: (Documento) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var itens: List<Item> = emptyList()

    fun atualizar(novos: List<Item>) {
        itens = novos
        notifyDataSetChanged()
    }

    /** Usado pela grade da aba Ferramentas para o título ocupar a linha inteira. */
    fun ehTitulo(posicao: Int): Boolean = itens[posicao] is Item.Titulo

    override fun getItemCount(): Int = itens.size

    override fun getItemViewType(posicao: Int): Int = when (itens[posicao]) {
        is Item.Titulo -> TIPO_TITULO
        is Item.FerramentaItem -> TIPO_FERRAMENTA
        else -> TIPO_LINHA
    }

    override fun onCreateViewHolder(pai: ViewGroup, tipo: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(pai.context)
        return when (tipo) {
            TIPO_TITULO -> TituloHolder(inflater.inflate(R.layout.item_titulo, pai, false))
            TIPO_FERRAMENTA -> FerramentaHolder(inflater.inflate(R.layout.item_ferramenta, pai, false))
            else -> LinhaHolder(inflater.inflate(R.layout.item_linha, pai, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, posicao: Int) {
        val item = itens[posicao]
        when (holder) {
            is TituloHolder -> {
                holder.texto.text = (item as Item.Titulo).texto
                holder.barra.setBackgroundColor(Tema.corPrimaria(holder.itemView.context))
            }
            is FerramentaHolder -> {
                val ferramenta = (item as Item.FerramentaItem).ferramenta
                holder.icone.setImageResource(iconeDaFerramenta(ferramenta))
                holder.titulo.text = ferramenta.titulo
                holder.itemView.setOnClickListener { aoClicar(item) }
            }
            is LinhaHolder -> ligarLinha(holder, item)
        }
    }

    private fun ligarLinha(holder: LinhaHolder, item: Item) {
        val contexto = holder.itemView.context
        val cor = Tema.corPrimaria(contexto)
        holder.rotulo.visibility = View.GONE
        holder.icone.visibility = View.VISIBLE
        holder.favorito.visibility = View.GONE
        holder.mais.visibility = View.GONE
        holder.subtitulo.visibility = View.VISIBLE
        when (item) {
            is Item.Arquivo -> {
                val doc = item.doc
                holder.icone.visibility = View.GONE
                holder.rotulo.visibility = View.VISIBLE
                holder.rotulo.text = rotuloDoc(doc)
                holder.titulo.text = doc.nome
                holder.subtitulo.text = "${Formatter.formatShortFileSize(contexto, doc.tamanho)} · " +
                    DateUtils.getRelativeTimeSpanString(doc.ultimoAcesso.coerceAtLeast(doc.adicionadoEm))
                holder.favorito.visibility = View.VISIBLE
                holder.favorito.setImageResource(
                    if (doc.favorito) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off
                )
                holder.favorito.setColorFilter(cor)
                holder.favorito.setOnClickListener { aoFavoritar(doc) }
                holder.mais.visibility = View.VISIBLE
                holder.mais.setOnClickListener { aoSegurar(item) }
            }
            is Item.PastaLiberada -> {
                holder.icone.setImageResource(android.R.drawable.ic_menu_myplaces)
                holder.titulo.text = DocumentFile.fromTreeUri(contexto, item.uri)?.name ?: "Pasta"
                holder.subtitulo.text = "Pasta liberada"
            }
            is Item.Acao -> {
                holder.icone.setImageResource(android.R.drawable.ic_input_add)
                holder.titulo.text = item.texto
                holder.subtitulo.visibility = View.GONE
            }
            is Item.Titulo, is Item.FerramentaItem -> Unit
        }
        holder.icone.setColorFilter(cor)
        holder.itemView.setOnClickListener { aoClicar(item) }
        holder.itemView.setOnLongClickListener {
            aoSegurar(item)
            true
        }
    }

    class TituloHolder(view: View) : RecyclerView.ViewHolder(view) {
        val barra: View = view.findViewById(R.id.barra)
        val texto: TextView = view.findViewById(R.id.texto)
    }

    class FerramentaHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icone: ImageView = view.findViewById(R.id.icone)
        val titulo: TextView = view.findViewById(R.id.titulo)
    }

    class LinhaHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icone: ImageView = view.findViewById(R.id.icone)
        val rotulo: TextView = view.findViewById(R.id.rotulo)
        val titulo: TextView = view.findViewById(R.id.titulo)
        val subtitulo: TextView = view.findViewById(R.id.subtitulo)
        val favorito: ImageButton = view.findViewById(R.id.favorito)
        val mais: ImageButton = view.findViewById(R.id.mais)
    }

    private companion object {
        const val TIPO_TITULO = 0
        const val TIPO_LINHA = 1
        const val TIPO_FERRAMENTA = 2
    }
}
