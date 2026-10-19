package br.com.datzerogames.abretudo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.util.Matrix
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.math.min

/** Como a ferramenta recebe os arquivos. */
enum class Entrada { NENHUMA, PDF_UM, PDF_VARIOS, IMAGENS }

/** Todas as ferramentas da aba Ferramentas. Só as `implementada` funcionam nesta versão. */
enum class Ferramenta(
    val titulo: String,
    val grupo: String,
    val entrada: Entrada,
    /** Pergunta feita antes de escolher os arquivos. Null quando a ferramenta não precisa de texto. */
    val pergunta: String?,
    val implementada: Boolean,
) {
    JUNTAR("Juntar PDF", "PDF", Entrada.PDF_VARIOS, null, true),
    DIVIDIR("Dividir PDF", "PDF", Entrada.PDF_UM, "Páginas a manter (ex: 1-3 ou 2,5)", true),
    ORGANIZAR("Organizar páginas", "PDF", Entrada.PDF_UM, "Nova ordem das páginas (ex: 3,1,2)", true),
    NUMERAR("Numerar páginas", "PDF", Entrada.PDF_UM, null, true),
    MARCA("Marca d'água", "PDF", Entrada.PDF_UM, "Texto da marca d'água", true),
    SENHA("Senha", "PDF", Entrada.PDF_UM, "Senha para abrir o PDF", true),
    COMPRIMIR("Comprimir para envio", "PDF", Entrada.PDF_UM, null, true),
    FOTOS_PDF("Fotos em PDF", "PDF", Entrada.IMAGENS, null, true),
    PDF_FOTOS("PDF em fotos", "PDF", Entrada.PDF_UM, null, true),
    ESCANEAR("Escanear", "Escanear e converter", Entrada.NENHUMA, null, false),
    OCR("Copiar texto (OCR)", "Escanear e converter", Entrada.PDF_UM, null, false),
    BOLETO("Ler boleto", "Para o dia a dia", Entrada.PDF_UM, null, false),
    ASSINAR("Assinar", "Para o dia a dia", Entrada.PDF_UM, null, false),
    FORMULARIO("Preencher formulário", "Para o dia a dia", Entrada.PDF_UM, null, false),
    RG_CPF("Juntar RG e CPF", "Para o dia a dia", Entrada.IMAGENS, null, false),
    LER_VOZ("Ler em voz alta", "Leitura e idiomas", Entrada.PDF_UM, null, false),
    TRADUZIR("Traduzir", "Leitura e idiomas", Entrada.PDF_UM, null, false),
}

/** Um arquivo produzido por uma ferramenta, ainda fora da biblioteca. */
class Saida(val arquivo: File, val nome: String, val tipo: String)

object PdfFerramentas {

    /** Executa a ferramenta. Chamar em segundo plano: pode demorar em arquivos grandes. */
    fun executar(contexto: Context, ferramenta: Ferramenta, uris: List<Uri>, parametro: String?): List<Saida> {
        // Cópias dos arquivos de entrada, apagadas no fim. As saídas não entram aqui:
        // quem chama move cada uma para a biblioteca.
        val entradas = mutableListOf<File>()
        fun copiar(uri: Uri): File = copiarParaCache(contexto, uri).also { entradas.add(it) }
        fun saida(nome: String): File = File(contexto.cacheDir, "${UUID.randomUUID()}-$nome")

        try {
            return when (ferramenta) {
                Ferramenta.JUNTAR -> {
                    val arquivo = saida("Juntado.pdf")
                    val juntador = PDFMergerUtility()
                    juntador.destinationFileName = arquivo.path
                    uris.forEach { juntador.addSource(copiar(it)) }
                    juntador.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
                    listOf(Saida(arquivo, "Juntado.pdf", "application/pdf"))
                }
                Ferramenta.DIVIDIR -> {
                    val origem = copiar(uris.first())
                    val paginas = parsePaginas(parametro.orEmpty(), contarPaginas(origem))
                    val arquivo = saida("parte.pdf")
                    extrairPaginas(origem, paginas, arquivo)
                    listOf(Saida(arquivo, "${nomeBase(contexto, uris.first())} - parte.pdf", "application/pdf"))
                }
                Ferramenta.ORGANIZAR -> {
                    val origem = copiar(uris.first())
                    val paginas = parsePaginas(parametro.orEmpty(), contarPaginas(origem), ordem = true)
                    val arquivo = saida("organizado.pdf")
                    extrairPaginas(origem, paginas, arquivo)
                    listOf(Saida(arquivo, "${nomeBase(contexto, uris.first())} - organizado.pdf", "application/pdf"))
                }
                Ferramenta.NUMERAR -> {
                    val origem = copiar(uris.first())
                    val arquivo = saida("numerado.pdf")
                    numerarPaginas(origem, arquivo)
                    listOf(Saida(arquivo, "${nomeBase(contexto, uris.first())} - numerado.pdf", "application/pdf"))
                }
                Ferramenta.MARCA -> {
                    val origem = copiar(uris.first())
                    val arquivo = saida("marca.pdf")
                    marcaDagua(origem, arquivo, parametro.orEmpty().ifBlank { "CONFIDENCIAL" })
                    listOf(Saida(arquivo, "${nomeBase(contexto, uris.first())} - marca.pdf", "application/pdf"))
                }
                Ferramenta.SENHA -> {
                    val origem = copiar(uris.first())
                    val arquivo = saida("protegido.pdf")
                    protegerComSenha(origem, arquivo, parametro.orEmpty())
                    listOf(Saida(arquivo, "${nomeBase(contexto, uris.first())} - protegido.pdf", "application/pdf"))
                }
                Ferramenta.COMPRIMIR -> {
                    val origem = copiar(uris.first())
                    val arquivo = saida("comprimido.pdf")
                    comprimir(origem, arquivo)
                    listOf(Saida(arquivo, "${nomeBase(contexto, uris.first())} - comprimido.pdf", "application/pdf"))
                }
                Ferramenta.FOTOS_PDF -> {
                    val arquivo = saida("Imagens.pdf")
                    fotosEmPdf(contexto, uris, arquivo)
                    listOf(Saida(arquivo, "Imagens.pdf", "application/pdf"))
                }
                Ferramenta.PDF_FOTOS -> {
                    val origem = copiar(uris.first())
                    val base = nomeBase(contexto, uris.first())
                    pdfEmFotos(origem) { pagina, arquivo ->
                        Saida(arquivo, "$base - pág ${pagina + 1}.jpg", "image/jpeg")
                    }
                }
                else -> throw IllegalStateException("Ferramenta ainda não disponível")
            }
        } finally {
            entradas.forEach { it.delete() }
        }
    }

    private fun copiarParaCache(contexto: Context, uri: Uri): File {
        val destino = File(contexto.cacheDir, "entrada-${UUID.randomUUID()}.pdf")
        contexto.contentResolver.openInputStream(uri)?.use { entrada ->
            destino.outputStream().use { saida -> entrada.copyTo(saida) }
        } ?: throw IOException("Não foi possível abrir o arquivo")
        return destino
    }

    private fun nomeBase(contexto: Context, uri: Uri): String {
        val nome = contexto.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val coluna = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && coluna >= 0) cursor.getString(coluna) else null
        } ?: "Documento"
        return nome.substringBeforeLast('.', nome)
    }

    private fun contarPaginas(arquivo: File): Int = PDDocument.load(arquivo).use { it.numberOfPages }

    /**
     * Lê uma lista de páginas como "1-3,5" (páginas a manter) ou "3,1,2" (nova ordem, se `ordem` for true).
     * Devolve os números de página começando em zero.
     */
    private fun parsePaginas(texto: String, total: Int, ordem: Boolean = false): List<Int> {
        val resultado = mutableListOf<Int>()
        texto.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { parte ->
            if (!ordem && parte.contains("-")) {
                val (inicio, fim) = parte.split("-", limit = 2).map { it.trim().toIntOrNull() }
                if (inicio == null || fim == null || inicio < 1 || fim > total || inicio > fim) {
                    throw IllegalArgumentException("Intervalo inválido: $parte (o PDF tem $total páginas)")
                }
                resultado.addAll((inicio..fim).map { it - 1 })
            } else {
                val numero = parte.toIntOrNull()
                if (numero == null || numero < 1 || numero > total) {
                    throw IllegalArgumentException("Página inválida: $parte (o PDF tem $total páginas)")
                }
                resultado.add(numero - 1)
            }
        }
        if (resultado.isEmpty()) throw IllegalArgumentException("Informe pelo menos uma página")
        return resultado
    }

    /** Monta um PDF novo só com as páginas pedidas, na ordem pedida. */
    private fun extrairPaginas(origem: File, paginas: List<Int>, destino: File) {
        PDDocument.load(origem).use { fonte ->
            PDDocument().use { novo ->
                paginas.forEach { novo.importPage(fonte.getPage(it)) }
                novo.save(destino)
            }
        }
    }

    private fun numerarPaginas(origem: File, destino: File) {
        PDDocument.load(origem).use { doc ->
            for (i in 0 until doc.numberOfPages) {
                val pagina = doc.getPage(i)
                val caixa = pagina.mediaBox
                PDPageContentStream(doc, pagina, PDPageContentStream.AppendMode.APPEND, true, true).use { conteudo ->
                    conteudo.beginText()
                    conteudo.setFont(PDType1Font.HELVETICA, 10f)
                    conteudo.newLineAtOffset(caixa.width / 2 - 6f, 20f)
                    conteudo.showText("${i + 1}")
                    conteudo.endText()
                }
            }
            doc.save(destino)
        }
    }

    private fun marcaDagua(origem: File, destino: File, texto: String) {
        PDDocument.load(origem).use { doc ->
            val fonte = PDType1Font.HELVETICA_BOLD
            val tamanho = 48f
            for (i in 0 until doc.numberOfPages) {
                val pagina = doc.getPage(i)
                val caixa = pagina.mediaBox
                val largura = fonte.getStringWidth(texto) / 1000f * tamanho
                PDPageContentStream(doc, pagina, PDPageContentStream.AppendMode.APPEND, true, true).use { conteudo ->
                    conteudo.setNonStrokingColor(0.8f)
                    conteudo.beginText()
                    conteudo.setFont(fonte, tamanho)
                    conteudo.setTextMatrix(
                        Matrix.getRotateInstance(
                            Math.toRadians(45.0),
                            caixa.width / 2 - largura / 2,
                            caixa.height / 2,
                        )
                    )
                    conteudo.showText(texto)
                    conteudo.endText()
                }
            }
            doc.save(destino)
        }
    }

    private fun protegerComSenha(origem: File, destino: File, senha: String) {
        if (senha.isEmpty()) throw IllegalArgumentException("Informe uma senha")
        PDDocument.load(origem).use { doc ->
            doc.protect(StandardProtectionPolicy(senha, senha, AccessPermission()))
            doc.save(destino)
        }
    }

    /**
     * Reduz o tamanho renderizando cada página como imagem JPEG. Por isso o texto
     * deixa de ser selecionável no arquivo final.
     */
    private fun comprimir(origem: File, destino: File) {
        val escala = 1.5f
        val documento = PdfDocument()
        ParcelFileDescriptor.open(origem, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { pagina ->
                        val largura = pagina.width
                        val altura = pagina.height
                        val bitmap = Bitmap.createBitmap(
                            (largura * escala).toInt(),
                            (altura * escala).toInt(),
                            Bitmap.Config.ARGB_8888,
                        )
                        bitmap.eraseColor(Color.WHITE)
                        pagina.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val jpeg = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 60, it) }
                        bitmap.recycle()
                        val comprimido = BitmapFactory.decodeByteArray(jpeg.toByteArray(), 0, jpeg.size())

                        val info = PdfDocument.PageInfo.Builder(largura, altura, i + 1).create()
                        val paginaPdf = documento.startPage(info)
                        paginaPdf.canvas.drawBitmap(comprimido, null, RectF(0f, 0f, largura.toFloat(), altura.toFloat()), null)
                        documento.finishPage(paginaPdf)
                        comprimido.recycle()
                    }
                }
            }
        }
        FileOutputStream(destino).use { documento.writeTo(it) }
        documento.close()
    }

    private fun fotosEmPdf(contexto: Context, uris: List<Uri>, destino: File) {
        val largura = 595
        val altura = 842
        val margem = 24f
        val documento = PdfDocument()
        uris.forEachIndexed { i, uri ->
            val bitmap = decodificarImagem(contexto, uri, 2000)
                ?: throw IOException("Não foi possível abrir uma das imagens")
            val info = PdfDocument.PageInfo.Builder(largura, altura, i + 1).create()
            val pagina = documento.startPage(info)
            val escala = min((largura - 2 * margem) / bitmap.width, (altura - 2 * margem) / bitmap.height)
            val w = bitmap.width * escala
            val h = bitmap.height * escala
            val x = (largura - w) / 2
            val y = (altura - h) / 2
            pagina.canvas.drawBitmap(bitmap, null, RectF(x, y, x + w, y + h), null)
            documento.finishPage(pagina)
            bitmap.recycle()
        }
        FileOutputStream(destino).use { documento.writeTo(it) }
        documento.close()
    }

    /** Lê uma imagem reduzindo o tamanho, para não estourar a memória com fotos grandes. */
    private fun decodificarImagem(contexto: Context, uri: Uri, maxLado: Int): Bitmap? {
        val opcoes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contexto.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opcoes) }
        var amostra = 1
        while (opcoes.outWidth / amostra > maxLado || opcoes.outHeight / amostra > maxLado) amostra *= 2
        val final = BitmapFactory.Options().apply { inSampleSize = amostra }
        return contexto.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, final) }
    }

    /** Cada página vira uma imagem JPEG separada. */
    private fun pdfEmFotos(origem: File, criarSaida: (Int, File) -> Saida): List<Saida> {
        val escala = 2f
        val saidas = mutableListOf<Saida>()
        ParcelFileDescriptor.open(origem, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { pagina ->
                        val bitmap = Bitmap.createBitmap(
                            (pagina.width * escala).toInt(),
                            (pagina.height * escala).toInt(),
                            Bitmap.Config.ARGB_8888,
                        )
                        bitmap.eraseColor(Color.WHITE)
                        pagina.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val arquivo = File(origem.parentFile, "pagina-${UUID.randomUUID()}.jpg")
                        FileOutputStream(arquivo).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                        bitmap.recycle()
                        saidas.add(criarSaida(i, arquivo))
                    }
                }
            }
        }
        return saidas
    }
}
