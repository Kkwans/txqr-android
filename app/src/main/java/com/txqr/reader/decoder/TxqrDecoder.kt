package com.txqr.reader.decoder

import android.util.Base64
import kotlin.math.ceil
import kotlin.random.Random

/**
 * TXQR 协议解码器
 *
 * 解码 txqr-gif 生成的动态二维码帧。
 * 帧格式: "blockCode/chunkLen/total|data"
 * 使用 LT codes (fountain codes) 实现容错传输。
 */
class TxqrDecoder {

    private var chunkLen = 0
    private var totalSize = 0
    private var completed = false
    private val receivedBlocks = mutableMapOf<Long, ByteArray>() // blockCode -> data
    private val seenHeaders = mutableSetOf<String>()
    private var frameCount = 0

    // LT codes 解码状态
    private var numSourceBlocks = 0
    private var decodedBlocks = mutableMapOf<Int, ByteArray>() // source block index -> data

    private var decodedData: ByteArray? = null

    /**
     * 喂入一帧 QR 码数据。
     * @param frame QR 码的原始字符串内容。
     * @return 解码完成后返回 true。
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

        // 尝试用 LT codes 解码
        tryDecode()

        return completed
    }

    /**
     * LT codes 解码: 迭代 peeling decoder
     */
    private fun tryDecode() {
        if (numSourceBlocks == 0) return

        val remaining = receivedBlocks.toMutableMap()
        var progress = true

        while (progress) {
            progress = false
            val toRemove = mutableListOf<Long>()

            for ((blockCode, data) in remaining) {
                val degree = getDegree(blockCode, numSourceBlocks)
                if (degree == 1) {
                    val sourceIdx = getSourceBlockIndex(blockCode, numSourceBlocks)
                    if (sourceIdx in 0 until numSourceBlocks && !decodedBlocks.containsKey(sourceIdx)) {
                        decodedBlocks[sourceIdx] = data
                        toRemove.add(blockCode)
                        progress = true
                    }
                }
            }

            for (code in toRemove) {
                remaining.remove(code)
            }

            if (progress) {
                val toUpdate = mutableListOf<Pair<Long, ByteArray>>()
                for ((blockCode, data) in remaining) {
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

        // 检查是否所有 source block 都已解码
        if (decodedBlocks.size >= numSourceBlocks) {
            val output = ByteArray(totalSize)
            var offset = 0
            for (i in 0 until numSourceBlocks) {
                val block = decodedBlocks[i] ?: return
                val copyLen = minOf(block.size, totalSize - offset)
                block.copyInto(output, offset, 0, copyLen)
                offset += copyLen
            }

            // 解码 base64 (txqr-gif: file -> base64 -> fountain codes)
            try {
                decodedData = Base64.decode(output, Base64.DEFAULT)
            } catch (e: Exception) {
                decodedData = output
            }
            completed = true
        }
    }

    fun isCompleted(): Boolean = completed
    fun dataBytes(): ByteArray = decodedData ?: ByteArray(0)

    /** 解码进度百分比 (0-100) */
    fun progress(): Int {
        if (completed) return 100
        if (numSourceBlocks == 0) return 0
        val p = decodedBlocks.size * 100 / numSourceBlocks
        return if (p > 99) 99 else p
    }

    /** 原始数据总大小（字节） */
    fun totalSize(): Int = totalSize

    /** 已接收的唯一帧数 */
    fun uniqueFrames(): Int = frameCount

    /** source block 总数 = ceil(总大小 / chunkLen) */
    fun sourceBlocksTotal(): Int = numSourceBlocks

    /** 已解码的 source block 数 */
    fun sourceBlocksDecoded(): Int = decodedBlocks.size

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
     * 根据 blockCode 确定 LT block 的度数（覆盖的 source block 数量）
     * 使用 soliton 分布
     */
    private fun getDegree(blockCode: Long, n: Int): Int {
        if (n <= 0) return 1
        val rng = Random(blockCode * 31 + 17)
        val r = rng.nextDouble()

        var cdf = 0.0
        for (k in 1..n) {
            cdf += solitonPMF(k, n)
            if (r < cdf) return k
        }
        return 1
    }

    /**
     * 获取 LT block 覆盖的 source block 索引列表
     */
    private fun getSourceBlockIndices(blockCode: Long, n: Int): List<Int> {
        val degree = getDegree(blockCode, n)
        val rng = Random(blockCode * 31 + 17)
        rng.nextDouble() // 消耗 degree 随机值

        val indices = mutableSetOf<Int>()
        while (indices.size < degree) {
            indices.add(rng.nextInt(n))
        }
        return indices.toList()
    }

    private fun getSourceBlockIndex(blockCode: Long, n: Int): Int {
        return getSourceBlockIndices(blockCode, n).first()
    }

    private fun solitonPMF(k: Int, n: Int): Double {
        if (k == 1) return 1.0 / n
        return 1.0 / (k * (k - 1))
    }

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
