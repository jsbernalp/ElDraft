package com.eldraft.domain.convocatory

import com.eldraft.data.models.MapEvent
import com.eldraft.domain.usecase.convocatory.ObserveMapEventsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Handle cancelable devuelto por [MapEventsSubscription.subscribe]. */
fun interface Cancellable {
    fun cancel()
}

/**
 * `Flow<MapEvent>` no se expone bien a Swift desde Kotlin/Native. Este wrapper
 * lo consume en un [CoroutineScope] propio (en el hilo principal, porque
 * alimenta la UI de SwiftUI) y lo reexpone como `subscribe(onEach:onError:)`
 * con callbacks planos — lo único que Swift necesita entender.
 */
class MapEventsSubscription(
    private val observeMapEvents: ObserveMapEventsUseCase,
    private val lat: Double,
    private val lng: Double,
    private val radius: Double = 5000.0,
    private val userId: String? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun subscribe(onEach: (MapEvent) -> Unit, onError: (Throwable) -> Unit): Cancellable {
        val job = scope.launch {
            observeMapEvents(lat, lng, radius, userId)
                .catch { error -> onError(error) }
                .collect { event -> onEach(event) }
        }
        return Cancellable { job.cancel() }
    }
}
