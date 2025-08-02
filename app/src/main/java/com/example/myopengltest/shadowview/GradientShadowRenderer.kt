package com.example.myopengltest.shadowview

import android.graphics.RectF
import android.opengl.GLES20
import android.opengl.GLSurfaceView.Renderer
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GradientShadowRenderer: Renderer {
	val buttonRect = RectF(100f, 300f, 100f + 220f, 300f + 64f)
	var radiusPx = 24f
	var blurPx = 80f
	var shadowOffset = floatArrayOf(0f, 12f)
	var softness = 36f
	var shadowColor = floatArrayOf(0.396f, 0.89f, 1.0f, 1f) // 기본 청록
	var color1 = floatArrayOf(0.396f, 0.89f, 1.0f, 1f) // 기본 청록
	var color2 = floatArrayOf(1.0f, 0.49f, 0.85f, 1f) // 기본 핑크
//	var color1 = floatArrayOf(0.949f, 0.765f, 0.804f, 1.0f)
//	var color2 = floatArrayOf(0.773f, 0.890f, 0.945f, 1.0f)
	var backgroundColor = floatArrayOf(1f, 1f, 1f, 1f) // 기본 배경색

	// 화면 전체를 덮는 사각형 Vertex
	private val quadVertices = floatArrayOf(
		-1f,  1f,   // 좌상단
		1f,  1f,    // 우상단
		-1f, -1f,   // 좌하단
		1f, -1f,   // 우하단
	)

	private lateinit var vertexBuffer: FloatBuffer
	private val BYTES_PER_FLOAT = 4
	private val POSITION_COMPONENT_COUNT = 2 // (x, y)
	private val STRIDE = POSITION_COMPONENT_COUNT * BYTES_PER_FLOAT

	// === GL 핸들 ===
	private var program = 0
	private var aPositionLocation = 0
	private var uTimeLocation = 0
	private var uResolution = 0
	private var uRadius = 0 // View 모서리 radius
	private var uBlur = 0
	private var uSoftness = -1
	private var uShadowColor = -1
	private var uColor1 = -1
	private var uColor2 = -1
	private var uCenter = -1
	private var uSize = -1
	private var uOffset = -1

	// 시간 기반 애니메이션용
	private var lastMs: Long = 0L
	private var accumulatedTime: Float = 0f

	private var dirty = true
	private var cachedTargetViewX = 0f
	private var cachedTargetViewY = 0f
	private var cachedTargetViewWidth = 0f
	private var cachedTargetViewHeight = 0f

	// region === 셰이더 소스 ===
	// Vertex Shader: vec2 정점 좌표를 받아 동차좌표(vec4)로 확장
	private val vertexShaderCode = """
		attribute vec4 a_Position;
		varying vec2 v_UV;
		
		void main() {
			v_UV = a_Position.xy * 0.5 + 0.5; // [0, 1] 정규화
			gl_Position = a_Position;
		}
    """.trimIndent()

	// Fragment Shader: 단색 렌더링 (u_Color로 주입)
	private val fragmentShaderCode = """
		precision mediump float;
		
		uniform vec2 u_Resolution; // View 가로 세로
		uniform float u_Radius; // View 모서리 둥근 정도
		uniform float u_Blur;
		uniform float u_Softness;
		uniform vec4 u_ShadowColor;
		uniform vec4 u_Color1;
		uniform vec4 u_Color2;
		uniform vec2 u_Center;
		uniform vec2 u_Size;
		uniform vec2 u_Offset;
		uniform float u_Time;   // 초 단위 시간
		
		varying vec2 v_UV;
		
		// 편의를 위한 상수
		const float PI     = 3.14159265359;
		const float TWO_PI = 6.28318530718;
		
		float sdRoundRect(vec2 p, vec2 size, float radius) {
		    vec2 q = abs(p) - size + vec2(radius);
		    return length(max(q, 0.0)) - radius;
		}
		
		void main() {
			// vec2 fragCoord = v_UV * u_Resolution;
			vec2 fragCoord = gl_FragCoord.xy;
			vec2 p = fragCoord - u_Center;
			vec2 halfSize = 0.5 * u_Size;
			float dist = sdRoundRect(p, halfSize, halfSize.x);
			
			float angle = 0.8 * u_Time;
			vec2 dir = vec2(cos(angle), sin(angle));
			vec2 pUnit = p == vec2(0.0) ? vec2(1.0, 0.0) : normalize(p);
			float g = clamp(0.5 + dot(pUnit, dir)*0.5, 0.0, 1.0);
			vec4 grad = mix(u_Color1, u_Color2, g);

			float alpha = 1.0 - smoothstep(0.0, u_Blur, dist);

			// vec3 rgb = grad.rgb * grad.a;
			vec3 rgb = grad.rgb;
			// gl_FragColor = vec4(rgb, alpha * grad.a);
			gl_FragColor = vec4(rgb, alpha);
		}
    """.trimIndent()
	// endregion

	fun invalidate() {
		dirty = true
	}

	override fun onSurfaceCreated(p0: GL10?, p1: EGLConfig?) {
		GLES20.glEnable(GLES20.GL_BLEND)
//		(srcColor × srcFactor) + (dstColor × dstFactor) src 쉐이더에서 칠하는 색, dst 는 배경색
		GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
//		GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
		lastMs = System.currentTimeMillis()

		// 2) 정점 버퍼 준비
		vertexBuffer = ByteBuffer
			.allocateDirect(quadVertices.size * BYTES_PER_FLOAT)
			.order(ByteOrder.nativeOrder())
			.asFloatBuffer()
			.apply {
				put(quadVertices)
				position(0)
			}

		// 3) 셰이더 컴파일 & 프로그램 링크
		val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
		val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
		program = linkProgram(vertexShader, fragmentShader)

		// 4) attribute/uniform 위치 조회
		aPositionLocation = GLES20.glGetAttribLocation(program, "a_Position")
		uTimeLocation = GLES20.glGetUniformLocation(program, "u_Time")
		uResolution = GLES20.glGetUniformLocation(program, "u_Resolution")
		uRadius = GLES20.glGetUniformLocation(program, "u_Radius")
		uBlur = GLES20.glGetUniformLocation(program, "u_Blur")
		uSoftness = GLES20.glGetUniformLocation(program, "u_Softness")
		uShadowColor = GLES20.glGetUniformLocation(program, "u_ShadowColor")
		uColor1 = GLES20.glGetUniformLocation(program, "u_Color1")
		uColor2 = GLES20.glGetUniformLocation(program, "u_Color2")
		uCenter = GLES20.glGetUniformLocation(program, "u_Center")
		uSize = GLES20.glGetUniformLocation(program, "u_Size")
		uOffset = GLES20.glGetUniformLocation(program, "u_Offset")

		// (선택) 유효성 검사
		validateProgram(program)
	}

	override fun onSurfaceChanged(p0: GL10?, width: Int, height: Int) {
		GLES20.glViewport(0, 0, width, height)
		GLES20.glUseProgram(program)
		GLES20.glUniform2f(uResolution, width.toFloat(), height.toFloat())
	}

	override fun onDrawFrame(unused: GL10?) {
		// 1) 배경 지우기
		if (dirty) {
			GLES20.glClearColor(backgroundColor[0], backgroundColor[1], backgroundColor[2], backgroundColor[3])
		}

		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

		// 2) 프로그램 사용
		GLES20.glUseProgram(program)

		val currentTimeMillis = System.currentTimeMillis()
		val t = (currentTimeMillis - lastMs) / 1000f
		lastMs = currentTimeMillis
		accumulatedTime += t
		if (accumulatedTime > 10f) {
			accumulatedTime %= (2f * Math.PI.toFloat() / 0.8f)
		}
		GLES20.glUniform1f(uTimeLocation, accumulatedTime)

		if (dirty) {
			cachedTargetViewX = (buttonRect.left + buttonRect.right) * 0.5f
			cachedTargetViewY = (buttonRect.top + buttonRect.bottom) * 0.5f
			cachedTargetViewWidth = (buttonRect.right - buttonRect.left)
			cachedTargetViewHeight = (buttonRect.bottom - buttonRect.top)
			dirty = false

			GLES20.glUniform2f(uCenter, cachedTargetViewX, cachedTargetViewY)
			GLES20.glUniform2f(uSize, cachedTargetViewWidth, cachedTargetViewHeight)
			GLES20.glUniform1f(uRadius, radiusPx)
			GLES20.glUniform1f(uBlur, blurPx)
			GLES20.glUniform2f(uOffset, shadowOffset[0], shadowOffset[1])
			GLES20.glUniform1f(uSoftness, softness)
			GLES20.glUniform4f(uShadowColor, shadowColor[0], shadowColor[1], shadowColor[2], shadowColor[3])
			GLES20.glUniform4f(uColor1, color1[0], color1[1], color1[2], color1[3])
			GLES20.glUniform4f(uColor2, color2[0], color2[1], color2[2], color2[3])
		}

		// 3) 정점 속성 포인터 설정
		//    CPU의 vertexBuffer → a_Position으로 연결
		vertexBuffer.position(0)

		// a_Position 속성에 vertexBuffer의 데이터를 연결
		GLES20.glVertexAttribPointer(
			aPositionLocation,
			POSITION_COMPONENT_COUNT,
			GLES20.GL_FLOAT,
			false,
			STRIDE,
			vertexBuffer
		)
		// 정점 배열을 전달할 것이다 라고 선언
		GLES20.glEnableVertexAttribArray(aPositionLocation)

		// 5) 그리기
		GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

		// 6) 정점 속성 비활성화 (정리)
		GLES20.glDisableVertexAttribArray(aPositionLocation)
	}

	// region === 셰이더/프로그램 유틸 ===
	private fun compileShader(type: Int, source: String): Int {
		val shader = GLES20.glCreateShader(type)
		if (shader == 0) {
			throw RuntimeException("glCreateShader failed: type=$type")
		}
		GLES20.glShaderSource(shader, source)
		GLES20.glCompileShader(shader)

		val compiled = IntArray(1)
		GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
		if (compiled[0] == 0) {
			val log = GLES20.glGetShaderInfoLog(shader)
			Log.e(TAG, "Shader compile error:\n$log\nsource:\n$source")
			GLES20.glDeleteShader(shader)
			throw RuntimeException("Shader compile failed (type=$type)")
		}
		return shader
	}

	private fun linkProgram(vertexShader: Int, fragmentShader: Int): Int {
		val program = GLES20.glCreateProgram()
		if (program == 0) {
			throw RuntimeException("glCreateProgram failed")
		}
		GLES20.glAttachShader(program, vertexShader)
		GLES20.glAttachShader(program, fragmentShader)
		GLES20.glLinkProgram(program)

		val linked = IntArray(1)
		GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0)
		if (linked[0] == 0) {
			val log = GLES20.glGetProgramInfoLog(program)
			Log.e(TAG, "Program link error:\n$log")
			GLES20.glDeleteProgram(program)
			throw RuntimeException("Program link failed")
		}
		return program
	}

	private fun validateProgram(program: Int) {
		GLES20.glValidateProgram(program)
		val status = IntArray(1)
		GLES20.glGetProgramiv(program, GLES20.GL_VALIDATE_STATUS, status, 0)
		if (status[0] == 0) {
			val log = GLES20.glGetProgramInfoLog(program)
			Log.w(TAG, "Program validate log:\n$log")
			// validate 실패가 항상致命은 아니지만, 상태 점검에 유용
		}
	}
	// endregion

	companion object {
		private const val TAG = "GradientShadowRenderer"
	}
}