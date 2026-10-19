package br.abretudo

import android.app.Application

class AbreTudoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Biblioteca.iniciar(this)
        Tema.aplicarModo(this)
    }
}
