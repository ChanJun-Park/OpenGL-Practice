package com.example.myopengltest

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView

class MyGLSurfaceView(context: Context) : GLSurfaceView(context) {

    private val renderer = MashGradientEffectRenderer()

    init {
        // OpenGL ES 2.0 컨텍스트 사용
        setEGLContextClientVersion(2)

        holder.setFormat(PixelFormat.TRANSLUCENT)

        setEGLConfigChooser(8, 8, 8, 8, 16, 0)

        setZOrderOnTop(true)

        // 렌더러 연결
        setRenderer(renderer)

        // 프레임 드로잉 방식: 연속 렌더링(애니메이션용)
        renderMode = RENDERMODE_CONTINUOUSLY
        // 정적 화면이면 RENDERMODE_WHEN_DIRTY 로 바꾸고 requestRender()로 수동 호출
    }
}