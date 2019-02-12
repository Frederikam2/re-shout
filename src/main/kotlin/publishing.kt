package com.frederikam.retroice

import java.io.File

fun publishAsync(pair: DataPair) {
    val newFile = File("audio.opus")
    encode(pair.audio, pair.audio)
    pair.audio = newFile
}

fun encode(inF: File, outF: File) {
    ProcessBuilder("ffmpeg -y -i ${inF.path} -b:a 48k ${outF.path}".split(" "))
        .inheritIO()
        .start()
        .waitFor()
}