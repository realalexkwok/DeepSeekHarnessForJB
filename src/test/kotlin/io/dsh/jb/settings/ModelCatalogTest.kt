package io.dsh.jb.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Roadmap item 6/10: /models parsing and fallback (pure). */
class ModelCatalogTest {

    @Test
    fun `parses an openai shaped models response`() {
        val models = ModelCatalog.parseModels(
            """{"object":"list","data":[{"id":"deepseek-v4-flash"},{"id":"deepseek-v4-pro"}]}""",
        )
        assertEquals(2, models.size)
        assertEquals("deepseek-v4-flash", models[0].id)
        assertEquals("DeepSeek-V4-Flash", models[0].name)
        assertEquals("deepseek-v4-pro", models[1].id)
    }

    @Test
    fun `unknown ids keep the raw id as display name`() {
        val models = ModelCatalog.parseModels("""{"data":[{"id":"deepseek-v5"}]}""")
        assertEquals("deepseek-v5", models.single().name)
    }

    @Test
    fun `malformed json yields an empty list`() {
        assertEquals(emptyList<ModelInfo>(), ModelCatalog.parseModels("not json"))
        assertEquals(emptyList<ModelInfo>(), ModelCatalog.parseModels("""{"nope":1}"""))
    }

    @Test
    fun `merge keeps known names and dedupes by id`() {
        val merged = ModelCatalog.merge(ModelCatalog.KNOWN, listOf(ModelInfo("deepseek-v4-pro", "x"), ModelInfo("deepseek-v5", "deepseek-v5")))
        assertEquals(4, merged.size)
        assertEquals("DeepSeek-V4-Pro", merged.first { it.id == "deepseek-v4-pro" }.name)
        assertTrue(merged.any { it.id == "deepseek-v5" })
    }

    @Test
    fun `default model is the sdk default`() {
        assertEquals("deepseek-v4-flash", ModelCatalog.DEFAULT_MODEL)
    }
}
