package com.voicestudio.mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.voicestudio.mobile.local.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class SupertonicPortugueseTest {

    @Test
    fun supertonicGeneratesBrazilianPortugueseOffline() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("supertonicPtBrSmoke") == "true")

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val catalog = context.assets.open("models.json")
            .bufferedReader().use { ModelCatalog.decode(it.readText()) }

        val model = catalog.models.first { it.id == "supertonic3-int8" }

        assertTrue("Supertonic must declare Portuguese support", "pt" in model.languages)
        assertEquals("supertonic", model.option("model_type"))

        val ram = android.app.ActivityManager.MemoryInfo().also {
            context.getSystemService(android.app.ActivityManager::class.java)
                .getMemoryInfo(it)
        }.totalMem

        assertTrue("Device does not meet the model RAM requirement", model.fits(ram))

        val root = File(context.noBackupFilesDir, "supertonic-ptbr-smoke")
            .apply { mkdirs() }

        val gate = LicenceGate(
            File(root, "licences.json"),
            BuildConfig.VERSION_NAME
        )

        if (model.gated) {
            assertEquals(
                "Review the model terms before accepting them for this test",
                "true",
                args.getString("acceptModelLicencesForSmoke")
            )
            gate.record(model, true)
        }

        val downloads = ModelDownloads(
            File(root, "models"),
            gate,
            ram,
            { path -> context.assets.open(path) }
        ) { true }

        if (args.getString("downloadSmokeModels") == "true") {
            downloads.download(model, false)
        }

        assertTrue(
            "Model must be explicitly downloaded and verified first",
            downloads.installed(model)
        )

        val engine = PhoneLocalEngine(downloads)

        val text = "O que você está prestes a descobrir pode mudar a maneira como enxerga o mundo."

        val audio = engine.synthesize(
            model = model,
            text = text,
            speaker = 5,
            speed = 1.0f,
            language = "pt"
        )

        assertEquals(44100, audio.sampleRate)
        assertTrue("Generated audio is too short", audio.samples.size > audio.sampleRate)
        assertTrue("Audio contains invalid samples", audio.samples.all { it.isFinite() })
        assertTrue(
            "Generated audio is silent",
            audio.samples.any { abs(it) > 0.01f }
        )

        val output = File(root, "supertonic-ptbr-speaker-5.wav")
        markSynthetic(audio, output)

        assertTrue("WAV file was not created", output.isFile && output.length() > 44)
        assertTrue(
            "Synthetic audio marker is missing",
            output.readBytes().toString(Charsets.ISO_8859_1)
                .contains("VoiceStudio synthetic audio")
        )
    }
}
