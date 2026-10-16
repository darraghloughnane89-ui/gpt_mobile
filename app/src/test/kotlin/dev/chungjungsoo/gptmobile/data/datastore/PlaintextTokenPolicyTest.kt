package dev.chungjungsoo.gptmobile.data.datastore

import dev.chungjungsoo.gptmobile.data.model.ApiType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PlaintextTokenPolicyTest {
    @Test
    fun legacyTokenKeys_coverEveryLegacyApiType() {
        assertEquals(ApiType.entries.toSet(), LegacyTokenKeys.preferenceNames.keys)
    }

    @Test
    fun settingDataSource_hasNoPlaintextTokenWriter() {
        val methodNames = SettingDataSource::class.java.methods.map { it.name }
        assertFalse(methodNames.contains("updateToken"))
    }

    @Test
    fun settingDataSourceImpl_doesNotAssignTokenPreferences() {
        val source = listOf(
            File("src/main/kotlin/dev/chungjungsoo/gptmobile/data/datastore/SettingDataSourceImpl.kt"),
            File("app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/datastore/SettingDataSourceImpl.kt")
        ).first { it.exists() }.readText()
        LegacyTokenKeys.preferenceNames.values.forEach { name ->
            assertFalse(source.contains("\"$name\""))
        }
        assertFalse(source.contains("fun updateToken"))
    }
}
