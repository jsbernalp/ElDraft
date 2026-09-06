package com.eldraft.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * Sustituye `Dispatchers.Main` por un dispatcher de test.
 *
 * Los ViewModels lanzan su trabajo en `viewModelScope`, que corre sobre
 * `Dispatchers.Main.immediate`. Ese dispatcher no existe en la JVM: sin
 * reemplazarlo, cualquier test que toque un ViewModel falla al arrancar con
 * "Module with the Main dispatcher had failed to initialize".
 *
 * Se usa desde `@BeforeTest` / `@AfterTest` en vez de como regla de JUnit4 para
 * no atar estos tests a un runner concreto:
 *
 * ```
 * private val main = MainDispatcherRule()
 * @BeforeTest fun setUp() = main.start()
 * @AfterTest fun tearDown() = main.stop()
 * ```
 *
 * El dispatcher es `StandardTestDispatcher`: las corrutinas quedan encoladas y
 * no avanzan solas, así que el test controla cuándo corren con
 * `advanceUntilIdle()`. Eso permite comprobar los estados intermedios —el
 * `isLoading = true` antes de que responda el caso de uso, por ejemplo— que con
 * un dispatcher inmediato pasarían desapercibidos.
 */
class MainDispatcherRule(val dispatcher: TestDispatcher = StandardTestDispatcher()) {
    fun start() = Dispatchers.setMain(dispatcher)
    fun stop() = Dispatchers.resetMain()
}
