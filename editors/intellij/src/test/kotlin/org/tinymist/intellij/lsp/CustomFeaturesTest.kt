package org.tinymist.intellij.lsp

import com.intellij.openapi.application.runReadAction
// org.eclipse.lsp4j.jsonrpc.services.JsonNotification is not directly used for method lookup, but good for context
import org.junit.Test
import java.lang.reflect.Method
// java.util.concurrent.CompletableFuture is not used in this specific test.
import kotlin.test.assertNotNull
import kotlin.test.fail


class CustomFeaturesTest : LspIntegrationTestBase() {

    // Inner class to represent parameters for tinymist/document if known
    // For now, using a simple Map as the current client uses Any?
    // data class TinymistDocumentParams(val uri: String, val version: Int /*, other fields */)

    @Test
    fun `test client handles tinymistDocument notification`() {
        val content = "#let a = 1"
        val psiFile = configureTypstFile("customDocTest.typ", content)
        val virtualFile = runReadAction { psiFile.virtualFile } // Ensure file is ready

        // The client's handleDocument method is annotated with @JsonNotification("tinymist/document")
        // We need to simulate the server sending this notification to our client instance.
        // This requires invoking the method on the `client` object obtained in LspIntegrationTestBase.

        // Ideally, lsp4j or lsp4ij would provide a test utility to directly send notifications
        // to the client as if they came from the server.
        // If not, we might need to use reflection to invoke the annotated method,
        // or have a way in LspIntegrationTestBase to get a "server-side" proxy to the client.

        // For now, let's assume we can find the method and invoke it.
        // This is a bit of a workaround and might need refinement if there's a better lsp4ij way.
        
        val methodName = "handleDocument" // Method annotated with @JsonNotification("tinymist/document")
        var handleDocumentMethod: Method? = null
        try {
            // Attempt to get the method. The current TinymistLanguageClient.handleDocument takes Any?
            // So we search for a method with that name and one parameter.
            handleDocumentMethod = client.javaClass.methods.find { 
                it.name == methodName && it.parameterCount == 1 && it.parameterTypes[0] == Object::class.java
            }
            // If it had no parameters:
            // handleDocumentMethod = client.javaClass.getMethod(methodName)
            // If it had a specific parameter type like TinymistDocumentParams:
            // handleDocumentMethod = client.javaClass.getMethod(methodName, TinymistDocumentParams::class.java)

        } catch (e: NoSuchMethodException) { // This exception might not be directly thrown by `find`
            fail("Method $methodName not found on client: ${e.message}")
        }

        assertNotNull("Method $methodName with one Object parameter should exist on TinymistLanguageClient. Found methods: ${client.javaClass.methods.filter { it.name == methodName }.joinToString { it.toGenericString() }}", handleDocumentMethod)

        // Example parameters matching a potential structure for tinymist/document
        // The current client implementation (handleDocument(params: Any?)) is flexible.
        val mockParams = mapOf(
            "uri" to virtualFileToURI(virtualFile), 
            "version" to 1, // Example version
            "content" to content // Example content, if the notification were to include it
        )

        try {
            // Invoke the notification handler.
            // This simulates the lsp4j framework dispatching the notification.
            // Since TinymistLanguageClient.handleDocument takes Any?, passing a Map should work.
            handleDocumentMethod!!.invoke(client, mockParams)
            
            // If the method invocation itself doesn't throw an exception,
            // it means the client received and processed it at a basic level (e.g., logged it).
            // No specific assertion on output here, as it currently just prints to System.err.
            // This test primarily ensures the pathway is open and doesn't crash.
            // Future: check for logged output or side effects if client behavior is enhanced.
            println("Successfully invoked $methodName on client with params: $mockParams")

        } catch (e: Exception) {
            fail("Invoking $methodName on client failed: ${e.message}. Method: ${handleDocumentMethod.toGenericString()}", e)
        }
        
        // To make this test more meaningful, TinymistLanguageClient.handleDocument
        // would need to update some state that can be queried here, or interact
        // with an IntelliJ service whose state change can be asserted.
    }

    @Test
    fun `testClientHandlesTinymistDocumentOutlineNotification`() {
        val content = """
           = Chapter 1
           == Section 1.1
           Content here.
           = Chapter 2
        """.trimIndent()
        val psiFile = configureTypstFile("customOutlineTest.typ", content)
        val virtualFile = runReadAction { psiFile.virtualFile }

        val methodName = "handleDocumentOutline"
        var handleOutlineMethod: Method? = null
        try {
            handleOutlineMethod = client.javaClass.methods.find {
                it.name == methodName && it.parameterCount == 1 && it.parameterTypes[0] == Object::class.java
            }
        } catch (e: NoSuchMethodException) { // Not directly thrown by find, but good practice
            fail("Method $methodName not found on client: ${e.message}")
        }

        assertNotNull("Method $methodName with one Object parameter should exist on TinymistLanguageClient. Found methods: ${client.javaClass.methods.filter { it.name == methodName }.joinToString { it.toGenericString() }}", handleOutlineMethod)

        val mockOutlineParams = mapOf(
            "uri" to virtualFileToURI(virtualFile),
            "outline" to listOf(
                mapOf(
                    "label" to "Chapter 1",
                    "range" to mapOf(
                        "start" to mapOf("line" to 0, "character" to 0),
                        "end" to mapOf("line" to 0, "character" to 10) // Example range
                    ),
                    "children" to listOf(
                         mapOf(
                            "label" to "Section 1.1",
                            "range" to mapOf(
                                "start" to mapOf("line" to 1, "character" to 0),
                                "end" to mapOf("line" to 1, "character" to 13) // Example range
                            )
                        )
                    )
                ),
                mapOf(
                    "label" to "Chapter 2",
                    "range" to mapOf(
                        "start" to mapOf("line" to 3, "character" to 0),
                        "end" to mapOf("line" to 3, "character" to 10) // Example range
                    )
                )
            )
        )

        try {
            handleOutlineMethod!!.invoke(client, mockOutlineParams)
            println("Successfully invoked $methodName on client with params: $mockOutlineParams")
            // Test primarily verifies non-crashing invocation.
        } catch (e: Exception) {
            fail("Invoking $methodName on client failed: ${e.message}. Method: ${handleOutlineMethod.toGenericString()}", e)
        }
    }
}
