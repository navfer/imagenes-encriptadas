package imagenesEncriptadas

/**
 * Imágenes cifradas con AES de 2 bytes.
 *
 */
import kotlinx.coroutines.*
import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

fun expandKey(keyHex: Int): ByteArray {
    val keyBytes = ByteArray(2)
    keyBytes[0] = (keyHex shr 8 and 0xff).toByte() // Extrae el primer byte
    keyBytes[1] = (keyHex and 0xff).toByte() // Extrae el segundo byte
    // Expande los bytes a 16 bytes
    return ByteArray(16) { keyBytes[it % keyBytes.size] }
}

suspend fun decryptFile(encryptedData: ByteArray): ByteArray? = coroutineScope {
    val iv = encryptedData.take(16).toByteArray()
    val encryptedContent = encryptedData.drop(16).toByteArray()

    val rangeSize = 0xFFFF / 100 // Dividimos en 100 partes
    val jobList = mutableListOf<Job>()
    val resultList = mutableListOf<Deferred<ByteArray?>>()

    for (part in 0 until 100) {
        val start = part * rangeSize
        val end = if (part == 99) 0xFFFF else (start + rangeSize - 1)
        print("Comienza $start hasta $end")
        val job = async {

            for (key in start..end) {
                val key128 = expandKey(key)
                val cipher = Cipher.getInstance("AES/CBC/NoPadding")
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    SecretKeySpec(key128, "AES"),
                    IvParameterSpec(iv)
                )
                val decrypted = cipher.doFinal(encryptedData).drop(16).toByteArray()
                if (jpg(decrypted)) {
                    println("El rango $start - $end encotró la clave")
                    //que salga desde aqui
                    return@async decrypted
                }
            }
            null
        }
        jobList.add(job)
        resultList.add(job)
    }

    resultList.awaitAll().firstOrNull { it != null }
}

fun jpg(x: ByteArray): Boolean{
    return x[0] == 0xFF.toByte() && x[1] == 0xD8.toByte() && x[2] == 0xFF.toByte()
}

fun main() = runBlocking {
    val encryptedData = File("src/imagenesEncriptadas/img/data1.bin").readBytes()
    val decryptedData = decryptFile(encryptedData)
    if (decryptedData != null) {
        File("decrypted_image.jpg").writeBytes(decryptedData)
        println("Image decrypted successfully.")
    } else {
        println("Failed to decrypt the image.")
    }
}
