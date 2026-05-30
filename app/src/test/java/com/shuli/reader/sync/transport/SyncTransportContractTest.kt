package com.shuli.reader.sync.transport

import org.junit.Assert.assertTrue
import org.junit.Test

// Part of T-14 SyncTransport interface
class SyncTransportContractTest {

    @Test
    fun `SyncTransport interface has all required methods`() {
        val methods = SyncTransport::class.java.declaredMethods.map { it.name }
        assertTrue("Missing read method", methods.contains("read"))
        assertTrue("Missing write method", methods.contains("write"))
        assertTrue("Missing delete method", methods.contains("delete"))
        assertTrue("Missing list method", methods.contains("list"))
        assertTrue("Missing exists method", methods.contains("exists"))
        assertTrue("Missing getMetadata method", methods.contains("getMetadata"))
    }
}
