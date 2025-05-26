package org.tinymist.intellij.lsp

import com.intellij.openapi.application.runReadAction
import org.eclipse.lsp4j.*
import org.junit.Test // If using JUnit5, this would be org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class ReferencesTest : LspIntegrationTestBase() {

    @Test
    fun `test references on variable finds all occurrences`() {
        val content = """
            #let myVar = 10
            #let another = myVar
            #let yetAnother = myVar
            // #myVar // This one is a comment
        """.trimIndent()
        val psiFile = configureTypstFile("referencesTest.typ", content)
        val virtualFile = runReadAction { psiFile.virtualFile }
        val fileUri = virtualFileToURI(virtualFile)

        // Position for references request: on 'myVar' in the first line (definition)
        // #let myVar = 10
        //      ^ (char 5)
        val requestPosition = Position(0, 5) 

        val referencesFuture = textDocumentService.references(ReferenceParams(
            TextDocumentIdentifier(fileUri),
            requestPosition,
            ReferenceContext(true) // Include declaration
        ))

        assertNotNull("References future should not be null", referencesFuture)
        val referencesResult = referencesFuture!!.get(5, TimeUnit.SECONDS) 
        assertNotNull("References result should not be null", referencesResult)

        // Depending on server, comments might be ignored or included if they match lexically.
        // Assuming the server is smart enough to find 3 actual code references.
        assertEquals("Should find 3 references (incl. definition)", 3, referencesResult!!.size)

        // Expected ranges for 'myVar'
        // Line 0: #let myVar = 10                     (char 5 to 10)
        // Line 1: #let another = myVar                (char 14 to 19)
        // Line 2: #let yetAnother = myVar            (char 17 to 22)
        val expectedRanges = listOf(
            Range(Position(0, 5), Position(0, 10)), 
            Range(Position(1, 14), Position(1, 19)), 
            Range(Position(2, 17), Position(2, 22))  
        ).sortedBy { it.start.line }


        val actualRanges = referencesResult.map { it.range }.sortedBy { it.start.line }
        
        referencesResult.forEach { assertEquals("Reference URI should match the file URI", fileUri, it.uri) }
        assertEquals("Reference ranges do not match", expectedRanges, actualRanges)
    }

    @Test
    fun `test references on function finds all occurrences`() {
        val content = """
            #let myFunction(p) = p + 1
            #myFunction(10)
            #let x = myFunction(20)
        """.trimIndent()
        val psiFile = configureTypstFile("referencesFuncTest.typ", content)
        val virtualFile = runReadAction { psiFile.virtualFile }
        val fileUri = virtualFileToURI(virtualFile)

        // Position for references request: on 'myFunction' in the first line (definition)
        // #let myFunction(p) = p + 1
        //      ^ (char 5)
        val requestPosition = Position(0, 5) 

        val referencesFuture = textDocumentService.references(ReferenceParams(
            TextDocumentIdentifier(fileUri),
            requestPosition,
            ReferenceContext(true)
        ))
        assertNotNull("References future should not be null", referencesFuture)
        val referencesResult = referencesFuture!!.get(5, TimeUnit.SECONDS)
        assertNotNull("References result should not be null", referencesResult)

        assertEquals("Should find 3 references for myFunction", 3, referencesResult!!.size)

        // Expected ranges for 'myFunction'
        // Line 0: #let myFunction(p) = p + 1         (char 5 to 15)
        // Line 1: #myFunction(10)                    (char 1 to 11)
        // Line 2: #let x = myFunction(20)            (char 10 to 20)
        val expectedRanges = listOf(
            Range(Position(0, 5), Position(0, 15)), 
            Range(Position(1, 1), Position(1, 11)), 
            Range(Position(2, 10), Position(2, 20))  
        ).sortedBy { it.start.line } 

        val actualRanges = referencesResult.map { it.range }.sortedBy { it.start.line }
        referencesResult.forEach { assertEquals("Reference URI should match the file URI", fileUri, it.uri) }
        assertEquals("Reference ranges do not match", expectedRanges, actualRanges)
    }
    
    @Test
    fun `test references on undefined symbol returns empty or null`() {
        val content = "#undefinedVar"
        val psiFile = configureTypstFile("referencesUndefined.typ", content)
        val virtualFile = runReadAction { psiFile.virtualFile }
        val fileUri = virtualFileToURI(virtualFile)

        // Position for references request: on 'undefinedVar'
        // #undefinedVar
        //  ^ (char 1)
        val requestPosition = Position(0, 1)

        val referencesFuture = textDocumentService.references(ReferenceParams(
            TextDocumentIdentifier(fileUri),
            requestPosition,
            ReferenceContext(true)
        ))

        assertNotNull("References future should not be null", referencesFuture)
        val referencesResult = referencesFuture!!.get(5, TimeUnit.SECONDS)
        
        // A server should return null or an empty list if no references are found.
        assertTrue("Expected no references for undefined symbol, got: ${referencesResult?.size ?: "null"}", 
            referencesResult == null || referencesResult.isEmpty())
    }
}
