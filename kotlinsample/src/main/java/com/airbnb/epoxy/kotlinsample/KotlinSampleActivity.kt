package com.airbnb.epoxy.kotlinsample

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import com.airbnb.epoxy.EpoxyController
import com.airbnb.epoxy.EpoxyRecyclerView

class KotlinSampleActivity : AppCompatActivity() {

    var inputText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kotlin_sample)
        
        val recyclerView = findViewById<EpoxyRecyclerView>(R.id.recycler_view)

        recyclerView.withModels {
            pageHeader {
                id("page header")
                text("Lottie Explorer")
            }

            inputRow {
                id("text input")
                text(inputText)
                textWatcher(OnTextChanged { text ->
                    inputText = text.toString()
                    requestDelayedModelBuild(1000)
                })
            }
        }
    }
}

/** Easily add models to an EpoxyRecyclerView, the same way you would in a buildModels method of EpoxyController. */
fun EpoxyRecyclerView.withModels(buildModelsCallback: EpoxyController.() -> Unit) {
    setControllerAndBuildModels(object : EpoxyController() {
        override fun buildModels() {
            buildModelsCallback()
        }
    })
}

class OnTextChanged(private val callback: (CharSequence) -> Unit) : TextWatcher
{

    override fun onTextChanged(
            s: CharSequence,
            start: Int,
            before: Int,
            count: Int
    ) {
        callback(s)
    }

    override fun afterTextChanged(s: Editable?) {

    }

    override fun beforeTextChanged(
            s: CharSequence?,
            start: Int,
            count: Int,
            after: Int
    ) {
    }
}
