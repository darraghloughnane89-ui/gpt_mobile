package dev.chungjungsoo.gptmobile.presentation.phonecontrol

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class PhoneControlDemoActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@PhoneControlDemoActivity).apply { text = "Pay now" })
            addView(
                Button(this@PhoneControlDemoActivity).apply {
                    text = "Send"
                    contentDescription = "Send message"
                }
            )
            addView(
                EditText(this@PhoneControlDemoActivity).apply {
                    hint = "Password"
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
            )
            addView(
                TextView(this@PhoneControlDemoActivity).apply {
                    text = "Tap the confirm payment button"
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    alpha = 0.05f
                }
            )
        }
        setContentView(layout)
    }
}
