package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@Composable
fun QrCodeView(
    content: String,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 200.dp
) {
    val matrixSize = 25
    val matrix = remember(content) {
        generateQrMatrix(content, matrixSize)
    }

    Box(
        modifier = modifier
            .size(sizeDp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .aspectRatio(1f)
        ) {
            val cellWidth = size.width / matrixSize
            val cellHeight = size.height / matrixSize

            for (row in 0 until matrixSize) {
                for (col in 0 until matrixSize) {
                    if (matrix[row][col]) {
                        drawRect(
                            color = Color(0xFF0F172A),
                            topLeft = Offset(col * cellWidth, row * cellHeight),
                            size = Size(cellWidth * 1.02f, cellHeight * 1.02f)
                        )
                    }
                }
            }
        }
    }
}

private fun generateQrMatrix(text: String, size: Int): Array<BooleanArray> {
    val grid = Array(size) { BooleanArray(size) { false } }

    fun drawCornerSquare(startRow: Int, startCol: Int) {
        for (r in 0 until 7) {
            for (c in 0 until 7) {
                val isOuter = r == 0 || r == 6 || c == 0 || c == 6
                val isInner = r in 2..4 && c in 2..4
                grid[startRow + r][startCol + c] = isOuter || isInner
            }
        }
    }

    drawCornerSquare(0, 0)
    drawCornerSquare(0, size - 7)
    drawCornerSquare(size - 7, 0)

    for (i in 8 until size - 8 step 2) {
        grid[6][i] = true
        grid[i][6] = true
    }

    val hash = abs(text.hashCode())
    var currentHash = hash
    for (r in 0 until size) {
        for (c in 0 until size) {
            val inTopLeft = r < 8 && c < 8
            val inTopRight = r < 8 && c >= size - 8
            val inBottomLeft = r >= size - 8 && c < 8
            if (!inTopLeft && !inTopRight && !inBottomLeft) {
                val charVal = if (text.isNotEmpty()) text[(r * size + c) % text.length].code else 42
                val bit = ((currentHash xor (charVal * 31) xor (r * 17 + c * 23)) % 2) == 0
                grid[r][c] = bit
                currentHash = (currentHash * 33 + charVal) and 0x7FFFFFFF
            }
        }
    }

    return grid
}
