package com.malik.lmai.build.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.malik.lmai.build.engine.compiler.JavacCompiler
import com.malik.lmai.build.engine.dex.D8DexConverter
import com.malik.lmai.build.engine.resource.Aapt2ResourceCompiler
import com.malik.lmai.build.engine.sign.DebugApkSigner

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.assertNotNull

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun buildComponents_canBeConstructed() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertNotNull(JavacCompiler(appContext))
        assertNotNull(Aapt2ResourceCompiler(appContext))
        assertNotNull(D8DexConverter(appContext))
        assertNotNull(DebugApkSigner(appContext))
    }
}
