package br.com.datzerogames.abretudo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/** Aplica a cor escolhida pelo usuário e recria a tela quando ela muda em outra tela. */
abstract class BaseActivity : AppCompatActivity() {
    private var corAplicada = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        corAplicada = Tema.aplicar(this)
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        if (corAplicada != Tema.corAtual(this)) recreate()
    }
}
