package com.pluvist.sachet.next

import org.junit.Assert.*
import org.junit.Test

class TranscriptPdfSearchTest {
    private val pages=listOf(
        TranscriptTextPage(1,"姓名 测试学生  高等数学 A  成绩 92  学分 4"),
        TranscriptTextPage(2,"大学英语  成绩 87  学分 2"),
        TranscriptTextPage(3,"")
    )

    @Test fun keywordsSelectMatchingPdfPagesAndKeepAReadableSnippet() {
        val found=TranscriptPdfSearch.find(pages," 高等数学 92 ")
        assertEquals(listOf(1),found.map{it.number})
        assertTrue(found.single().snippet.contains("高等数学 A"))
    }

    @Test fun emptySearchListsAllPagesAndScannedPagesDoNotMatchText() {
        assertEquals(listOf(1,2,3),TranscriptPdfSearch.find(pages,"").map{it.number})
        assertTrue(TranscriptPdfSearch.find(pages,"不存在").isEmpty())
    }

    @Test fun fullWidthAndCaseInsensitiveQueriesFindTheSamePage() {
        assertEquals(listOf(2),TranscriptPdfSearch.find(pages,"大学英语 ８７").map{it.number})
    }

    @Test fun chineseWordsRemainSearchableWhenPdfSplitsEveryCharacter() {
        val segmented=listOf(TranscriptTextPage(4,"计 算 机 网 络  成绩 ９０"))
        val found=TranscriptPdfSearch.find(segmented,"计算机网络 90")
        assertEquals(listOf(4),found.map{it.number})
        assertTrue(found.single().snippet.contains("计 算 机 网 络"))
    }
}
