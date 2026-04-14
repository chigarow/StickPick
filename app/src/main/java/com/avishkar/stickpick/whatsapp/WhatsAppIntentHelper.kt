package com.avishkar.stickpick.whatsapp

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast

object WhatsAppIntentHelper {
    private const val TAG = "AddStickerPack"
    private const val ADD_PACK = 200
    const val CONSUMER_WHATSAPP_PACKAGE = "com.whatsapp"
    const val SMB_WHATSAPP_PACKAGE = "com.whatsapp.w4b"

    fun addStickerPackToWhatsApp(activity: Activity, identifier: String, stickerPackName: String) {
        launchForPackage(activity, identifier, stickerPackName, CONSUMER_WHATSAPP_PACKAGE)
    }

    fun addStickerPackToWhatsAppBusiness(activity: Activity, identifier: String, stickerPackName: String) {
        launchForPackage(activity, identifier, stickerPackName, SMB_WHATSAPP_PACKAGE)
    }

    private fun launchForPackage(activity: Activity, identifier: String, stickerPackName: String, packageName: String) {
        try {
            if (!isPackageInstalled(packageName, activity.packageManager)) {
                Toast.makeText(activity, "$packageName is not installed", Toast.LENGTH_LONG).show()
                return
            }
            val intent = createIntentToAddStickerPack(identifier, stickerPackName)
            intent.setPackage(packageName)
            activity.startActivityForResult(intent, ADD_PACK)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, "Could not add sticker pack", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "error adding sticker pack", e)
            Toast.makeText(activity, "Could not add sticker pack", Toast.LENGTH_LONG).show()
        }
    }

    private fun createIntentToAddStickerPack(identifier: String, stickerPackName: String): Intent {
        return Intent().apply {
            action = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
            putExtra("sticker_pack_id", identifier)
            putExtra("sticker_pack_authority", StickerContentProvider.AUTHORITY)
            putExtra("sticker_pack_name", stickerPackName)
        }
    }

    private fun isPackageInstalled(packageName: String, pm: PackageManager): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
}
