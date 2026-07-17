package ru.aspid.nightmaster.data.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelFilenameMetadataTest {
    @Test
    fun detectsCommonFamilies() {
        assertEquals("Qwen", ModelFilenameMetadata.family("Qwen3-4B-Q4_K_M.gguf"))
        assertEquals("Llama", ModelFilenameMetadata.family("Meta-Llama-3.1-8B-Q5_K_M.gguf"))
        assertEquals("Mistral", ModelFilenameMetadata.family("mistral-small-Q4_K_S.gguf"))
        assertEquals("Gemma", ModelFilenameMetadata.family("gemma-3-4b-it-Q8_0.gguf"))
        assertEquals("DeepSeek", ModelFilenameMetadata.family("DeepSeek-R1-Distill-Qwen-Q4_K_M.gguf"))
        assertEquals("Phi", ModelFilenameMetadata.family("Phi-4-mini-Q6_K.gguf"))
    }

    @Test
    fun detectsQuantization() {
        assertEquals("Q4_K_M", ModelFilenameMetadata.quantization("Qwen3-4B-Q4_K_M.gguf"))
        assertEquals("Q8_0", ModelFilenameMetadata.quantization("gemma-3-4b-it.Q8_0.gguf"))
        assertEquals("Q6_K", ModelFilenameMetadata.quantization("Phi-4-mini_q6_k.gguf"))
    }

    @Test
    fun unknownMetadataReturnsNull() {
        assertNull(ModelFilenameMetadata.family("custom-model.gguf"))
        assertNull(ModelFilenameMetadata.quantization("custom-model.gguf"))
    }
}
