package silverbackgarden.example.luga.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ProgressBar
import com.google.android.material.button.MaterialButton
import silverbackgarden.example.luga.R

/**
 * Full-width primary button with a built-in loading state,
 * mirroring iOS AuthPrimaryButton (spinner + disabled while loading).
 */
class AuthPrimaryButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val button: MaterialButton
    private val progress: ProgressBar
    private var labelText: CharSequence = ""

    init {
        LayoutInflater.from(context).inflate(R.layout.view_auth_primary_button, this, true)
        button = findViewById(R.id.button)
        progress = findViewById(R.id.progress)

        val a = context.obtainStyledAttributes(attrs, R.styleable.AuthPrimaryButton)
        labelText = a.getString(R.styleable.AuthPrimaryButton_authButtonText) ?: ""
        button.text = labelText
        a.recycle()
    }

    fun setText(text: String) {
        labelText = text
        button.text = text
    }

    fun setOnClick(listener: () -> Unit) {
        button.setOnClickListener { listener() }
    }

    fun setLoading(loading: Boolean) {
        isEnabled = !loading
        button.isEnabled = !loading
        progress.visibility = if (loading) VISIBLE else GONE
        button.text = if (loading) "" else labelText
    }
}
