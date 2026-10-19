package br.abretudo

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider

/** Abre o arquivo com um app instalado no aparelho. O visualizador de PDF chega na próxima versão. */
fun Activity.abrirDocumento(doc: Documento) {
    Biblioteca.marcarAcesso(doc)
    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", Biblioteca.arquivo(doc))
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, doc.tipo)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    intent.clipData = ClipData.newRawUri("", uri)
    try {
        startActivity(Intent.createChooser(intent, "Abrir com"))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, "Nenhum app instalado abre este tipo de arquivo.", Toast.LENGTH_LONG).show()
    }
}

fun Activity.opcoesDocumento(doc: Documento, aoMudar: () -> Unit) {
    AlertDialog.Builder(this)
        .setTitle(doc.nome)
        .setItems(arrayOf("Mover para pasta", "Excluir")) { _, which ->
            when (which) {
                0 -> escolherPasta(doc, aoMudar)
                1 -> confirmarExclusao(doc, aoMudar)
            }
        }
        .show()
}

fun Activity.novaPasta(aoCriar: () -> Unit) {
    val campo = EditText(this).apply {
        hint = "Nome da pasta"
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        val margem = (16 * resources.displayMetrics.density).toInt()
        setPadding(margem, margem, margem, 0)
    }
    AlertDialog.Builder(this)
        .setTitle("Nova pasta")
        .setView(campo)
        .setPositiveButton("Criar") { _, _ ->
            val nome = campo.text.toString().trim()
            if (nome.isNotEmpty()) {
                Biblioteca.criarPasta(nome)
                aoCriar()
            }
        }
        .setNegativeButton("Cancelar", null)
        .show()
}

fun Activity.confirmarExcluirPasta(pasta: Pasta, aoMudar: () -> Unit) {
    AlertDialog.Builder(this)
        .setMessage("Excluir a pasta \"${pasta.nome}\"? Os arquivos dela não são apagados, só saem da pasta.")
        .setPositiveButton("Excluir") { _, _ ->
            Biblioteca.excluirPasta(pasta)
            aoMudar()
        }
        .setNegativeButton("Cancelar", null)
        .show()
}

private fun Activity.escolherPasta(doc: Documento, aoMudar: () -> Unit) {
    val pastas = Biblioteca.pastas()
    if (pastas.isEmpty()) {
        Toast.makeText(this, "Crie uma pasta primeiro, no menu.", Toast.LENGTH_LONG).show()
        return
    }
    val nomes = (listOf("Sem pasta") + pastas.map { it.nome }).toTypedArray()
    AlertDialog.Builder(this)
        .setTitle("Mover para")
        .setItems(nomes) { _, which ->
            Biblioteca.mover(doc, pastas.getOrNull(which - 1)?.id)
            aoMudar()
        }
        .show()
}

private fun Activity.confirmarExclusao(doc: Documento, aoMudar: () -> Unit) {
    AlertDialog.Builder(this)
        .setMessage("Excluir \"${doc.nome}\" do Abre Tudo? O arquivo original no aparelho não é alterado.")
        .setPositiveButton("Excluir") { _, _ ->
            Biblioteca.excluir(doc)
            aoMudar()
        }
        .setNegativeButton("Cancelar", null)
        .show()
}
