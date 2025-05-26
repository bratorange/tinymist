package org.tinymist.intellij.lsp

import com.intellij.openapi.application.runReadAction
import org.eclipse.lsp4j.*
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.fail

class FormattingTest : LspIntegrationTestBase() {

    @Test
    fun `test formatting whole document`() {
        // Example: Unformatted Typst content
        val unformattedContent = """#let myVar=10
#let myFunction(p)=p*2
myFunction(myVar)""" // No trailing newline here
        // Example: How the above *might* be formatted (depends on server's rules)
        val expectedFormattedContent = """#let myVar = 10
#let myFunction(p) = p * 2
myFunction(myVar)
""" 
        // Note: Expected content might have a trailing newline if server adds one.

        val psiFile = configureTypstFile("formattingTest.typ", unformattedContent)
        val virtualFile = runReadAction { psiFile.virtualFile }
        val fileUri = virtualFileToURI(virtualFile)

        val formattingParams = DocumentFormattingParams(
            TextDocumentIdentifier(fileUri),
            FormattingOptions(
                4, // tabSize
                true // insertSpaces
                // Other options like trimTrailingWhitespace, insertFinalNewline can be added
                // if the server is known to support them or if we want to test defaults.
            )
        )

        val formattingFuture = textDocumentService.formatting(formattingParams)
        assertNotNull("Formatting future should not be null", formattingFuture)
        
        val textEdits = formattingFuture!!.get(5, TimeUnit.SECONDS) // Adjust timeout
        
        // If the server does not support formatting, it might return null or an empty list.
        if (textEdits == null || textEdits.isEmpty()) {
            // This could be a valid scenario if the document is already formatted
            // or if the server doesn't support formatting.
            // For this test, assume some formatting change is expected or server supports it.
            // If server explicitly states no formatting support, this test should be adjusted/removed.
            println("No text edits received for formatting. Server might not support formatting or doc is formatted.")
            // To make this a stronger assertion if formatting is expected:
            // fail("Expected text edits for formatting, but got none.")
            // For now, let's assume it might return empty if no changes needed.
            // If unformattedContent and expectedFormattedContent are different, then edits should not be empty.
            if (unformattedContent.replace("\r\n", "\n") != expectedFormattedContent.replace("\r\n", "\n")) {
                 fail("Expected formatting changes, but received no edits. Unformatted and expected content differ.")
            }
            return // Exit test if no edits and content was same or server doesn't support.
        }

        assertFalse("Expected text edits for formatting, but got an empty list.", textEdits.isEmpty() && unformattedContent.replace("\r\n", "\n") != expectedFormattedContent.replace("\r\n", "\n"))

        // Apply text edits to the original content to see the result
        // Note: LSP4J's TextEdit application logic might be complex for multiple edits.
        // For a single full-document formatting edit, it's usually one replace edit.
        
        var formattedContentResult = unformattedContent
        // Sort edits in reverse order of application (by start position, then end position)
        // to avoid range shifts for subsequent edits.
        // For full document format, usually one edit, but good practice for multiple edits.
        val sortedEdits = textEdits.sortedWith(compareByDescending<TextEdit> { it.range.start.line }
            .thenByDescending { it.range.start.character }
            .thenByDescending { it.range.end.line }
            .thenByDescending { it.range.end.character })

        for (edit in sortedEdits) {
            formattedContentResult = applyEdit(formattedContentResult, edit)
        }
        
        assertEquals(
            expectedFormattedContent.replace("\r\n", "\n"), 
            formattedContentResult.replace("\r\n", "\n"),
            "Formatted content does not match expected"
        )
    }

    // Helper function to apply a single TextEdit.
    // A more robust version would handle multi-line ranges and character offsets correctly.
    // This is a simplified version. IntelliJ or lsp4j might have utilities for this.
    private fun applyEdit(original: String, edit: TextEdit): String {
        val startOffset = getOffset(original, edit.range.start)
        val endOffset = getOffset(original, edit.range.end)
        // Ensure offsets are within bounds
        val safeStartOffset = startOffset.coerceIn(0, original.length)
        val safeEndOffset = endOffset.coerceIn(safeStartOffset, original.length)

        return original.substring(0, safeStartOffset) + edit.newText + original.substring(safeEndOffset)
    }

    private fun getOffset(text: String, position: Position): Int {
        var offset = 0
        // Normalize line endings in the text being processed for offset calculation
        val normalizedText = text.replace("\r\n", "\n")
        val lines = normalizedText.lines()
        
        if (position.line >= lines.size && lines.isEmpty() && position.line == 0) { // Empty text, position at (0,0)
             return 0
        }
        if (position.line >= lines.size) {
            // Position line is out of bounds, could point to the end of the text or be an error
            // For LSP, a position beyond the last line often implies appending.
            // Let's cap it at text length for safety in this helper.
            return normalizedText.length
        }

        for (i in 0 until position.line) {
            offset += lines[i].length + 1 // +1 for newline character (always \n due to normalization)
        }
        
        // Ensure character offset is within the line's bounds
        val lineLength = lines[position.line].length
        val charOffset = position.character.coerceIn(0, lineLength)
        offset += charOffset
        
        // Ensure total offset is within the text's bounds
        return offset.coerceIn(0, normalizedText.length)
    }
}
