package com.example.myopengltest

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.sin

class MyRenderer : GLSurfaceView.Renderer {

    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        // 최초 한 번: 기본 배경색 설정 (RGBA)
        GLES20.glClearColor(0.15f, 0.15f, 0.2f, 1.0f)
        // 필요한 경우 깊이/블렌딩 초기 설정도 여기서
        // GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        // GLES20.glDisable(GLES20.GL_BLEND)
    }

    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
        // 뷰포트(그릴 영역) 지정
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(unused: GL10?) {
        // 매 프레임: 화면 클리어
//        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // (옵션) 배경색을 프레임마다 살짝 바꾸고 싶다면:
        // val t = (System.nanoTime() / 1_000_000_000.0).toFloat()
        // val r = 0.2f + 0.1f * (sin(t * 0.7) + 1f).toFloat() * 0.5f
        // val g = 0.3f + 0.1f * (sin(t * 1.1) + 1f).toFloat() * 0.5f
        // val b = 0.5f + 0.1f * (sin(t * 0.9) + 1f).toFloat() * 0.5f
        // GLES20.glClearColor(r, g, b, 1.0f)

        val t = (System.nanoTime() / 1_000_000_000.0).toFloat()
        val r = 0.25f + 0.25f * ((kotlin.math.sin(t * 0.7) + 1f) * 0.5f).toFloat()
        val g = 0.30f + 0.25f * ((kotlin.math.sin(t * 1.1) + 1f) * 0.5f).toFloat()
        val b = 0.35f + 0.25f * ((kotlin.math.sin(t * 0.9) + 1f) * 0.5f).toFloat()

        GLES20.glClearColor(r, g, b, 1.0f) // 다음 clear 에 적용될 색상 설정
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
    }
}