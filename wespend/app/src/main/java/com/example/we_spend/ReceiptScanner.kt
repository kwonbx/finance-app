package com.example.we_spend

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class ReceiptScanner {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun scanReceipt(context: Context, imageUri: Uri, onResult: (String, String, String) -> Unit, onError: (Exception) -> Unit) {
        try {
            val image = InputImage.fromFilePath(context, imageUri)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val fullText = visionText.text
                    val lines = fullText.lines().filter { it.isNotBlank() }

                    val shopName = lines.getOrNull(0) ?: ""
                    
                    val dateRegex = """\d{2}[.-]\d{2}[.-]\d{4}|\d{4}[.-]\d{2}[.-]\d{2}""".toRegex()
                    val date = dateRegex.find(fullText)?.value ?: ""
                    var totalAmount = ""
                    val amountRegex = """(\d+[.,]\d{2})""".toRegex()
                    val foundAmounts = mutableListOf<Double>()

                    for (line in lines) {
                        val match = amountRegex.find(line)
                        if (match != null) {
                            val amountStr = match.value.replace(",", ".")
                            val amountDouble = amountStr.toDoubleOrNull()

                            if (amountDouble != null) {
                                foundAmounts.add(amountDouble)
                            }
                        }
                    }

                    if (foundAmounts.isNotEmpty()) {
                        val bestAmount = foundAmounts.maxOrNull()

                        if (bestAmount != null) {
                            totalAmount = String.format(java.util.Locale.US, "%.2f", bestAmount)
                        }
                    }
                    onResult(shopName, date, totalAmount)
                }
                .addOnFailureListener { e ->
                    onError(e)
                }
        } catch (e: Exception) {
            onError(e)
        }
    }
}