package com.openstablediffusion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Headers.Companion.toHeaders
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ceil


class ParametersFragment : Fragment() {
    private lateinit var view: View
    private lateinit var mainInterface: MainInterface
    private lateinit var promptElement: EditText
    private lateinit var generationTypeElement: Spinner
    private lateinit var generationModelElement: Spinner
    private lateinit var imageElement: ImageButton
    private lateinit var heightElement: EditText
    private lateinit var widthElement: EditText
    private lateinit var stepsElement: EditText
    private lateinit var seedElement: EditText
    private lateinit var promptStrengthElement: EditText
    private lateinit var imageStrengthElement: EditText
    private lateinit var apikeyElement: EditText
    private lateinit var nsfwElement: CheckBox
    private lateinit var censorElement: CheckBox
    private lateinit var generateElement: Button
    private val apiUrl: String = "https://stablehorde.net/api/v2/"

    // Enum class used for setting the generation
    enum class GenerationType {
        TXT2IMG, IMG2IMG
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        view = inflater.inflate(R.layout.parameters, container, false)
        initialize()
        return view
    }

    private fun initialize() {
        mainInterface = activity as MainInterface

        promptElement = view.findViewById(R.id.prompt)

        generationTypeElement = view.findViewById(R.id.type)
        generationTypeElement.adapter = ArrayAdapter(
            requireContext(),
            R.layout.spinneritem,
            GenerationType.values()
        )

        generationModelElement = view.findViewById(R.id.model)
        generationModelElement.adapter = ArrayAdapter(
            requireContext(),
            R.layout.spinneritem,
            arrayOf("Default")
        )
        CoroutineScope(Dispatchers.IO).launch { getModels() }

        imageElement = view.findViewById(R.id.upload)
        imageElement.setOnClickListener { mainInterface.uploadImage() }

        heightElement = view.findViewById(R.id.height)
        heightElement.setText("512")

        widthElement = view.findViewById(R.id.width)
        widthElement.setText("512")

        stepsElement = view.findViewById(R.id.steps)
        stepsElement.setText("30")

        seedElement = view.findViewById(R.id.seed)

        promptStrengthElement = view.findViewById(R.id.promptStrength)
        promptStrengthElement.setText("7")

        imageStrengthElement = view.findViewById(R.id.imageStrength)
        imageStrengthElement.setText("0.4")

        apikeyElement = view.findViewById(R.id.apikey)

        nsfwElement = view.findViewById(R.id.nsfw)

        censorElement = view.findViewById(R.id.censor)

        generateElement = view.findViewById(R.id.generate)
        generateElement.setOnClickListener {
            generateRequest()
            CoroutineScope(Dispatchers.IO).launch { getModels() }
        }
    }

    private fun generateRequest() {
        mainInterface.displayError("")

        val generationType: Any = generationTypeElement.selectedItem

        val model: String = generationModelElement.selectedItem.toString()

        // Make sure all the necessary parameters are filled in
        val prompt: String = promptElement.text.toString()
        if (prompt == "") {
            mainInterface.displayError("Enter a prompt!")
            return
        }
        var height: Double? = heightElement.text.toString().toDoubleOrNull()
        var width: Double? = widthElement.text.toString().toDoubleOrNull()
        if (height == null || width == null) {
            mainInterface.displayError("Enter a width and height")
            return
        }
        var steps: Int? = stepsElement.text.toString().toIntOrNull()
        if (steps == null) {
            mainInterface.displayError("Enter a number of steps!")
            return
        }
        var promptStrength: Float? = promptStrengthElement.text.toString().toFloatOrNull()
        if (promptStrength == null) {
            mainInterface.displayError("Enter a prompt strength!")
            return
        }

        // Optional parameters
        var apikey: String = apikeyElement.text.toString()
        val seed: String = seedElement.text.toString()
        val nsfw: Boolean = nsfwElement.isChecked
        val censor: Boolean = censorElement.isChecked

        // Height and width needs to be a multiple of 64
        if(height % 64 != .0) {
            height = ceil(height/64) *64
        }
        if(width % 64 != .0) {
            width = ceil(width/64)*64
        }

        val headers = mapOf(
            "Content-Type" to "application/json",
            "accept" to "application/json",
            "apikey" to if(apikey==""){"0000000000"}else{"$apikey"}
        )
        // Creates the HTTP request based on selected generation type
        when (generationType) {
            GenerationType.TXT2IMG -> txt2img(prompt, model, steps, promptStrength,
                nsfw, censor, seed, height, width, headers)
            GenerationType.IMG2IMG -> img2img(prompt, model, steps, promptStrength,
                nsfw, censor, seed, height, width, headers)
        }
    }

    private fun txt2img(prompt: String,
                        model: String,
                        steps: Int,
                        promptStrength: Float,
                        nsfw: Boolean,
                        censor: Boolean,
                        seed: String,
                        height: Double,
                        width: Double,
                        headers: Map<String, String>) {

        // Create the request with all the necessary parameters
        val params = """
        {
            "cfg_scale": $promptStrength,
            "steps": $steps,
            ${if(seed==""){""}else{"\"seed\": \"$seed\","}}
            "width": ${width.toInt()},
            "height": ${height.toInt()}
        }
        """.trimIndent()
        val requestBody = """
        {
            "params": $params,
            "prompt": "$prompt",
            "nsfw": $nsfw,
            "censor_nsfw": $censor,
            ${if(model=="Default"){""}else{"\"models\": [\"$model\"],"}}
            "r2": true
        }
        """.trimIndent()
        val request = Request.Builder()
            .url(apiUrl + "generate/async")
            .headers(headers.toHeaders())
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
        val generationCoroutine = CoroutineScope(Dispatchers.IO).launch {
            mainInterface.generateImage(request, prompt)
        }
        mainInterface.setGenerationCoroutine(generationCoroutine)
    }

    private fun img2img(prompt: String,
                        model: String,
                        steps: Int,
                        promptStrength: Float,
                        nsfw: Boolean,
                        censor: Boolean,
                        seed: String,
                        height: Double,
                        width: Double,
                        headers: Map<String, String>) {

        val imageData = mainInterface.getImage()

        // Make sure all the necessary parameters are filled in
        if(imageData == null) {
            mainInterface.displayError("Upload an input image!")
            return
        }

        var imageStrength: Float? = imageStrengthElement.text.toString().toFloatOrNull()
        if (imageStrength == null) {
            mainInterface.displayError("Enter an input image strength!")
            return
        }

        // Create the request with all the necessary parameters
        val params = """
        {
            "cfg_scale": $promptStrength,
            "steps": $steps,
            ${if(seed==""){""}else{"\"seed\": \"$seed\","}}
            "width": ${width.toInt()},
            "height": ${height.toInt()},
            "denoising_strength": ${1f-imageStrength}
        }
        """.trimIndent()
        val requestBody = """
        {
            "source_processing": "img2img",
            "source_image": "$imageData",
            "params": $params,
            "prompt": "$prompt",
            "nsfw": $nsfw,
            "censor_nsfw": $censor,
            ${if(model=="Default"){""}else{"\"models\": [\"$model\"],"}}
            "r2": true
        }
        """.trimIndent()
        val request = Request.Builder()
            .url(apiUrl + "generate/async")
            .headers(headers.toHeaders())
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
        val generationCoroutine = CoroutineScope(Dispatchers.IO).launch {
            mainInterface.generateImage(request, prompt)
        }
        mainInterface.setGenerationCoroutine(generationCoroutine)
    }

     private suspend fun getModels() {
         var models: Array<String> = arrayOf("Default")
         val client = OkHttpClient()
         val request = Request.Builder()
            .url(apiUrl + "status/models?type=image")
            .build()
         var response: Any?

         response = client.newCall(request).execute()
         response = response.body?.string()
         response = JSONArray(response)

         for (i in 0 until response.length()){
             models += response.getJSONObject(i).get("name").toString()
         }
         val act = activity ?: return
         act.runOnUiThread {
             generationModelElement.adapter = ArrayAdapter(
             requireContext(),
             R.layout.spinneritem,
             models
         )}
    }
}