package org.example.memosm.data

import android.util.Base64
import java.io.InputStream
import java.io.OutputStream
import java.io.Writer

/**
 * Utilities for streaming base64 encoding/decoding without loading
 * entire files into memory. Prevents OOM for large attachments.
 * 
 * IMPORTANT: When streaming base64, we must encode in multiples of 3 bytes
 * to avoid adding padding (=) characters in the middle of the output.
 * Only the final chunk may have padding.
 */
object StreamingBase64 {
    
    // Must be a multiple of 3 to avoid intermediate padding
    // 3072 bytes = 4096 chars of base64 output
    private const val CHUNK_SIZE = 3 * 1024
    
    /**
     * Stream base64 encoding directly to a Writer.
     * Reads input in chunks that are multiples of 3 bytes to ensure
     * proper base64 encoding without intermediate padding.
     * 
     * @param input The input stream to read raw bytes from
     * @param output The writer to write base64-encoded string to
     */
    fun encodeToWriter(input: InputStream, output: Writer) {
        val buffer = ByteArray(CHUNK_SIZE)
        var leftover = ByteArray(0)
        var bytesRead: Int
        
        while (input.read(buffer).also { bytesRead = it } != -1) {
            // Combine leftover from previous iteration with new data
            val combined = leftover + buffer.copyOf(bytesRead)
            
            // Calculate how many complete 3-byte groups we have
            val completeGroups = combined.size / 3
            val bytesToEncode = completeGroups * 3
            
            if (bytesToEncode > 0) {
                val toEncode = combined.copyOf(bytesToEncode)
                val encoded = Base64.encodeToString(toEncode, Base64.NO_WRAP)
                output.write(encoded)
            }
            
            // Save leftover bytes for next iteration
            leftover = combined.copyOfRange(bytesToEncode, combined.size)
        }
        
        // Encode any remaining bytes (final chunk with potential padding)
        if (leftover.isNotEmpty()) {
            val encoded = Base64.encodeToString(leftover, Base64.NO_WRAP)
            output.write(encoded)
        }
    }
    
    /**
     * Stream base64 encoding directly to an OutputStream.
     * 
     * @param input The input stream to read raw bytes from
     * @param output The output stream to write base64-encoded bytes to
     */
    fun encodeToStream(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(CHUNK_SIZE)
        var leftover = ByteArray(0)
        var bytesRead: Int
        
        while (input.read(buffer).also { bytesRead = it } != -1) {
            // Combine leftover from previous iteration with new data
            val combined = leftover + buffer.copyOf(bytesRead)
            
            // Calculate how many complete 3-byte groups we have
            val completeGroups = combined.size / 3
            val bytesToEncode = completeGroups * 3
            
            if (bytesToEncode > 0) {
                val toEncode = combined.copyOf(bytesToEncode)
                val encoded = Base64.encode(toEncode, Base64.NO_WRAP)
                output.write(encoded)
            }
            
            // Save leftover bytes for next iteration
            leftover = combined.copyOfRange(bytesToEncode, combined.size)
        }
        
        // Encode any remaining bytes (final chunk with potential padding)
        if (leftover.isNotEmpty()) {
            val encoded = Base64.encode(leftover, Base64.NO_WRAP)
            output.write(encoded)
        }
    }
    
    /**
     * Stream base64 decoding from InputStream to OutputStream.
     * Reads base64 in chunks that are multiples of 4 characters
     * to ensure proper decoding.
     * 
     * @param input The input stream containing base64-encoded data
     * @param output The output stream to write decoded bytes to
     */
    fun decodeToStream(input: InputStream, output: OutputStream) {
        // 4KB base64 input = ~3KB decoded output
        // Must be multiple of 4 for proper base64 decoding
        val buffer = ByteArray(4 * 1024)
        var leftover = ByteArray(0)
        var bytesRead: Int
        
        while (input.read(buffer).also { bytesRead = it } != -1) {
            // Combine leftover from previous iteration
            val combined = leftover + buffer.copyOf(bytesRead)
            
            // Calculate how many complete 4-byte groups we have
            val completeGroups = combined.size / 4
            val bytesToDecode = completeGroups * 4
            
            if (bytesToDecode > 0) {
                val toDecode = combined.copyOf(bytesToDecode)
                val decoded = Base64.decode(toDecode, Base64.DEFAULT)
                output.write(decoded)
            }
            
            // Save leftover bytes for next iteration
            leftover = combined.copyOfRange(bytesToDecode, combined.size)
        }
        
        // Decode any remaining bytes
        if (leftover.isNotEmpty()) {
            val decoded = Base64.decode(leftover, Base64.DEFAULT)
            output.write(decoded)
        }
    }
    
    /**
     * Calculate the approximate base64 encoded size for a given input size.
     * Base64 encoding increases size by ~33%.
     */
    fun estimateEncodedSize(inputSize: Long): Long {
        return ((inputSize + 2) / 3) * 4
    }
}
