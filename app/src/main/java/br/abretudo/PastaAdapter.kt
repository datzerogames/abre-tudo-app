package br.abretudo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PastaAdapter(
    private val aoAbrir: (Pasta) -> Unit,
    private val aoSegurar: (Pasta) -> Unit,
) : RecyclerView.Adapter<PastaAdapter.Holder>() {

    private var itens: List<Pasta> = emptyList()

    fun atualizar(novas: List<Pasta>) {
        itens = novas
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = itens.size

    override fun onCreateViewHolder(pai: ViewGroup, tipo: Int): Holder {
        val view = LayoutInflater.from(pai.context).inflate(R.layout.item_pasta, pai, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, posicao: Int) {
        val pasta = itens[posicao]
        val quantidade = Biblioteca.contarNaPasta(pasta.id)
        holder.nome.text = pasta.nome
        holder.quantidade.text = if (quantidade == 1) "1 arquivo" else "$quantidade arquivos"
        holder.itemView.setOnClickListener { aoAbrir(pasta) }
        holder.itemView.setOnLongClickListener {
            aoSegurar(pasta)
            true
        }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val nome: TextView = view.findViewById(R.id.nome)
        val quantidade: TextView = view.findViewById(R.id.quantidade)
    }
}
