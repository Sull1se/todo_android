package com.example.data

/** 清单的待办格式。 */
object TaskContentMode {
    /** 默认：标题 + 详细信息两个输入框。 */
    const val TITLE_AND_DETAIL = 0

    /** 合并为一个可自由换行的“信息”。存储仍拆为首行（title）与其余行（content）。 */
    const val MERGED = 1

    fun isValid(mode: Int): Boolean = mode == TITLE_AND_DETAIL || mode == MERGED
}

/**
 * 合并格式的文字换算。合并格式下 `title` 保存“信息”的首个非空行，`content` 保存其后各行，
 * 因此两种格式互相切换时无需改写任务，只换编辑界面与列表显示。
 */
object TaskText {
    /** 标题占首行；详细信息非空时换行接在后面。 */
    fun merge(title: String, content: String): String =
        if (content.isBlank()) title else "$title\n$content"

    /**
     * 拆分“信息”：开头的空行跳过，首个非空行为标题，其后各行原样保留为详细信息（去掉末尾空行）。
     * 全为空时返回两个空串。
     */
    fun split(info: String): Pair<String, String> {
        val lines = info.lines()
        val first = lines.indexOfFirst { it.isNotBlank() }
        if (first < 0) return "" to ""
        val rest = lines.subList(first + 1, lines.size).dropLastWhile { it.isBlank() }
        return lines[first] to rest.joinToString("\n")
    }

    /** 合并格式在列表中显示的两行：跳过空行后的前两行，第二行可能为空串。 */
    fun displayLines(title: String, content: String): Pair<String, String> {
        val lines = merge(title, content).lines().filter { it.isNotBlank() }
        return lines.getOrElse(0) { "" } to lines.getOrElse(1) { "" }
    }

    /** 切回默认格式时整理详细信息：去掉首尾空行，中间内容不变。 */
    fun trimBlankLines(content: String): String =
        content.lines().dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }.joinToString("\n")
}
