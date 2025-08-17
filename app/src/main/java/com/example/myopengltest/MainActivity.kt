package com.example.myopengltest

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.os.Bundle
import android.widget.ViewAnimator
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myopengltest.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

	private lateinit var glView: MyGLSurfaceView
	private lateinit var binding: ActivityMainBinding

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		binding = ActivityMainBinding.inflate(layoutInflater)
//		glView = MyGLSurfaceView(this)
//		setContentView(glView)
		setContentView(binding.root)
		ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
			val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
			v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
			insets
		}

		binding.shape.setOnClickListener { v ->
			val originalWidth = v.width
			val originalHeight = v.height
			val targetWidth = originalWidth * 2
			val targetHeight = originalHeight * 2

			val widthAnimator = ValueAnimator.ofInt(originalWidth, targetWidth)
			val heightAnimator = ValueAnimator.ofInt(originalHeight, targetHeight)

			widthAnimator.addUpdateListener { animation ->
				val value = animation.animatedValue as Int
				val layoutParams = v.layoutParams
				layoutParams.width = value
				v.layoutParams = layoutParams
			}

			heightAnimator.addUpdateListener { animation ->
				val value = animation.animatedValue as Int
				val layoutParams = v.layoutParams
				layoutParams.height = value
				v.layoutParams = layoutParams
			}

			val animatorSet = AnimatorSet()
			animatorSet.playTogether(widthAnimator, heightAnimator)
			animatorSet.duration = 200
			animatorSet.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					// 원래 크기로 다시 애니메이션
					val widthReverse = ValueAnimator.ofInt(targetWidth, originalWidth)
					val heightReverse = ValueAnimator.ofInt(targetHeight, originalHeight)

					widthReverse.addUpdateListener { anim ->
						val value = anim.animatedValue as Int
						val layoutParams = v.layoutParams
						layoutParams.width = value
						v.layoutParams = layoutParams
					}
					heightReverse.addUpdateListener { anim ->
						val value = anim.animatedValue as Int
						val layoutParams = v.layoutParams
						layoutParams.height = value
						v.layoutParams = layoutParams
					}
					val reverseSet = AnimatorSet()
					reverseSet.playTogether(widthReverse, heightReverse)
					reverseSet.duration = 200
					reverseSet.start()
				}
			})
			animatorSet.start()
			true
		}
	}

	override fun onResume() {
		super.onResume()
	}

	override fun onPause() {
		super.onPause()
	}
}