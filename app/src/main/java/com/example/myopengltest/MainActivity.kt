package com.example.myopengltest

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

	private lateinit var glView: MyGLSurfaceView

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		glView = MyGLSurfaceView(this)
		setContentView(glView)
		ViewCompat.setOnApplyWindowInsetsListener(glView) { v, insets ->
			val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
			v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
			insets
		}
	}

	override fun onResume() {
		super.onResume()
		glView.onResume()
	}

	override fun onPause() {
		glView.onPause()
		super.onPause()
	}
}