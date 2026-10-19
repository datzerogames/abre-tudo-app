package br.com.datzerogames.abretudo

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.text.InputType
import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import java.io.IOException

/** Abre o arquivo com um app instalado no aparelho. O visualizador de PDF chega na próxima etapa. */
fun Activity.abrirDocumento(doc: Documento) {
    Biblioteca.marcarAcesso(doc)
    val uri = uriDaBiblioteca(doc)
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

fun Activity.compartilharDocumento(doc: Documento) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType(doc.tipo)
        .putExtra(Intent.EXTRA_STREAM, uriDaBiblioteca(doc))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(Intent.createChooser(intent, "Compartilhar com"))
}

/** Menu de ações de um arquivo, aberto com um toque longo. */
fun Activity.opcoesDocumento(doc: Documento, aoMudar: () -> Unit) {
    val favoritoTexto = if (doc.favorito) "Tirar dos favoritos" else "Favoritar"
    val acoes = listOf(
        "Abrir" to { abrirDocumento(doc); aoMudar() },
        favoritoTexto to { Biblioteca.alternarFavorito(doc); aoMudar() },
        "Renomear" to { renomear(doc, aoMudar) },
        "Mover para" to { moverPara(doc, aoMudar) },
        "Duplicar" to { duplicar(doc, aoMudar) },
        "Compartilhar" to { compartilharDocumento(doc) },
        "Detalhes" to { mostrarDetalhes(doc) },
        "Excluir" to { confirmarExclusao(doc, aoMudar) },
    )
    AlertDialog.Builder(this)
        .setTitle(doc.nome)
        .setItems(acoes.map { it.first }.toTypedArray()) { _, which -> acoes[which].second() }
        .show()
}

fun Activity.mostrarDetalhes(doc: Documento) {
    val onde = "Guardado no app (recebido de outro app)"
    val modificado = DateUtils.getRelativeTimeSpanString(doc.adicionadoEm)
    val aberto = if (doc.ultimoAcesso > 0) DateUtils.getRelativeTimeSpanString(doc.ultimoAcesso) else "Nunca"
    AlertDialog.Builder(this)
        .setTitle("Detalhes")
        .setMessage(
            "Nome: ${doc.nome}\n" +
                "Tipo: ${doc.tipo}\n" +
                "Tamanho: ${Formatter.formatShortFileSize(this, doc.tamanho)}\n" +
                "Onde está: $onde\n" +
                "Modificado em: $modificado\n" +
                "Aberto pela última vez: $aberto"
        )
        .setPositiveButton("OK", null)
        .show()
}

private fun Activity.renomear(doc: Documento, aoMudar: () -> Unit) {
    val campo = EditText(this).apply {
        setText(doc.nome)
        setSelection(doc.nome.length)
        inputType = InputType.TYPE_CLASS_TEXT
        val margem = (16 * resources.displayMetrics.density).toInt()
        setPadding(margem, margem, margem, 0)
    }
    AlertDialog.Builder(this)
        .setTitle("Novo nome")
        .setView(campo)
        .setPositiveButton("Salvar") { _, _ ->
            val novo = campo.text.toString().trim()
            when {
                novo.isEmpty() -> Unit
                novo == doc.nome -> Unit
                else -> {
                    Biblioteca.renomear(doc, novo)
                    Toast.makeText(this, "Arquivo renomeado", Toast.LENGTH_SHORT).show()
                    aoMudar()
                }
            }
        }
        .setNegativeButton("Cancelar", null)
        .show()
}

private fun Activity.duplicar(doc: Documento, aoMudar: () -> Unit) {
    val copia = Biblioteca.duplicar(doc)
    if (copia == null) {
        Toast.makeText(this, "Não foi possível duplicar o arquivo.", Toast.LENGTH_LONG).show()
    } else {
        Toast.makeText(this, "Cópia criada", Toast.LENGTH_SHORT).show()
        aoMudar()
    }
}

/**
 * Move o arquivo para uma pasta liberada. Como o app só guarda uma cópia dos arquivos recebidos,
 * ao mover ele copia para a pasta escolhida e apaga a cópia interna.
 */
private fun Activity.moverPara(doc: Documento, aoMudar: () -> Unit) {
    val pastas = PastasLiberadas.listar(this)
    if (pastas.isEmpty()) {
        Toast.makeText(this, "Libere uma pasta na aba Arquivos primeiro", Toast.LENGTH_LONG).show()
        return
    }
    val nomes = pastas.map { DocumentFile.fromTreeUri(this, it)?.name ?: "Pasta" }.toTypedArray()
    AlertDialog.Builder(this)
        .setTitle("Mover para")
        .setItems(nomes) { _, which ->
            val pasta = DocumentFile.fromTreeUri(this, pastas[which])
            val destino = pasta?.createFile(doc.tipo, doc.nome)
            val copiou = destino != null && try {
                contentResolver.openOutputStream(destino.uri)?.use { saida ->
                    Biblioteca.arquivo(doc).inputStream().use { entrada -> entrada.copyTo(saida) }
                } != null
            } catch (e: IOException) {
                false
            }
            if (destino == null || !copiou) {
                destino?.delete()
                Toast.makeText(this, "Não foi possível mover o arquivo.", Toast.LENGTH_LONG).show()
            } else {
                Biblioteca.excluir(doc)
                Toast.makeText(this, "Movido para ${nomes[which]}", Toast.LENGTH_SHORT).show()
                aoMudar()
            }
        }
        .show()
}

private fun Activity.confirmarExclusao(doc: Documento, aoMudar: () -> Unit) {
    AlertDialog.Builder(this)
        .setTitle("Excluir arquivo?")
        .setMessage("\"${doc.nome}\" será apagado do aparelho. Não dá para desfazer.")
        .setPositiveButton("Excluir") { _, _ ->
            Biblioteca.excluir(doc)
            aoMudar()
        }
        .setNegativeButton("Cancelar", null)
        .show()
}

/** Endereço do arquivo para outros apps lerem, via FileProvider. */
fun Activity.uriDaBiblioteca(doc: Documento): Uri =
    FileProvider.getUriForFile(this, "$packageName.fileprovider", Biblioteca.arquivo(doc))
