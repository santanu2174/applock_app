package com.security.lockguard.intruder

import android.content.Context
import androidx.security.crypto.EncryptedFile
import com.security.lockguard.security.KeystoreManager
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

class EncryptedStorageManager(private val context: Context) {

    private val secureDir: File = File(context.filesDir, "vault_intruders").apply {
        if (!exists()) mkdirs()
    }

    private val masterKey = KeystoreManager.getOrCreateMasterKey(context)

    fun createEncryptedTargetFile(filename: String): File {
        return File(secureDir, filename)
    }

    fun encryptAndSaveFile(sourceFile: File, targetEncryptedFile: File) {
        val encryptedOutputFile = EncryptedFile.Builder(
            context,
            targetEncryptedFile,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

        FileInputStream(sourceFile).use { input ->
            encryptedOutputFile.openFileOutput().use { output ->
                input.copyTo(output)
            }
        }
    }

    fun openEncryptedFileInputStream(encryptedFile: File): InputStream {
        val encryptedInput = EncryptedFile.Builder(
            context,
            encryptedFile,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

        return encryptedInput.openFileInput()
    }

    fun listIntruderLogFiles(): List<File> {
        return secureDir.listFiles()?.filter { it.extension == "enc" }?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }
}

