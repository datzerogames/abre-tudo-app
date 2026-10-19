package br.com.datzerogames.abretudo

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class AbreTudoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Biblioteca.iniciar(this)
        Tema.aplicarModo(this)
        // Carrega as fontes e os recursos que o PDFBox usa nas ferramentas de PDF.
        PDFBoxResourceLoader.init(applicationContext)
    }
}
