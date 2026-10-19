package br.com.datzerogames.abretudo

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

data class Documento(
    val id: String,
    var nome: String,
    val arquivo: String,
    val tamanho: Long,
    val tipo: String,
    val adicionadoEm: Long,
    /** 0 quando o arquivo nunca foi aberto nem recebido (ou o histórico foi limpo). */
    var ultimoAcesso: Long,
    var favorito: Boolean,
) {
    fun paraJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("nome", nome)
        .put("arquivo", arquivo)
        .put("tamanho", tamanho)
        .put("tipo", tipo)
        .put("adicionadoEm", adicionadoEm)
        .put("ultimoAcesso", ultimoAcesso)
        .put("favorito", favorito)

    companion object {
        fun deJson(o: JSONObject) = Documento(
            id = o.getString("id"),
            nome = o.getString("nome"),
            arquivo = o.getString("arquivo"),
            tamanho = o.getLong("tamanho"),
            tipo = o.getString("tipo"),
            adicionadoEm = o.getLong("adicionadoEm"),
            ultimoAcesso = o.getLong("ultimoAcesso"),
            favorito = o.getBoolean("favorito"),
        )
    }
}

enum class Ordem(val rotulo: String) {
    NOME("Nome"),
    DATA("Data"),
    TAMANHO("Tamanho"),
    TIPO("Tipo");

    fun proxima(): Ordem = entries[(ordinal + 1) % entries.size]

    fun ordenar(docs: List<Documento>): List<Documento> = when (this) {
        NOME -> docs.sortedBy { it.nome.lowercase() }
        DATA -> docs.sortedByDescending { it.ultimoAcesso.coerceAtLeast(it.adicionadoEm) }
        TAMANHO -> docs.sortedByDescending { it.tamanho }
        TIPO -> docs.sortedWith(compareBy({ it.tipo }, { it.nome.lowercase() }))
    }
}

/**
 * Guarda os arquivos do usuário dentro do próprio app (filesDir/biblioteca) e o índice
 * em biblioteca.json. Nada é enviado para fora do aparelho.
 */
object Biblioteca {
    private lateinit var contexto: Context
    private val documentosSalvos = mutableListOf<Documento>()

    /** Carrega o índice salvo. Chamar uma vez, ao abrir o app. */
    fun iniciar(context: Context) {
        contexto = context.applicationContext
        carregar()
    }

    fun todos(): List<Documento> = documentosSalvos.toList()

    /** Arquivos abertos ou recebidos, do mais recente para o mais antigo. */
    fun recentes(): List<Documento> =
        documentosSalvos.filter { it.ultimoAcesso > 0 }.sortedByDescending { it.ultimoAcesso }

    fun favoritos(): List<Documento> = documentosSalvos.filter { it.favorito }

    fun arquivo(doc: Documento): File = File(pastaDeArquivos(), doc.arquivo)

    /**
     * Copia um arquivo recebido ou escolhido para a biblioteca. Demora, então chamar em segundo plano.
     * Depois, adicionar o retorno com adicionar(), na thread principal.
     */
    fun importar(uri: Uri): Documento? {
        val resolver = contexto.contentResolver
        var nome = "arquivo"
        var tamanho = -1L
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val colNome = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val colTamanho = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (colNome >= 0) nome = cursor.getString(colNome) ?: nome
                if (colTamanho >= 0 && !cursor.isNull(colTamanho)) tamanho = cursor.getLong(colTamanho)
            }
        }

        val id = UUID.randomUUID().toString()
        val destino = File(pastaDeArquivos(), id + extensao(nome))
        val copiou = try {
            resolver.openInputStream(uri)?.use { origem ->
                destino.outputStream().use { saida -> origem.copyTo(saida) }
            } != null
        } catch (e: IOException) {
            false
        }
        if (!copiou) {
            destino.delete()
            return null
        }

        val agora = System.currentTimeMillis()
        return Documento(
            id = id,
            nome = nome,
            arquivo = destino.name,
            tamanho = if (tamanho >= 0) tamanho else destino.length(),
            tipo = resolver.getType(uri) ?: "application/octet-stream",
            adicionadoEm = agora,
            ultimoAcesso = agora,
            favorito = false,
        )
    }

    /** Guarda um arquivo gerado por uma ferramenta (ele é movido para a biblioteca). */
    fun importarArquivo(origem: File, nome: String, tipo: String): Documento {
        val id = UUID.randomUUID().toString()
        val destino = File(pastaDeArquivos(), id + extensao(nome))
        if (!origem.renameTo(destino)) {
            origem.copyTo(destino, overwrite = true)
            origem.delete()
        }
        val agora = System.currentTimeMillis()
        return Documento(id, nome, destino.name, destino.length(), tipo, agora, agora, false)
    }

    fun adicionar(doc: Documento) {
        documentosSalvos.add(doc)
        salvar()
    }

    fun marcarAcesso(doc: Documento) {
        doc.ultimoAcesso = System.currentTimeMillis()
        salvar()
    }

    fun alternarFavorito(doc: Documento) {
        doc.favorito = !doc.favorito
        salvar()
    }

    fun renomear(doc: Documento, novoNome: String) {
        doc.nome = novoNome
        salvar()
    }

    /** Cria uma cópia do arquivo na biblioteca, com "cópia" no nome. */
    fun duplicar(doc: Documento): Documento? {
        val origem = arquivo(doc)
        if (!origem.exists()) return null
        val nome = "${doc.nome.substringBeforeLast('.', doc.nome)} cópia${extensao(doc.nome)}"
        val id = UUID.randomUUID().toString()
        val destino = File(pastaDeArquivos(), id + extensao(doc.nome))
        return try {
            origem.copyTo(destino)
            val agora = System.currentTimeMillis()
            Documento(id, nome, destino.name, destino.length(), doc.tipo, agora, agora, false).also { adicionar(it) }
        } catch (e: IOException) {
            destino.delete()
            null
        }
    }

    fun excluir(doc: Documento) {
        arquivo(doc).delete()
        documentosSalvos.remove(doc)
        salvar()
    }

    /** Apaga a lista de recentes. Os arquivos, favoritos e pastas continuam como estão. */
    fun limparRecentes() {
        documentosSalvos.forEach { it.ultimoAcesso = 0 }
        salvar()
    }

    private fun pastaDeArquivos(): File = File(contexto.filesDir, "biblioteca").apply { mkdirs() }

    private fun arquivoIndice(): File = File(contexto.filesDir, "biblioteca.json")

    private fun extensao(nome: String): String {
        val ext = nome.substringAfterLast('.', "")
        return if (ext.isNotEmpty() && ext.length <= 10) ".$ext" else ""
    }

    private fun salvar() {
        val json = JSONObject()
            .put("documentos", JSONArray(documentosSalvos.map { it.paraJson() }))
        arquivoIndice().writeText(json.toString())
    }

    private fun carregar() {
        val indice = arquivoIndice()
        if (!indice.exists()) return
        try {
            val docs = JSONObject(indice.readText()).getJSONArray("documentos")
            for (i in 0 until docs.length()) documentosSalvos.add(Documento.deJson(docs.getJSONObject(i)))
        } catch (e: JSONException) {
            // Índice corrompido: guarda uma cópia para não perder nada e começa um novo.
            indice.renameTo(File(contexto.filesDir, "biblioteca.corrompido.json"))
            documentosSalvos.clear()
        }
    }
}
