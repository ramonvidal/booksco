package com.punyplatipus.podouvir.testutil

fun loadFixture(name: String): String =
    requireNotNull(object {}.javaClass.classLoader!!.getResourceAsStream(name)) { "missing fixture $name" }
        .bufferedReader().use { it.readText() }
