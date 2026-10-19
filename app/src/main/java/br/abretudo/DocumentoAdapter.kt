package br.abretudo

import android.text.format.DateUtils
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DocumentoAdapter(
    private val aoAbrir: (Documento) -> Unit,
    private val aoFavoritar: (Documento) -> Unit,
    private val aoSegurar: (Documento) -> Unit,
) : RecyclerView.Adapter<DocumentoAdapter.Holder>() {

    private var itens: List<Documento> = emptyList()

    fun atualizar(novos: List<Documento>) {
        itens = novos
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = itens.size

    override fun onCreateViewHolder(pai: ViewGroup, tipo: Int): Holder {
        val view = LayoutInflater.from(pai.context).inflate(R.layout.item_documento, pai, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, posicao: Int) {
        val doc = itens[posicao]
        val contexto = holder.itemView.context
        holder.nome.text = doc.nome
        holder.detalhes.text = "${Formatter.formatShortFileSize(contexto, doc.tamanho)} · " +
            DateUtils.getRelativeTimeSpanString(doc.ultimoAcesso)
        holder.favorito.setImageResource(
            if (doc.favorito) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off
        )
        holder.itemView.setOnClickListener { aoAbrir(doc) }
        holder.itemView.setOnLongClickListener {
            aoSegurar(doc)
            true
        }
        holder.favorito.setOnClickListener { aoFavoritar(doc) }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val nome: TextView = view.findViewById(R.id.nome)
        val detalhes: TextView = view.findViewById(R.id.detalhes)
        val favorito: ImageButton = view.findViewById(R.id.favorito)
    }
}
