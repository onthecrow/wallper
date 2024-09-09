package com.onthecrow.wallper.presentation.picker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

class PickMediaContract: ActivityResultContract<List<String>, Uri>() {
    override fun createIntent(context: Context, input: List<String>): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
//            type = ActivityResultContracts.PickVisualMedia.getVisualMimeType(input.mediaType)
//            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)

            if (type == null) {
                // ACTION_OPEN_DOCUMENT requires to set this parameter when launching the
                // intent with multiple mime types
                type = "*/*"
//                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            }
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri {
        return intent.takeIf {
            resultCode == Activity.RESULT_OK
        }?.getClipDataUris() ?: Uri.EMPTY
    }
}

fun Intent.getClipDataUris(): Uri {
    // Use a LinkedHashSet to maintain any ordering that may be
    // present in the ClipData
    val resultSet = LinkedHashSet<Uri>()
    data?.let { data ->
        return data
//        resultSet.add(data)
    }
    return Uri.EMPTY
//    val clipData = clipData
//    if (clipData == null && resultSet.isEmpty()) {
//        return emptyList()
//    } else if (clipData != null) {
//        for (i in 0 until clipData.itemCount) {
//            val uri = clipData.getItemAt(i).uri
//            if (uri != null) {
//                resultSet.add(uri)
//            }
//        }
//    }
//    return ArrayList(resultSet)
}