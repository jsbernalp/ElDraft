package com.eldraft.data.local

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFRetain
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.darwin.OSStatus
import platform.posix.memcpy

/**
 * Guarda un solo par (token, userId) en el Keychain, bajo el mismo "servicio".
 * Equivale al [SessionStore] de DataStore de Android: el JWT no debe ir a
 * NSUserDefaults (no está cifrado).
 */
@OptIn(ExperimentalForeignApi::class)
class IosSessionStore : SessionStore {

    private object Account {
        const val TOKEN = "com.eldraft.ios.session.token"
        const val USER_ID = "com.eldraft.ios.session.userId"
    }

    override suspend fun currentToken(): String? = Keychain.get(Account.TOKEN)

    override suspend fun currentUserId(): String? = Keychain.get(Account.USER_ID)

    override suspend fun save(token: String, userId: String) {
        Keychain.set(Account.TOKEN, token)
        Keychain.set(Account.USER_ID, userId)
    }

    override suspend fun clear() {
        Keychain.delete(Account.TOKEN)
        Keychain.delete(Account.USER_ID)
    }
}

/**
 * Envoltorio mínimo sobre la API de Keychain (Security.framework) para leer,
 * guardar y borrar un string por cuenta. Todos los ítems comparten servicio
 * [SERVICE]; la cuenta ("account") es la clave lógica (token / userId).
 */
@OptIn(ExperimentalForeignApi::class)
private object Keychain {
    private const val SERVICE = "com.eldraft.ios"

    private fun query(vararg pairs: Pair<CFStringRef?, Any?>): NSMutableDictionary {
        val dict = NSMutableDictionary()
        pairs.forEach { (key, value) ->
            if (key != null && value != null) dict.setObject(value, forKey = key.asNSString())
        }
        return dict
    }

    /**
     * SecItemXxx recibe el query como CFDictionaryRef, pero lo construimos como
     * NSMutableDictionary (mucho más cómodo desde Kotlin). CFBridgingRetain +
     * CFRelease es el "préstamo" correcto: le da a la llamada C una referencia
     * CF válida sin que ARC pierda la suya, y la libera apenas termina la
     * llamada (Security no retiene el diccionario más allá de leerlo).
     */
    private inline fun <T> NSMutableDictionary.asCFDictionary(block: (CFDictionaryRef?) -> T): T {
        val cfDict = CFBridgingRetain(this) as CFDictionaryRef?
        try {
            return block(cfDict)
        } finally {
            cfDict?.let { CFRelease(it) }
        }
    }

    fun get(account: String): String? = memScoped {
        query(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to SERVICE,
            kSecAttrAccount to account,
            kSecReturnData to true,
            kSecMatchLimit to kSecMatchLimitOne,
        ).asCFDictionary { q ->
            val result = alloc<CFTypeRefVar>()
            val status: OSStatus = SecItemCopyMatching(q, result.ptr)
            if (status != errSecSuccess) return@memScoped null
            // SecItemCopyMatching sigue la "Create Rule" de CF: el llamador es
            // dueño de la referencia devuelta. CFBridgingRelease le entrega esa
            // propiedad a ARC, que es lo que hace falta para tratarla como
            // NSData de Kotlin.
            val data = result.value?.let { CFBridgingRelease(it) } as? NSData ?: return@memScoped null
            data.toKString()
        }
    }

    fun set(account: String, value: String) {
        delete(account)
        val data = value.toNSData()
        query(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to SERVICE,
            kSecAttrAccount to account,
            kSecValueData to data,
        ).asCFDictionary { q -> SecItemAdd(q, null) }
    }

    fun delete(account: String) {
        query(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to SERVICE,
            kSecAttrAccount to account,
        ).asCFDictionary { q -> SecItemDelete(q) }
    }
}

// Conversión String <-> NSData sobre los bytes crudos (UTF-8), sin pasar por
// NSString: evita la ambigüedad de selector de dataUsingEncoding(_:) entre
// Kotlin/Native y el header de Foundation.
@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
private fun String.toNSData(): NSData {
    val bytes = encodeToByteArray()
    return bytes.usePinned { pinned ->
        NSData.create(bytes = if (bytes.isEmpty()) null else pinned.addressOf(0), length = bytes.size.toULong())
    }
}

// Las constantes kSecXxx llegan como CFStringRef (un CPointer crudo): Kotlin no
// las reconoce como el mismo objeto que su NSString toll-free-bridged, así que
// `as NSString` compila pero falla en tiempo de ejecución (ClassCastException).
// CFRetain + CFBridgingRelease es el patrón correcto para "tomar prestada" una
// referencia CF que no es nuestra y envolverla en un NSString gestionado por
// ARC sin alterar el conteo de referencias original.
@OptIn(ExperimentalForeignApi::class)
private fun CFStringRef.asNSString(): NSString = CFBridgingRelease(CFRetain(this)) as NSString

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toKString(): String {
    val size = length.toInt()
    if (size == 0) return ""
    val out = ByteArray(size)
    out.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, size.toULong()) }
    return out.decodeToString()
}
