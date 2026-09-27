package com.kyanro.ibiki_logger.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LicensesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val notices by produceState("読み込み中…") {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val mainDocuments = listOf("LICENSE", "ASSET_LICENSES.md", "THIRD_PARTY_NOTICES.md")
                val additionalDocuments = context.assets.list("licenses").orEmpty().filterNot { it in mainDocuments }.sorted()
                (mainDocuments + additionalDocuments).joinToString("\n\n") { name ->
                    context.assets.open("licenses/$name").bufferedReader().use { "$name\n\n${it.readText()}" }
                }
            }.getOrDefault("ライセンスを読み込めませんでした。")
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ライセンスとクレジット") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Text("Copyright 2026 kyanro\n\nコードと文書：Apache License 2.0\n音源：© 2026 kyanro（別の権利表示）\n\n", fontSize = 14.sp)
                Text(notices, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }
    )
}
