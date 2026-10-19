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

    override fun getItemCount(): Int = itens.size

    override fun getItemViewType(posicao: Int): Int = when (itens[posicao]) {
        is Item.Titulo -> TIPO_TITULO
        else -> TIPO_LINHA
    }

    override fun onCreateViewHolder(pai: ViewGroup, tipo: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(pai.context)
        return if (tipo == TIPO_TITULO) {
            TituloHolder(inflater.inflate(R.layout.item_titulo, pai, false))
        } else {
            LinhaHolder(inflater.inflate(R.layout.item_linha, pai, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, posicao: Int) {
        val item = itens[posicao]
        when (holder) {
            is TituloHolder -> holder.texto.text = (item as Item.Titulo).texto
            is LinhaHolder -> ligarLinha(holder, item)
        }
    }

    private fun ligarLinha(holder: LinhaHolder, item: Item) {
        val contexto = holder.itemView.context
        holder.favorito.visibility = View.GONE
        holder.subtitulo.visibility = View.VISIBLE
        when (item) {
            is Item.Arquivo -> {
                val doc = item.doc
                holder.icone.setImageResource(android.R.drawable.ic_menu_agenda)
                holder.titulo.text = doc.nome
                holder.subtitulo.text = "${Formatter.formatShortFileSize(contexto, doc.tamanho)} · " +
                    DateUtils.getRelativeTimeSpanString(doc.ultimoAcesso.coerceAtLeast(doc.adicionadoEm))
                holder.favorito.visibility = View.VISIBLE
                holder.favorito.setImageResource(
                    if (doc.favorito) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off
                )
                holder.favorito.setOnClickListener { aoFavoritar(doc) }
            }
            is Item.PastaLiberada -> {
                holder.icone.setImageResource(android.R.drawable.ic_menu_myplaces)
                holder.titulo.text = DocumentFile.fromTreeUri(contexto, item.uri)?.name ?: "Pasta"
                holder.subtitulo.text = "Pasta liberada"
            }
            is Item.FerramentaItem -> {
                holder.icone.setImageResource(android.R.drawable.ic_menu_manage)
                holder.titulo.text = item.ferramenta.titulo
                holder.subtitulo.text = if (item.ferramenta.implementada) "" else "Em breve"
                holder.subtitulo.visibility = if (item.ferramenta.implementada) View.GONE else View.VISIBLE
            }
            is Item.Acao -> {
                holder.icone.setImageResource(android.R.drawable.ic_input_add)
                holder.titulo.text = item.texto
                holder.subtitulo.visibility = View.GONE
            }
            is Item.Titulo -> Unit
        }
        holder.itemView.setOnClickListener { aoClicar(item) }
        holder.itemView.setOnLongClickListener {
            aoSegurar(item)
            true
        }
    }

    class TituloHolder(view: View) : RecyclerView.ViewHolder(view) {
        val texto: TextView = view as TextView
    }

    class LinhaHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icone: ImageView = view.findViewById(R.id.icone)
        val titulo: TextView = view.findViewById(R.id.titulo)
        val subtitulo: TextView = view.findViewById(R.id.subtitulo)
        val favorito: ImageButton = view.findViewById(R.id.favorito)
    }

    private companion object {
        const val TIPO_TITULO = 0
        const val TIPO_LINHA = 1
    }
}
