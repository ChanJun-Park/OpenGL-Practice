package com.example.myopengltest

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MashGradientEffectRenderer : GLSurfaceView.Renderer {

	// region === 셰이더 소스 ===
	// Vertex Shader: vec2 정점 좌표를 받아 동차좌표(vec4)로 확장
	private val vertexShaderCode = """
		attribute vec2 a_Position;
		varying vec2 v_UV;
		uniform float u_AspectInv;
		
		void main() {
			vec2 pos = a_Position;
			pos.x *= u_AspectInv;
			// pos.y /= u_AspectInv;
			// [-1,1] → [0,1]로 정규화
			v_UV = (a_Position + 1.0) * 0.5;
			gl_Position = vec4(pos, 0.0, 1.0);
		}
    """.trimIndent()

	// Fragment Shader: 단색 렌더링 (u_Color로 주입)
	private val fragmentShaderCode = """
		precision mediump float;

		uniform float u_Time;
		varying vec2 v_UV;
		
		// Gradient 중심 위치 정의
		vec2 baseCenter1 = vec2(0.5, 0.5);
		vec2 baseCenter2 = vec2(0.5, 0.5);
		const float PI = 3.14159265359;
		
		void main() {
			// 시간 기반 원운동 궤적 생성
			float radius1 = 0.10;
			float radius2 = 0.10;
			
			float speed = 1.0; // 회전 속도 (radians/sec)
			vec2 offset1 = vec2(
				cos(u_Time * speed),
				sin(u_Time * speed)
			) * radius1;
			
			vec2 offset2 = vec2(
				cos(u_Time * speed + PI),
				sin(u_Time * speed + PI)
			) * radius2;
			
			vec2 center1 = baseCenter1 + offset1;
			vec2 center2 = baseCenter2 + offset2;
		
			float minimumDistance = distance(center1, center2);
		
			// 거리 기반 밝기 계산
			float d1 = distance(v_UV, center1);
			float d2 = distance(v_UV, center2);
		
			float brightness1 = 1.0 - smoothstep(0.0, 0.3, d1);
			float brightness2 = 1.0 - smoothstep(0.0, 0.3, d2);
			
			// float brightness1 = d1 >= 0.2 ? 1.0 : 0.0;
			// float brightness2 = d2 >= 0.2 ? 1.0 : 0.0;
			
			float totalBrightness = brightness1 + brightness2 + 0.0001;
			
			float weight = 2.0 - smoothstep(0.0, 2.0, d1 + d2);

			vec3 color1 = vec3(1.0, 1.0, 1.0) - vec3(0.6471, 0.3255, 1.0);
			vec3 color2 = vec3(1.0, 1.0, 1.0) - vec3(0.4235, 0.5569, 1.0);
		
			vec3 finalColor = (color1 * brightness1 + color2 * brightness2);
			// vec3 finalColor = (color1 * brightness1 + color2 * brightness2) / weight;
			// vec3 finalColor = (color1 * brightness1 + color2 * brightness2) / 2.0;
			
			vec3 inverse = vec3(1.0, 1.0, 1.0) - finalColor;
		
			// gl_FragColor = vec4(finalColor, 1.0);
			gl_FragColor = vec4(inverse, 1.0);
		}
    """.trimIndent()
	// endregion

	// x, y
	// 어떤 순서로 면을 만들던 fragment shader에 전달되는 좌표는 동일한듯
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
	// endregion

	// region === GL 핸들 ===
	private var program = 0
	private var aPositionLocation = 0
	private var uTimeLocation = 0
	private var uAspectInvLocation = 0
	// endregion

	// 시간 기반 애니메이션용
	private var lastMs: Long = 0L
	private var accumulatedTime: Float = 0f

	override fun onSurfaceCreated(p0: GL10?, p1: EGLConfig?) {
		GLES20.glEnable(GLES20.GL_BLEND)
		GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
		lastMs = System.currentTimeMillis()

		// 1) 클리어 컬러(배경색) 초기 설정
		GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f)

		// 2) 정점 버퍼 준비
		vertexBuffer = ByteBuffer
			.allocateDirect(quadVertices.size * BYTES_PER_FLOAT)
			.order(ByteOrder.nativeOrder())
			.asFloatBuffer()
			.put(quadVertices)
		vertexBuffer.position(0)

		// 3) 셰이더 컴파일 & 프로그램 링크
		val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
		val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
		program = linkProgram(vertexShader, fragmentShader)

		// 4) attribute/uniform 위치 조회
		aPositionLocation = GLES20.glGetAttribLocation(program, "a_Position")
		uTimeLocation = GLES20.glGetUniformLocation(program, "u_Time")
		uAspectInvLocation = GLES20.glGetUniformLocation(program, "u_AspectInv")

		// (선택) 유효성 검사
		validateProgram(program)
	}

	override fun onSurfaceChanged(p0: GL10?, width: Int, height: Int) {
		// 그릴 영역 지정 (픽셀 단위)
		GLES20.glViewport(0, 0, width, height)


		val aspectInv = height.toFloat() / width.toFloat()  // ★ height / width
		GLES20.glUseProgram(program)
		GLES20.glUniform1f(uAspectInvLocation, aspectInv)
	}

	override fun onDrawFrame(unused: GL10?) {
		// 1) 배경 지우기
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

		// 2) 프로그램 사용
		GLES20.glUseProgram(program)

		val currentTimeMillis = System.currentTimeMillis()
		val t = (currentTimeMillis - lastMs) / 1000f
		lastMs = currentTimeMillis
		accumulatedTime += t
		if (accumulatedTime > 10f) {
			accumulatedTime %= (2f * Math.PI.toFloat())
		}
		GLES20.glUniform1f(uTimeLocation, accumulatedTime)

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
		private const val TAG = "BackgroundEffectRenderer"
	}

}