package com.pluvist.sachet.next

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.text.Normalizer
import java.util.Locale

internal data class TranscriptTextPage(val number:Int,val text:String)
internal data class TranscriptPageMatch(val number:Int,val snippet:String)

/** Extract once after download; searches of the same PDF reuse the in-memory pages. */
internal object TranscriptPdfSearch {
    suspend fun index(context:Context,pdf:File):List<TranscriptTextPage> =
        if(Build.VERSION.SDK_INT>=35)indexPlatform(pdf)else indexPdfBox(context,pdf)

    @android.annotation.TargetApi(35)
    private suspend fun indexPlatform(pdf:File):List<TranscriptTextPage> {
        val pages=ArrayList<TranscriptTextPage>()
        ParcelFileDescriptor.open(pdf,ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                for(index in 0 until renderer.pageCount) {
                    currentCoroutineContext().ensureActive()
                    renderer.openPage(index).use { page ->
                        pages += TranscriptTextPage(index+1,page.textContents.joinToString(" "){it.text})
                    }
                }
            }
        }
        return pages
    }

    private suspend fun indexPdfBox(context:Context,pdf:File):List<TranscriptTextPage> {
        PDFBoxResourceLoader.init(context.applicationContext)
        val pages=ArrayList<TranscriptTextPage>()
        PDDocument.load(pdf).use { document ->
            val extractor=PDFTextStripper().apply { setSortByPosition(true) }
            for(index in 0 until document.numberOfPages) {
                currentCoroutineContext().ensureActive()
                extractor.setStartPage(index+1)
                extractor.setEndPage(index+1)
                pages += TranscriptTextPage(index+1,extractor.getText(document))
            }
        }
        return pages
    }

    private data class SearchProjection(val text:String,val sourceOffsets:IntArray)

    /** PDF text runs can split even one Chinese word into characters separated by spaces. */
    private fun project(source:String):SearchProjection {
        val text=StringBuilder(source.length)
        val offsets=ArrayList<Int>(source.length)
        var sourceOffset=0
        while(sourceOffset<source.length){
            val codePoint=source.codePointAt(sourceOffset)
            val normalized=Normalizer.normalize(String(Character.toChars(codePoint)),Normalizer.Form.NFKC)
                .lowercase(Locale.ROOT)
            normalized.forEach { character ->
                if(!character.isWhitespace()){
                    text.append(character)
                    offsets.add(sourceOffset)
                }
            }
            sourceOffset+=Character.charCount(codePoint)
        }
        return SearchProjection(text.toString(),offsets.toIntArray())
    }

    private fun words(query:String)=Normalizer.normalize(query,Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).trim().split(Regex("\\s+")).filter(String::isNotBlank)

    fun find(pages:List<TranscriptTextPage>,query:String):List<TranscriptPageMatch> {
        val terms=words(query)
        return pages.mapNotNull { page ->
            val projection=project(page.text)
            val hits=terms.map { projection.text.indexOf(it) }
            if(hits.any { it<0 })return@mapNotNull null
            val firstHit=hits.withIndex().minByOrNull { it.value }
            val first=firstHit?.value?:0
            val sourceOffset=projection.sourceOffsets.getOrNull(first)?:0
            val matchEnd=firstHit?.let { hit ->
                projection.sourceOffsets.getOrNull(hit.value+terms[hit.index].length-1)?.let { offset ->
                    offset+Character.charCount(page.text.codePointAt(offset))
                }
            }?:sourceOffset
            val start=(sourceOffset-34).coerceAtLeast(0)
            val end=maxOf(sourceOffset+100,matchEnd).coerceAtMost(page.text.length)
            val excerpt=page.text.substring(start,end).replace(Regex("\\s+")," ").trim()
            val snippet=if(excerpt.isBlank())"本页没有可提取的文字" else
                (if(start>0)"…" else "")+excerpt+(if(end<page.text.length)"…" else "")
            TranscriptPageMatch(page.number,snippet)
        }
    }
}
