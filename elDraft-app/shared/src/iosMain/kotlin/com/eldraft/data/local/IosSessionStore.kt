package com.eldraft.data.local

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRefVar
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

    private fun query(vararg pairs: Pair<CFStringRef?, Any?>): CFDictionaryRef? {
        val dict = NSMutableDictionary()
        pairs.forEach { (key, value) ->
            if (key != null && value != null) dict.setObject(value, forKey = key as NSString)
        }
        return dict as CFDictionaryRef?
    }

    fun get(account: String): String? = memScoped {
        val q = query(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to SERVICE,
            kSecAttrAccount to account,
            kSecReturnData to true,
            kSecMatchLimit to kSecMatchLimitOne,
        )
        val result = alloc<CFTypeRefVar>()
        val status: OSStatus = SecItemCopyMatching(q, result.ptr)
        if (status != errSecSuccess) return@memScoped null
        val data = result.value as? NSData ?: return@memScoped null
        data.toKString()
    }

    fun set(account: String, value: String) {
        delete(account)
        val data = value.toNSData()
        val q = query(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to SERVICE,
            kSecAttrAccount to account,
            kSecValueData to data,
        )
        SecItemAdd(q, null)
    }

    fun delete(account: String) {
        val q = query(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to SERVICE,
            kSecAttrAccount to account,
        )
        SecItemDelete(q)
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

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toKString(): String {
    val size = length.toInt()
    if (size == 0) return ""
    val out = ByteArray(size)
    out.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, size.toULong()) }
    return out.decodeToString()
}
