package silverbackgarden.example.luga.ui.components

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import silverbackgarden.example.luga.R

/**
 * Bordered text input with a label above and an inline error message below,
 * mirroring the iOS AuthInputField/AuthTextInput primitives.
 */
class AuthTextInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val labelView: TextView
    val editText: EditText
    private val errorView: TextView

    var text: String
        get() = editText.text.toString()
        set(value) = editText.setText(value)

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_auth_text_input, this, true)
        labelView = findViewById(R.id.label)
        editText = findViewById(R.id.input)
        errorView = findViewById(R.id.errorText)

        val a = context.obtainStyledAttributes(attrs, R.styleable.AuthTextInputView)
        labelView.text = a.getString(R.styleable.AuthTextInputView_authLabel)
        editText.hint = a.getString(R.styleable.AuthTextInputView_authHint)
        when (a.getInt(R.styleable.AuthTextInputView_authInputType, 0)) {
            1 -> editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            2 -> editText.inputType = InputType.TYPE_CLASS_NUMBER
            else -> editText.inputType = InputType.TYPE_CLASS_TEXT
        }
        a.recycle()
    }

    fun setError(message: String?) {
        if (message.isNullOrEmpty()) {
            errorView.visibility = GONE
            editText.background = context.getDrawable(R.drawable.bg_auth_input)
        } else {
            errorView.text = message
            errorView.visibility = VISIBLE
            editText.background = context.getDrawable(R.drawable.bg_auth_input_error)
        }
    }
}
