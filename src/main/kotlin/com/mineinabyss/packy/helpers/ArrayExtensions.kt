package com.mineinabyss.packy.helpers

fun <T> List<T>.rotatedLeft(offset: Int = +1): List<T> = toMutableList().also { it.rotateLeft(offset) }

private fun <T> MutableList<T>.reverse(fromIndex: Int, toIndex: Int) {
    if (fromIndex < 0 || toIndex > size) {
        throw IndexOutOfBoundsException("fromIndex: $fromIndex, toIndex: $toIndex, size: $size")
    }
    if (fromIndex > toIndex) {
        throw IllegalArgumentException("fromIndex: $fromIndex > toIndex: $toIndex")
    }
    val midPoint = (fromIndex + toIndex) / 2
    if (fromIndex == midPoint) return
    var reverseIndex = toIndex - 1
    for (index in fromIndex until midPoint) {
        val tmp = this[index]
        this[index] = this[reverseIndex]
        this[reverseIndex] = tmp
        reverseIndex--
    }
}
private fun <T> MutableList<T>.rotateLeft(offset: Int = +1) = rotateRight(-offset)
private fun <T> MutableList<T>.rotateRight(offset: Int = +1) = _rotateRight(size, offset) { start, end -> reverse(start, end) }
private inline fun _rotateRight(size: Int, offset: Int, reverse: (start: Int, end: Int) -> Unit) {
    val offset = offset umod size
    if (offset == 0) return
    check(offset in 1 until size)
    reverse(0, size)
    reverse(0, offset)
    reverse(offset, size)
}
private infix fun Int.umod(other: Int): Int {
    val rm = this % other
    val remainder = if (rm == -0) 0 else rm
    return when {
        remainder < 0 -> remainder + other
        else -> remainder
    }
}
