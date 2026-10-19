package br.abretudo

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar

class TemasActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_temas)

        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.temas)

        val botoes = listOf(
            R.id.modoSistema to Tema.MODO_SISTEMA,
            R.id.modoClaro to Tema.MODO_CLARO,
            R.id.modoEscuro to Tema.MODO_ESCURO,
        )
        val grupo: RadioGroup = findViewById(R.id.modos)
        botoes.forEach { (id, modo) ->
            if (modo == Tema.modo(this)) findViewById<RadioButton>(id).isChecked = true
        }
        grupo.setOnCheckedChangeListener { _, checado ->
            Tema.definirModo(this, botoes.first { it.first == checado }.second)
        }

        val cores: RecyclerView = findViewById(R.id.cores)
        cores.layoutManager = LinearLayoutManager(this)
        cores.adapter = TemaAdapter(Tema.corAtual(this)) { indice ->
            Tema.definirCor(this, indice)
            recreate()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

private class TemaAdapter(
    private val selecionada: Int,
    private val aoEscolher: (Int) -> Unit,
) : RecyclerView.Adapter<TemaAdapter.Holder>() {

    override fun getItemCount(): Int = Tema.cores.size

    override fun onCreateViewHolder(pai: ViewGroup, tipo: Int): Holder {
        val view = LayoutInflater.from(pai.context).inflate(R.layout.item_tema, pai, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, posicao: Int) {
        val tema = Tema.cores[posicao]
        holder.swatch.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(tema.hex))
        }
        holder.nome.text = tema.nome
        holder.marca.visibility = if (posicao == selecionada) View.VISIBLE else View.INVISIBLE
        holder.itemView.setOnClickListener { aoEscolher(posicao) }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val swatch: View = view.findViewById(R.id.swatch)
        val nome: TextView = view.findViewById(R.id.nome)
        val marca: TextView = view.findViewById(R.id.marca)
    }
}
