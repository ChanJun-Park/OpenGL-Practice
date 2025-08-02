package com.example.myopengltest.shadowview

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.RectF
import android.opengl.GLSurfaceView

class GradientShadowGLSurfaceView(context: Context) : GLSurfaceView(context) {
	private val renderer = GradientShadowRenderer()

	init {
		// OpenGL ES 2.0 컨텍스트 사용
		setEGLContextClientVersion(2)

		holder.setFormat(PixelFormat.TRANSLUCENT)

		setEGLConfigChooser(8, 8, 8, 8, 16, 0)

		// 렌더러 연결
		setRenderer(renderer)

		// 프레임 드로잉 방식: 연속 렌더링(애니메이션용)
		renderMode = RENDERMODE_CONTINUOUSLY
	}

	fun setButtonRect(
		rect: RectF,
		radiusPx: Float
	) {
		queueEvent {
			renderer.buttonRect.set(rect)
			renderer.radiusPx = radiusPx
			renderer.invalidate()
		}
		requestRender()
	}

	fun setShadowOffset(
		x: Float,
		y: Float,
	) {
		queueEvent {
			renderer.shadowOffset[0] = x
			renderer.shadowOffset[1] = y
			renderer.invalidate()
		}
	}

	fun setBlur(blurPx: Float) {
		queueEvent {
			renderer.blurPx = blurPx
			renderer.invalidate()
		}
	}

	fun setSoftness(s: Float) {
		queueEvent {
			renderer.softness = s
			renderer.invalidate()
		}
	}

	fun setGradientColors(
		color1: Int,
		color2: Int,
	) {
		queueEvent {
			renderer.color1 = intColorToLinear(color1)
			renderer.color2 = intColorToLinear(color2)
			renderer.invalidate()
		}
	}

	fun setShadowColor(
		color: Int,
	) {
		queueEvent {
			renderer.shadowColor = intColorToLinear(color)
			renderer.invalidate()
		}
	}

	/** 배경색 설정 (투명: Color.TRANSPARENT) */
	override fun setBackgroundColor(color: Int) {
		queueEvent {
			renderer.backgroundColor = intColorToLinear(color)
			renderer.invalidate()
		}
		requestRender()
	}

	/** sRGB int → 선형(0~1) RGBA */
	private fun intColorToLinear(c: Int): FloatArray {
		fun ch(x: Int) = (x / 255f)
		val a = ch(Color.alpha(c))
		val r = ch(Color.red(c))
		val g = ch(Color.green(c))
		val b = ch(Color.blue(c))
		// 프리멀티는 셰이더에서 처리하므로 여기선 비프리멀티
		return floatArrayOf(r, g, b, a)
	}
}