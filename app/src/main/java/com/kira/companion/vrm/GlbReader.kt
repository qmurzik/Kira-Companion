package com.kira.companion.vrm

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Raw structural parsing of the glTF Binary (.glb) container - the format both plain
 * .glb files and .vrm files use (a .vrm is a .glb whose JSON chunk carries a VRM
 * extension under `extensions.VRM` or `extensions.VRMC_vrm`). We only need the JSON
 * chunk here; Filament's own glTF loader (via SceneView) separately reads the binary
 * mesh/texture chunk when it loads the model for rendering.
 *
 * Container layout (little-endian):
 *  - header: magic "glTF" (4 bytes) + version (uint32) + total length (uint32)
 *  - one or more chunks: chunk length (uint32) + chunk type (4 ASCII bytes) + data
 */
object GlbReader {

    class InvalidGlbException(message: String) : Exception(message)

    private const val HEADER_SIZE = 12
    private const val CHUNK_HEADER_SIZE = 8
    private const val CHUNK_TYPE_JSON = "JSON"

    /** Extracts and returns the UTF-8 JSON chunk text from raw .glb/.vrm bytes. */
    fun extractJsonChunk(bytes: ByteArray): String {
        if (bytes.size < HEADER_SIZE) {
            throw InvalidGlbException("Файл слишком мал, чтобы быть корректным .vrm/.glb")
        }
        val magic = String(bytes, 0, 4, Charsets.US_ASCII)
        if (magic != "glTF") {
            throw InvalidGlbException("Не является glTF Binary (.glb/.vrm) файлом: неверная сигнатура")
        }

        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val totalLength = header.getInt(8)
        if (totalLength < 0 || totalLength > bytes.size) {
            throw InvalidGlbException("Файл повреждён или обрезан (некорректная длина в заголовке)")
        }

        var offset = HEADER_SIZE
        while (offset + CHUNK_HEADER_SIZE <= bytes.size) {
            val chunkLength = header.getInt(offset)
            val chunkType = String(bytes, offset + 4, 4, Charsets.US_ASCII)
            val dataStart = offset + CHUNK_HEADER_SIZE
            val dataEnd = dataStart + chunkLength
            if (chunkLength < 0 || dataEnd > bytes.size) {
                throw InvalidGlbException("Повреждён .vrm/.glb: чанк выходит за пределы файла")
            }
            if (chunkType == CHUNK_TYPE_JSON) {
                return String(bytes, dataStart, chunkLength, Charsets.UTF_8)
            }
            offset = dataEnd
        }
        throw InvalidGlbException("В .vrm/.glb не найден JSON-чанк")
    }
}
