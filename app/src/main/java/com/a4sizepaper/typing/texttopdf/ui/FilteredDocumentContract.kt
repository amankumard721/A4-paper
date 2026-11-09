package com.a4sizepaper.typing.texttopdf.ui

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts

/** Keep the narrowest base type as well as MIME extras for device/provider compatibility. */
internal class FilteredDocumentContract : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).apply {
            type = if (input.size == 1) input.single() else "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
}
