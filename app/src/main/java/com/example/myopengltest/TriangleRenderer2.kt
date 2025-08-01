package com.example.myopengltest

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.sin

/**
 *
 *
 */
class TriangleRenderer2 : GLSurfaceView.Renderer {

	// region === 셰이더 소스 ===
	// Vertex Shader: vec2 정점 좌표를 받아 동차좌표(vec4)로 확장
	private val vertexShaderCode = """
        attribute vec2 a_Position;	// 정점 좌표
		attribute vec3 a_Color;		// 정점 색상
		
		varying vec3 v_Color;          // Fragment로 전달할 변수

        void main() {
            // NDC(-1~1) 좌표계로 이미 들어온다고 가정
			v_Color = a_Color;         // 색상 전달
            gl_Position = vec4(a_Position, 0.0, 2.0);
        }
    """.trimIndent()

	// Fragment Shader: 단색 렌더링 (u_Color로 주입)
	private val fragmentShaderCode = """
        precision mediump float;
        
		varying vec3 v_Color;          // Vertex에서 넘어온 색상
		
        void main() {
            gl_FragColor = vec4(v_Color, 1.0); // 색상과 알파값 1.0
        }
    """.trimIndent()
	// endregion

	// region === 정점 데이터 ===
	// 삼각형(정규화 장치 좌표: NDC)
	//   (0, 0.5)    (top)
	// (-0.5, -0.5)  (left bottom)
	// (0.5, -0.5)   (right bottom)
	private val triangleVertices = floatArrayOf(
		0.0f,  0.5f,  1.0f, 0.0f, 0.0f, // top 빨강
		-0.5f, -0.5f, 0.0f, 1.0f, 0.0f,  // bottom-left 초록
		0.5f, -0.5f, 0.0f, 0.0f, 1.0f   // bottom-right 파랑
	)

	private lateinit var vertexBuffer: FloatBuffer
	private val BYTES_PER_FLOAT = 4
	private val POSITION_COMPONENT_COUNT = 2 // (x, y)
	private val COLOR_COMPONENT_COUNT = 3 // (r, g, b)
	private val STRIDE = (POSITION_COMPONENT_COUNT + COLOR_COMPONENT_COUNT) * BYTES_PER_FLOAT
	// endregion

	// region === GL 핸들 ===
	private var program = 0
	private var aPositionLocation = 0
	private var aColorLocation = 0
	// endregion

	// 시간 기반 애니메이션용
	private var startNs: Long = 0L

	override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
		startNs = System.nanoTime()

		// 1) 클리어 컬러(배경색) 초기 설정
		GLES20.glClearColor(0.1f, 0.12f, 0.15f, 1.0f)

		// 2) 정점 버퍼 준비
		vertexBuffer = ByteBuffer
			.allocateDirect(triangleVertices.size * BYTES_PER_FLOAT)
			.order(ByteOrder.nativeOrder())
			.asFloatBuffer()
			.put(triangleVertices)
		vertexBuffer.position(0)

		// 3) 셰이더 컴파일 & 프로그램 링크
		val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
		val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
		program = linkProgram(vertexShader, fragmentShader)

		// 4) attribute/uniform 위치 조회
		aPositionLocation = GLES20.glGetAttribLocation(program, "a_Position")
		aColorLocation = GLES20.glGetAttribLocation(program, "a_Color")

		// (선택) 유효성 검사
		validateProgram(program)
	}

	override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
		// 그릴 영역 지정 (픽셀 단위)
		GLES20.glViewport(0, 0, width, height)
	}

	override fun onDrawFrame(unused: GL10?) {
		// 1) 배경 지우기
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

		// 2) 프로그램 사용
		GLES20.glUseProgram(program)

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

		vertexBuffer.position(POSITION_COMPONENT_COUNT)
		GLES20.glVertexAttribPointer(
			aColorLocation,
			COLOR_COMPONENT_COUNT,
			GLES20.GL_FLOAT,
			false,
			STRIDE,
			vertexBuffer
		)
		GLES20.glEnableVertexAttribArray(aColorLocation)

		// 4) 색상(uniform) 설정 - 시간 기반으로 살짝 변하게
		val t = ((System.nanoTime() - startNs) / 1_000_000_000.0).toFloat()
		val r = 0.8f + 0.2f * ((sin(t * 1.1) + 1f) * 0.5f).toFloat()  // 0.8~1.0
		val g = 0.2f + 0.3f * ((sin(t * 0.9 + 1.57) + 1f) * 0.5f).toFloat() // 위상 약간 변경
		val b = 0.3f + 0.2f * ((sin(t * 1.3 + 0.7) + 1f) * 0.5f).toFloat()
		GLES20.glUniform4f(aColorLocation, r, g, b, 1.0f)

		// 5) 그리기
		GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)

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
		private const val TAG = "TriangleRenderer"
	}
}