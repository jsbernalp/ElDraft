package com.eldraft.data.local

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
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
 *
 * Construye el query directamente como `CFMutableDictionaryRef` (en vez de
 * `NSMutableDictionary` + bridging manual): las constantes kSecXxx son
 * CFTypeRef nativos y SecItemXxx las compara por identidad en varios casos
 * (p. ej. kSecReturnData contra el singleton kCFBooleanTrue) — envolverlas en
 * un objeto Foundation "equivalente mirror" recién creado no basta y da
 * errSecParam (-50). Quedarse en CoreFoundation puro evita esa ambigüedad.
 */
@OptIn(ExperimentalForeignApi::class)
private object Keychain {
    private const val SERVICE = "com.eldraft.ios"

    private fun String.toCFString(): CFStringRef =
        CFStringCreateWithCString(kCFAllocatorDefault, this, kCFStringEncodingUTF8)!!

    private fun newQueryDictionary(): CFMutableDictionaryRef = CFDictionaryCreateMutable(
        kCFAllocatorDefault,
        0,
        kCFTypeDictionaryKeyCallBacks.ptr,
        kCFTypeDictionaryValueCallBacks.ptr,
    )!!

    fun get(account: String): String? = memScoped {
        val serviceRef = SERVICE.toCFString()
        val accountRef = account.toCFString()
        val query = newQueryDictionary()
        try {
            CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionarySetValue(query, kSecAttrService, serviceRef)
            CFDictionarySetValue(query, kSecAttrAccount, accountRef)
            CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)

            val result = alloc<CFTypeRefVar>()
            val status: OSStatus = SecItemCopyMatching(query, result.ptr)
            if (status != errSecSuccess) return@memScoped null

            // SecItemCopyMatching sigue la "Create Rule" de CF: el llamador es
            // dueño de la referencia devuelta y debe liberarla.
            val data = result.value as? CFDataRef ?: return@memScoped null
            try {
                val length = CFDataGetLength(data).toInt()
                val bytePtr = CFDataGetBytePtr(data)
                if (length == 0 || bytePtr == null) {
                    ""
                } else {
                    val out = ByteArray(length)
                    out.usePinned { pinned -> memcpy(pinned.addressOf(0), bytePtr, length.toULong()) }
                    out.decodeToString()
                }
            } finally {
                CFRelease(data)
            }
        } finally {
            CFRelease(query)
            CFRelease(serviceRef)
            CFRelease(accountRef)
        }
    }

    fun set(account: String, value: String) {
        delete(account)
        memScoped {
            val serviceRef = SERVICE.toCFString()
            val accountRef = account.toCFString()
            val data = value.toCFData()
            val query = newQueryDictionary()
            try {
                CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
                CFDictionarySetValue(query, kSecAttrService, serviceRef)
                CFDictionarySetValue(query, kSecAttrAccount, accountRef)
                CFDictionarySetValue(query, kSecValueData, data)
                val status = SecItemAdd(query, null)
                // Sin este chequeo, un fallo de Keychain se traga en silencio: el
                // login "funciona" pero la sesión nunca queda guardada, y el
                // síntoma solo aparece más tarde como un currentUserId() nulo
                // inexplicable en otra pantalla.
                check(status == errSecSuccess) { "No se pudo guardar en Keychain (status=$status)" }
            } finally {
                CFRelease(query)
                CFRelease(serviceRef)
                CFRelease(accountRef)
                CFRelease(data)
            }
        }
    }

    fun delete(account: String) {
        val serviceRef = SERVICE.toCFString()
        val accountRef = account.toCFString()
        val query = newQueryDictionary()
        try {
            CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionarySetValue(query, kSecAttrService, serviceRef)
            CFDictionarySetValue(query, kSecAttrAccount, accountRef)
            SecItemDelete(query)
        } finally {
            CFRelease(query)
            CFRelease(serviceRef)
            CFRelease(accountRef)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun String.toCFData(): CFDataRef {
    val bytes = encodeToByteArray()
    return bytes.usePinned { pinned ->
        CFDataCreate(
            kCFAllocatorDefault,
            if (bytes.isEmpty()) null else pinned.addressOf(0).reinterpret(),
            bytes.size.toLong(),
        )!!
    }
}
