package com.example.myopengltest.shadowview

import android.content.Context
import android.graphics.RectF
import android.util.AttributeSet
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout

class GradientShadowSurfaceContainer @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
	private val rect = RectF()
	private val glView = GradientShadowGLSurfaceView(context)

	init {
		addView(glView, LayoutParams(MATCH_PARENT, MATCH_PARENT))
	}

	override fun onLayout(
		changed: Boolean,
		l: Int,
		t: Int,
		r: Int,
		b: Int,
	) {
		super.onLayout(changed, l, t, r, b)

		// 섀도우는 보통 특정 자식(버튼)의 경계에 맞춥니다.
		// 여기서는 첫 번째 "콘텐츠" 자식을 타겟으로 가정
		val target = getChildAt(childCount - 1) ?: return // 마지막에 추가된 콘텐츠
		rect.set(
			target.left.toFloat(),
			target.top.toFloat(),
			target.right.toFloat(),
			target.bottom.toFloat(),
		)

		// FrameLayout(나 자신) 좌표계 → GL은 같은 좌표계를 사용하므로 그대로 전달
		glView.setButtonRect(rect, radiusPx = 0f)
	}
}