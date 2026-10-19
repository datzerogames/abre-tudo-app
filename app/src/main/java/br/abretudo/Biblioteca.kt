package br.abretudo

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
    val nome: String,
    val arquivo: String,
    val tamanho: Long,
    val tipo: String,
    val adicionadoEm: Long,
    var ultimoAcesso: Long,
    var favorito: Boolean,
    var pastaId: String?,
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
        .put("pastaId", pastaId ?: JSONObject.NULL)

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
            pastaId = if (o.isNull("pastaId")) null else o.getString("pastaId"),
        )
    }
}

data class Pasta(val id: String, val nome: String) {
    fun paraJson(): JSONObject = JSONObject().put("id", id).put("nome", nome)

    companion object {
        fun deJson(o: JSONObject) = Pasta(o.getString("id"), o.getString("nome"))
    }
}

/**
 * Guarda os arquivos do usuário dentro do próprio app (filesDir/biblioteca) e o índice
 * em biblioteca.json. Nada é enviado para fora do aparelho.
 */
object Biblioteca {
    private lateinit var contexto: Context
    private val documentosSalvos = mutableListOf<Documento>()
    private val pastasSalvas = mutableListOf<Pasta>()

    /** Carrega o índice salvo. Chamar uma vez, ao abrir o app. */
    fun iniciar(context: Context) {
        contexto = context.applicationContext
        carregar()
    }

    fun todos(): List<Documento> = documentosSalvos.sortedByDescending { it.ultimoAcesso }

    fun favoritos(): List<Documento> = todos().filter { it.favorito }

    fun daPasta(pastaId: String): List<Documento> = todos().filter { it.pastaId == pastaId }

    fun pastas(): List<Pasta> = pastasSalvas.sortedBy { it.nome.lowercase() }

    fun contarNaPasta(pastaId: String): Int = documentosSalvos.count { it.pastaId == pastaId }

    fun pastaPorId(pastaId: String?): Pasta? = pastasSalvas.firstOrNull { it.id == pastaId }

    fun arquivo(doc: Documento): File = File(pastaDeArquivos(), doc.arquivo)

    /**
     * Copia o arquivo recebido para a biblioteca. Demora, então chamar em segundo plano.
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
            pastaId = null,
        )
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

    fun mover(doc: Documento, pastaId: String?) {
        doc.pastaId = pastaId
        salvar()
    }

    fun excluir(doc: Documento) {
        arquivo(doc).delete()
        documentosSalvos.remove(doc)
        salvar()
    }

    fun criarPasta(nome: String): Pasta {
        val pasta = Pasta(UUID.randomUUID().toString(), nome)
        pastasSalvas.add(pasta)
        salvar()
        return pasta
    }

    /** Excluir a pasta não apaga os arquivos dela: eles voltam para "Sem pasta". */
    fun excluirPasta(pasta: Pasta) {
        documentosSalvos.filter { it.pastaId == pasta.id }.forEach { it.pastaId = null }
        pastasSalvas.remove(pasta)
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
            .put("pastas", JSONArray(pastasSalvas.map { it.paraJson() }))
        arquivoIndice().writeText(json.toString())
    }

    private fun carregar() {
        val indice = arquivoIndice()
        if (!indice.exists()) return
        try {
            val json = JSONObject(indice.readText())
            val docs = json.getJSONArray("documentos")
            for (i in 0 until docs.length()) documentosSalvos.add(Documento.deJson(docs.getJSONObject(i)))
            val pastas = json.getJSONArray("pastas")
            for (i in 0 until pastas.length()) pastasSalvas.add(Pasta.deJson(pastas.getJSONObject(i)))
        } catch (e: JSONException) {
            // Índice corrompido: guarda uma cópia para não perder nada e começa um novo.
            indice.renameTo(File(contexto.filesDir, "biblioteca.corrompido.json"))
            documentosSalvos.clear()
            pastasSalvas.clear()
        }
    }
}
