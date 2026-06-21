package com.txqr.reader.decoder

import android.util.Base64
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.random.Random

/**
 * TXQR Protocol Decoder
 *
 * Decodes animated QR code frames encoded by txqr-gif.
 * Frame format: "blockCode/chunkLen/total|data"
 *
 * Uses fountain codes (LT codes) for error-resilient transmission.
 */
class TxqrDecoder {

    private var chunkLen = 0
    private var totalSize = 0
    private var completed = false
    private val receivedBlocks = mutableMapOf<Long, ByteArray>() // blockCode -> data
    private val seenHeaders = mutableSetOf<String>()
    private var frameCount = 0

    // Fountain code state
    private var numSourceBlocks = 0
    private var decodedBlocks = mutableMapOf<Int, ByteArray>() // source block index -> data

    /**
     * Feed a QR frame to the decoder.
     * @param frame The raw string content of the QR code.
     * @return true if decoding is complete after this frame.
     */
    fun decodeChunk(frame: String): Boolean {
        if (completed) return true

        val pipeIdx = frame.indexOf('|')
        if (pipeIdx == -1) return false

        val header = frame.substring(0, pipeIdx)
        if (seenHeaders.contains(header)) return false
        seenHeaders.add(header)
        frameCount++

        val parts = header.split("/")
        if (parts.size != 3) return false

        val blockCode = parts[0].toLongOrNull() ?: return false
        val cl = parts[1].toIntOrNull() ?: return false
        val total = parts[2].toIntOrNull() ?: return false

        val payload = frame.substring(pipeIdx + 1).toByteArray(Charsets.UTF_8)

        if (chunkLen == 0) {
            chunkLen = cl
            totalSize = total
            numSourceBlocks = ceil(total.toDouble() / chunkLen).toInt()
        }

        receivedBlocks[blockCode] = payload

        // Try to decode using LT codes
        tryDecode()

        return completed
    }

    /**
     * LT codes decoding: iterative peeling decoder
     */
    private fun tryDecode() {
        if (numSourceBlocks == 0) return

        // Build the decoding matrix
        // Each received block with blockCode i is XOR of some source blocks
        // For LT codes, blockCode determines which source blocks are XORed together

        // Simple approach: for each received block, try to determine which source blocks it covers
        // Then use Gaussian elimination / peeling to solve

        // First pass: find degree-1 blocks (blocks that cover exactly one source block)
        // For LT codes with soliton distribution, we can reconstruct the degree from blockCode

        val remaining = receivedBlocks.toMutableMap()
        var progress = true

        while (progress) {
            progress = false
            val toRemove = mutableListOf<Long>()

            for ((blockCode, data) in remaining) {
                val degree = getDegree(blockCode, numSourceBlocks)
                if (degree == 1) {
                    // This block covers exactly one source block
                    val sourceIdx = getSourceBlockIndex(blockCode, numSourceBlocks)
                    if (sourceIdx in 0 until numSourceBlocks && !decodedBlocks.containsKey(sourceIdx)) {
                        decodedBlocks[sourceIdx] = data
                        toRemove.add(blockCode)
                        progress = true
                    }
                }
            }

            // Remove decoded blocks
            for (code in toRemove) {
                remaining.remove(code)
            }

            // Second pass: XOR out known source blocks from multi-degree blocks
            if (progress) {
                val toUpdate = mutableListOf<Pair<Long, ByteArray>>()
                for ((blockCode, data) in remaining) {
                    val degree = getDegree(blockCode, numSourceBlocks)
                    val sourceIndices = getSourceBlockIndices(blockCode, numSourceBlocks)

                    var unknownCount = 0
                    var lastUnknownIdx = -1
                    for (idx in sourceIndices) {
                        if (!decodedBlocks.containsKey(idx)) {
                            unknownCount++
                            lastUnknownIdx = idx
                        }
                    }

                    if (unknownCount == 1) {
                        // XOR out all known source blocks
                        var result = data.copyOf()
                        for (idx in sourceIndices) {
                            if (idx != lastUnknownIdx && decodedBlocks.containsKey(idx)) {
                                result = xorBytes(result, decodedBlocks[idx]!!)
                            }
                        }
                        decodedBlocks[lastUnknownIdx] = result
                        toUpdate.add(Pair(blockCode, data))
                        progress = true
                    }
                }
                for ((code, _) in toUpdate) {
                    remaining.remove(code)
                }
            }
        }

        // Check if we have all source blocks
        if (decodedBlocks.size >= numSourceBlocks) {
            // Reconstruct the original data
            val output = ByteArray(totalSize)
            var offset = 0
            for (i in 0 until numSourceBlocks) {
                val block = decodedBlocks[i] ?: return
                val copyLen = minOf(block.size, totalSize - offset)
                block.copyInto(output, offset, 0, copyLen)
                offset += copyLen
            }

            // The decoded data is base64 encoded (txqr-gif: file -> base64 -> fountain codes)
            try {
                val decoded = Base64.decode(output, Base64.DEFAULT)
                decodedData = decoded
            } catch (e: Exception) {
                decodedData = output
            }
            completed = true
        }
    }

    private var decodedData: ByteArray? = null

    fun isCompleted(): Boolean = completed
    fun dataBytes(): ByteArray = decodedData ?: ByteArray(0)
    fun progress(): Int {
        if (completed) return 100
        if (numSourceBlocks == 0) return 0
        val p = decodedBlocks.size * 100 / numSourceBlocks
        return if (p > 99) 99 else p
    }
    fun totalSize(): Int = totalSize
    fun uniqueFrames(): Int = frameCount

    fun reset() {
        chunkLen = 0
        totalSize = 0
        completed = false
        receivedBlocks.clear()
        seenHeaders.clear()
        frameCount = 0
        numSourceBlocks = 0
        decodedBlocks.clear()
        decodedData = null
    }

    /**
     * Get the degree (number of source blocks XORed) for a given blockCode.
     * Uses the soliton distribution CDF to determine degree.
     */
    private fun getDegree(blockCode: Long, n: Int): Int {
        if (n <= 0) return 1

        // Use a deterministic mapping from blockCode to degree
        // based on the soliton distribution
        val rng = Random(blockCode * 31 + 17)
        val r = rng.nextDouble()

        // Soliton distribution CDF
        var cdf = 0.0
        for (k in 1..n) {
            cdf += solitonPMF(k, n)
            if (r < cdf) return k
        }
        return 1
    }

    /**
     * Get the source block indices that a given block covers.
     */
    private fun getSourceBlockIndices(blockCode: Long, n: Int): List<Int> {
        val degree = getDegree(blockCode, n)
        val rng = Random(blockCode * 31 + 17)
        rng.nextDouble() // consume the degree random value

        val indices = mutableSetOf<Int>()
        while (indices.size < degree) {
            indices.add(rng.nextInt(n))
        }
        return indices.toList()
    }

    /**
     * Get the single source block index for degree-1 blocks.
     */
    private fun getSourceBlockIndex(blockCode: Long, n: Int): Int {
        return getSourceBlockIndices(blockCode, n).first()
    }

    /**
     * Soliton distribution PMF
     */
    private fun solitonPMF(k: Int, n: Int): Double {
        if (k == 1) return 1.0 / n
        return 1.0 / (k * (k - 1))
    }

    /**
     * XOR two byte arrays
     */
    private fun xorBytes(a: ByteArray, b: ByteArray): ByteArray {
        val maxLen = maxOf(a.size, b.size)
        val result = ByteArray(maxLen)
        for (i in 0 until maxLen) {
            val av = if (i < a.size) a[i].toInt() and 0xFF else 0
            val bv = if (i < b.size) b[i].toInt() and 0xFF else 0
            result[i] = (av xor bv).toByte()
        }
        return result
    }
}
