package com.frederikam.retroice

import java.io.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.concurrent.TimeUnit


fun main() {
    val dir = File("tmp")
    dir.mkdir()

    doStream(File("tmp/" + Instant.now().toString() + ".mp3"))
    doStream(File("tmp/" + Instant.now().toString() + ".mp3"))

    val meta = joinFiles(dir, "meta")
    val mp3Joined = joinFiles(dir, "mp3")
    val opus = File(dir, "final.opus")
    encode(mp3Joined, opus)

    println("Produced files $opus and $meta")
}

fun doStream(outFile: File) {
    val client = HttpClient.newHttpClient()
    val request = HttpRequest.newBuilder()
        .uri(URI.create("http://live-icy.gss.dr.dk/A/A22H.mp3"))
        .header("Icy-MetaData", "1")
        .build()

    var metadataInterval = 0
    val future = client.sendAsync<InputStream>(request) {
        metadataInterval = it.headers().firstValue("icy-metaint").get().toInt()
        HttpResponse.BodySubscribers.ofInputStream()
    }
    future.get(1, TimeUnit.MINUTES).body().use { inStream ->
        FileOutputStream(outFile).use { outStream ->
            FileOutputStream(outFile.path + ".meta").use {
                handleIo(inStream, outStream, it, metadataInterval)
            }
        }
    }
}

fun handleIo(
    inputStream: InputStream,
    outputStream: OutputStream,
    metadataStream: OutputStream,
    metaInterval: Int
) {
    var saved = 0
    while (saved < 500_000) {
        val read = inputStream.readNBytes(metaInterval)
        saved += read.size
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

fun joinFiles(dir: File, extension: String): File {
    val files = dir.listFiles().filter { it.extension == extension }
        .sortedBy { it.lastModified() }

    if (files.size == 1) return files[0]

    val endFile = files[0]
    FileOutputStream(endFile, true).use { out ->
        files.drop(1).forEach {
            FileInputStream(it).transferTo(out)
        }
    }

    return endFile
}

fun encode(inF: File, outF: File) {
    /*val concatList = dirF.listFiles()
        .filter { it.extension == "mp3" }
        .map { "file '${it.relativeTo(dirF).path}'\n" }
        .reduce { acc, s -> acc + s }

    FileOutputStream(File(dir, "concatlist")).use {
        it.write(concatList.toByteArray())
    }

    ProcessBuilder("ffmpeg -y -f concat -safe 0 -i $dir/concatlist -c copy $dir/temp.mp3".split(" "))
        .inheritIO()
        .start()
        .waitFor()*/

    ProcessBuilder("ffmpeg -y -i ${inF.path} -b:a 48k ${outF.path}".split(" "))
        .inheritIO()
        .start()
        .waitFor()
}