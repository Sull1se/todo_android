package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskTextTest {

    @Test
    fun merge_putsTitleFirstAndAppendsDetailOnNewLine() {
        assertEquals("买菜\n西红柿\n鸡蛋", TaskText.merge("买菜", "西红柿\n鸡蛋"))
        assertEquals("买菜", TaskText.merge("买菜", ""))
        assertEquals("买菜", TaskText.merge("买菜", "  "))
    }

    @Test
    fun split_firstNonBlankLineIsTitleAndRestIsDetail() {
        assertEquals("买菜" to "西红柿\n\n鸡蛋", TaskText.split("\n  \n买菜\n西红柿\n\n鸡蛋\n\n"))
        assertEquals("单行" to "", TaskText.split("单行"))
        assertEquals("" to "", TaskText.split(" \n\n"))
    }

    @Test
    fun mergeThenSplit_isLosslessForDefaultFormatTasks() {
        val cases = listOf("标题" to "", "标题" to "第一行\n第二行", "标题" to "\n空行开头", "标题" to "中间\n\n空行")
        for ((title, content) in cases) {
            assertEquals(title to content, TaskText.split(TaskText.merge(title, content)))
        }
    }

    @Test
    fun displayLines_skipsBlankLinesAndKeepsTwo() {
        assertEquals("买菜" to "西红柿", TaskText.displayLines("买菜", "\n\n西红柿\n鸡蛋"))
        assertEquals("买菜" to "", TaskText.displayLines("买菜", ""))
    }

    @Test
    fun trimBlankLines_removesOnlyLeadingAndTrailingBlankLines() {
        assertEquals("a\n\nb", TaskText.trimBlankLines("\n \na\n\nb\n\n"))
        assertEquals("", TaskText.trimBlankLines("\n\n"))
    }

    @Test
    fun contentMode_validValues() {
        assertTrue(TaskContentMode.isValid(TaskContentMode.TITLE_AND_DETAIL))
        assertTrue(TaskContentMode.isValid(TaskContentMode.MERGED))
        assertFalse(TaskContentMode.isValid(2))
    }
}
