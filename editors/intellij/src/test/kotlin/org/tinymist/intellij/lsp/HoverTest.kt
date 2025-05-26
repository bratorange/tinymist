package org.tinymist.intellij.lsp

import com.intellij.openapi.application.runReadAction
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.junit.Test // If using JUnit5, this would be org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class HoverTest : LspIntegrationTestBase() {

    @Test
    fun `test hover on identifier shows documentation`() {
        // Content where hovering over 'myVar' or a function should provide info
        val content = """
            #let myVar = 123
            // Hover over myVar below
            #myVar 
        """.trimIndent()
        val psiFile = configureTypstFile("hoverTest.typ", content)
        val virtualFile = runReadAction { psiFile.virtualFile }
        val fileUri = virtualFileToURI(virtualFile) // Use helper from base class

        // Position for hover - e.g., on 'myVar' (line 2, char 1, assuming '#myVar')
        val position = Position(2, 2) // Hovering over 'm' in '#myVar' on line 2 (0-indexed)

        val hoverFuture = textDocumentService.hover(HoverParams(
            TextDocumentIdentifier(fileUri), // Use URI
            position
        ))

        assertNotNull("Hover future should not be null", hoverFuture)
        val hoverResult = hoverFuture!!.get(5, TimeUnit.SECONDS) // Adjust timeout

        assertNotNull("Hover result should not be null", hoverResult)
        assertNotNull("Hover contents should not be null", hoverResult!!.contents)
        
        val contents = hoverResult.contents
        assertTrue("Hover contents should be MarkupContent", contents.isRight) // Assuming MarkupContent
        
        val markupContent = contents.right
        assertNotNull("MarkupContent value should not be null", markupContent.value)
        assertTrue("MarkupContent value should not be empty. Got: '${markupContent.value}'", markupContent.value.isNotBlank())

        // Example: Check for expected text in hover. This is highly dependent on tinymist's output.
        // assertTrue(markupContent.value.contains("myVar") || markupContent.value.contains("integer"))
        // assertEquals("markdown", markupContent.kind) // Or "plaintext"
        println("Hover content (identifier): ${markupContent.value}") // For manual inspection during test development
    }

    @Test
    fun `test hover on builtInFunction shows documentation`() {
        val content = "#abs(-1)" 
        val psiFile = configureTypstFile("hoverBuiltIn.typ", content)
        val virtualFile = runReadAction { psiFile.virtualFile }
        val fileUri = virtualFileToURI(virtualFile) // Use helper from base class

        val position = Position(0, 2) // Hovering over 'a' in 'abs' (index 1, after #)

        val hoverFuture = textDocumentService.hover(HoverParams(
            TextDocumentIdentifier(fileUri), // Use URI
            position
        ))

        assertNotNull("Hover future should not be null", hoverFuture)
        val hoverResult = hoverFuture!!.get(5, TimeUnit.SECONDS) 
        assertNotNull("Hover result should not be null", hoverResult)
        
        val contents = hoverResult!!.contents
        assertTrue("Hover contents should be MarkupContent (isRight). Actual: ${if(contents.isLeft) "MarkedString: "+contents.left else "MarkupContent: "+contents.right}", contents.isRight)
        
        val markupContent = contents.right
        assertTrue("MarkupContent value should not be empty for built-in function. Got: '${markupContent.value}'", markupContent.value.isNotBlank())
        // assertTrue(markupContent.value.contains("abs"))
        // assertTrue(markupContent.value.toLowerCase().contains("absolute value"))
        println("Hover content (built-in): ${markupContent.value}")
    }
    
    @Test
    fun `test hover on non-identifier returns empty or null`() {
        val content = "  #let a = 1" // Hover over whitespace
        val psiFile = configureTypstFile("hoverEmpty.typ", content)
        val virtualFile = runReadAction { psiFile.virtualFile }
        val fileUri = virtualFileToURI(virtualFile) // Use helper from base class

        val position = Position(0, 1) // Hovering over whitespace

        val hoverFuture = textDocumentService.hover(HoverParams(
            TextDocumentIdentifier(fileUri), // Use URI
            position
        ))

        assertNotNull("Hover future should not be null", hoverFuture)
        val hoverResult = hoverFuture!!.get(5, TimeUnit.SECONDS) 

        // Servers might return null or a Hover object with empty/null contents
        // for positions where no hover information is available.
        if (hoverResult != null && hoverResult.contents != null) {
            val hoverContents = hoverResult.contents
            if (hoverContents.isLeft) { // MarkedString
                 assertTrue("Expected MarkedString content to be null or blank for whitespace hover, got: '${hoverContents.left.value}'", hoverContents.left.value.isNullOrBlank())
            } else { // MarkupContent
                 assertTrue("Expected MarkupContent to be null or blank for whitespace hover, got: '${hoverContents.right.value}'", hoverContents.right.value.isNullOrBlank())
            }
        } else {
            // This branch is taken if hoverResult is null OR hoverResult.contents is null.
            // Both are acceptable for a hover on whitespace.
            assertTrue(true) 
        }
        println("Hover content (whitespace/non-identifier): ${hoverResult?.contents?.let { if(it.isLeft) it.left.value else it.right.value } ?: "null"}")
    }
}
