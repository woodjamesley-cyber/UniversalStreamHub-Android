package com.universalstreamhub.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.universalstreamhub.app.data.remote.ProviderLinkDto

/**
 * Opens a licensed title in the provider's official Android app through an app
 * link, falling back to the provider's official website in the user's browser.
 * This is a handoff to the provider - never an embedded browser or fake player.
 */
object ProviderLauncher {

    enum class Result { APP_LINK, APP_HOME, WEBSITE, NONE }

    fun launch(context: Context, link: ProviderLinkDto, title: String): Result {
        if (link.packageName.isNotBlank()) {
            val direct = Intent(Intent.ACTION_VIEW, Uri.parse(link.webUrl)).apply {
                setPackage(link.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (direct.resolveActivity(context.packageManager) != null) {
                return try {
                    context.startActivity(direct)
                    Result.APP_LINK
                } catch (_: ActivityNotFoundException) {
                    Result.NONE
                }
            }
            val home = context.packageManager.getLaunchIntentForPackage(link.packageName)
            if (home != null) {
                home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return try {
                    context.startActivity(home)
                    Toast.makeText(
                        context,
                        "Opening ${link.name}. Search for \"$title\" there to watch.",
                        Toast.LENGTH_LONG,
                    ).show()
                    Result.APP_HOME
                } catch (_: ActivityNotFoundException) {
                    Result.NONE
                }
            }
        }

        val web = Intent(Intent.ACTION_VIEW, Uri.parse(link.webUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (web.resolveActivity(context.packageManager) != null) {
            return try {
                context.startActivity(web)
                Result.WEBSITE
            } catch (_: ActivityNotFoundException) {
                Result.NONE
            }
        }

        Toast.makeText(
            context,
            "No app or browser is available for ${link.name}. Open ${link.webUrl} on another device.",
            Toast.LENGTH_LONG,
        ).show()
        return Result.NONE
    }

    fun openExternal(context: Context, url: String, failureMessage: String = "Nothing on this device can open that link.") {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) != null) {
            try {
                context.startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                // fall through
            }
        }
        Toast.makeText(context, failureMessage, Toast.LENGTH_LONG).show()
    }

    /** YouTube app/site links are the legal way to show provider trailers. */
    fun openTrailer(context: Context, youtubeKey: String) {
        openExternal(
            context,
            "https://www.youtube.com/watch?v=$youtubeKey",
            "No app is available to play the trailer.",
        )
    }
}
