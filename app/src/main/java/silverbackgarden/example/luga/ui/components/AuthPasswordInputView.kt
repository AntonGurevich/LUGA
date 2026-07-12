package silverbackgarden.example.luga.ui.components

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import silverbackgarden.example.luga.R

/**
 * Password input with a show/hide toggle, mirroring iOS AuthPasswordInput.
 */
class AuthPasswordInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val labelView: TextView
    val editText: EditText
    private val errorView: TextView
    private val toggleButton: ImageButton
    private var isVisible = false

    var text: String
        get() = editText.text.toString()
        set(value) = editText.setText(value)

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_auth_password_input, this, true)
        labelView = findViewById(R.id.label)
        editText = findViewById(R.id.input)
        errorView = findViewById(R.id.errorText)
        toggleButton = findViewById(R.id.toggleVisibility)

        val a = context.obtainStyledAttributes(attrs, R.styleable.AuthPasswordInputView)
        labelView.text = a.getString(R.styleable.AuthPasswordInputView_authLabel)
        editText.hint = a.getString(R.styleable.AuthPasswordInputView_authHint)
        a.recycle()

        toggleButton.setOnClickListener {
            isVisible = !isVisible
            applyVisibility()
        }
        applyVisibility()
    }

    private fun applyVisibility() {
        val selectionStart = editText.selectionStart
        editText.inputType = if (isVisible) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        toggleButton.setImageResource(
            if (isVisible) silverbackgarden.example.luga.R.drawable.ic_visibility
            else silverbackgarden.example.luga.R.drawable.ic_visibility_off
        )
        if (selectionStart >= 0) editText.setSelection(selectionStart.coerceAtMost(editText.text.length))
    }

    fun setError(message: String?) {
        if (message.isNullOrEmpty()) {
            errorView.visibility = GONE
            (editText.parent as? android.view.View)?.background =
                context.getDrawable(R.drawable.bg_auth_input)
        } else {
            errorView.text = message
            errorView.visibility = VISIBLE
            (editText.parent as? android.view.View)?.background =
                context.getDrawable(R.drawable.bg_auth_input_error)
        }
    }
}
