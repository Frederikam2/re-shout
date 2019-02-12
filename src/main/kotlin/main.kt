package com.frederikam.retroice

import java.io.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.time.temporal.ChronoField
import java.time.temporal.TemporalAccessor
import java.util.concurrent.TimeUnit

data class DataPair(var audio: File, val meta: File)

fun main() {
    val dir = File("tmp")
    dir.mkdir()

    doStream(dir)
}

fun doStream(tmpDir: File) {
    val client = HttpClient.newHttpClient()
    val request = HttpRequest.newBuilder()
        .uri(URI.create("http://live-icy.gss.dr.dk/A/A22H.mp3"))
        .header("Icy-MetaData", "1")
        .build()

    var metadataInterval = 0
    val future = client.sendAsync(request) {
        metadataInterval = it.headers().firstValue("icy-metaint").get().toInt()
        HttpResponse.BodySubscribers.ofInputStream()
    }
    future.get(1, TimeUnit.MINUTES).body().use { inStream ->
        val now = Instant.now()
        val key = "${now[ChronoField.YEAR]}-${now[ChronoField.DAY_OF_YEAR]}"
        val pair = DataPair(tmpDir.resolve("$key.mp3"), tmpDir.resolve("$key.meta"))
        println("Started streaming to ${pair.audio}")

        val day = now[ChronoField.EPOCH_DAY]
        val endTime = (day + 1) * TimeUnit.DAYS.toMillis(1)

        FileOutputStream(pair.audio, true).use { outStream ->
            FileOutputStream(pair.meta, true).use {
                writeFiles(inStream, outStream, it, metadataInterval, endTime)
            }
        }

        publishAsync(pair)
    }
}

fun writeFiles(
    inputStream: InputStream,
    outputStream: OutputStream,
    metadataStream: OutputStream,
    metaInterval: Int,
    endTime: Long
) {
    while (System.currentTimeMillis() < endTime) {
        val read = inputStream.readNBytes(metaInterval)
        outputStream.write(read)

        var meta = String(inputStream.readNBytes(inputStream.read() * 16))

        if (meta.isEmpty()) continue

        meta = meta.takeWhile { it != Char.MIN_VALUE }
            .drop("StreamTitle='".length)
            .dropLast(2)

        if (meta.isEmpty()) meta = "Unknown"

        meta = Instant.now().toString() + " $meta"

        metadataStream.write("$meta\n".toByteArray())
        println(meta)
    }
}
