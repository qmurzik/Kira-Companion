package com.kira.companion.ai

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens the official ChatGPT app (if installed) or its website in the browser, using a
 * completely ordinary [Intent.ACTION_VIEW]. This is the entire integration: no login is
 * performed, no session/cookie/token is read from the ChatGPT app or any browser, and no
 * API key is required. Kira simply hands the user off to OpenAI's own official surface.
 */
private const val CHATGPT_URL = "https://chatgpt.com/"
private const val CHATGPT_PACKAGE = "com.openai.chatgpt"

fun openChatGpt(context: Context) {
    val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse(CHATGPT_URL)).apply {
        setPackage(CHATGPT_PACKAGE)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(appIntent)
        return
    } catch (e: ActivityNotFoundException) {
        // Official app isn't installed - fall back to the browser below.
    }

    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(CHATGPT_URL)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(browserIntent)
}
