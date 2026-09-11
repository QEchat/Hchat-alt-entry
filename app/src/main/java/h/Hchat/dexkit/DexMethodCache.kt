package h.Hchat.dexkit

import android.content.Context
import android.content.SharedPreferences
import h.Hchat.hooks.api.runtime.WeChatVersionApi
import h.Hchat.preferences.HchatStorage
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Constructor
import java.lang.reflect.Method

object DexMethodCache {
    private const val CACHE_KEY = "cache.key"

    fun prefs(context: Context, name: String): SharedPreferences {
        return HchatStorage.preferences(context, name)
    }

    fun runtimeKey(context: Context, classLoader: ClassLoader): String {
        return WeChatVersionApi.buildCacheKey(context, classLoader)
    }

    fun load(
        prefs: SharedPreferences,
        runtimeKey: String,
        classLoader: ClassLoader,
        name: String
    ): Method? {
        if (runtimeKey.isBlank()) return null
        if (!ensureRuntimeKey(prefs, runtimeKey)) return null
        val descriptor = prefs.getString(name, "")?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { DexMethod(descriptor).getMethodInstance(classLoader) }.getOrNull()
    }

    /**
     * Reuses a descriptor located in another process of the same WeChat runtime.
     * The final cache-key segment is the process ClassLoader fingerprint; every
     * package, version, APK and Tinker segment before it must still match. The
     * descriptor is resolved again against the current process ClassLoader.
     */
    fun loadCrossProcess(
        prefs: SharedPreferences,
        runtimeKey: String,
        classLoader: ClassLoader,
        name: String
    ): Method? {
        if (runtimeKey.isBlank()) return null
        val cachedRuntimeKey = prefs.getString(CACHE_KEY, "").orEmpty()
        if (!sameRuntimeAcrossProcesses(cachedRuntimeKey, runtimeKey)) return null
        val descriptor = prefs.getString(name, "")?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { DexMethod(descriptor).getMethodInstance(classLoader) }.getOrNull()
    }

    fun save(
        prefs: SharedPreferences,
        runtimeKey: String,
        name: String,
        method: Method?
    ) {
        if (runtimeKey.isBlank() || method == null) return
        runCatching {
            val editor = prefs.edit()
            if (prefs.getString(CACHE_KEY, "") != runtimeKey) {
                editor.clear()
            }
            editor
                .putString(CACHE_KEY, runtimeKey)
                .putString(name, method.toDexDescriptor())
                .apply()
        }
    }

    fun loadConstructor(
        prefs: SharedPreferences,
        runtimeKey: String,
        classLoader: ClassLoader,
        name: String
    ): Constructor<*>? {
        if (runtimeKey.isBlank()) return null
        if (!ensureRuntimeKey(prefs, runtimeKey)) return null
        val descriptor = prefs.getString(name, "")?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { DexMethod(descriptor).getConstructorInstance(classLoader) }.getOrNull()
    }

    fun saveConstructor(
        prefs: SharedPreferences,
        runtimeKey: String,
        name: String,
        constructor: Constructor<*>?
    ) {
        if (runtimeKey.isBlank() || constructor == null) return
        runCatching {
            val editor = prefs.edit()
            if (prefs.getString(CACHE_KEY, "") != runtimeKey) {
                editor.clear()
            }
            editor
                .putString(CACHE_KEY, runtimeKey)
                .putString(name, constructor.toDexDescriptor())
                .apply()
        }
    }

    fun loadList(
        prefs: SharedPreferences,
        runtimeKey: String,
        classLoader: ClassLoader,
        name: String
    ): List<Method> {
        if (runtimeKey.isBlank()) return emptyList()
        if (!ensureRuntimeKey(prefs, runtimeKey)) return emptyList()
        val descriptors = prefs.getString(name, "")?.takeIf { it.isNotBlank() } ?: return emptyList()
        val methods = resolveMethodList(descriptors, classLoader)
        if (methods == null) {
            // A partially resolved list cannot represent all parallel host entries.
            // Only evict this record, and do not erase a newer runtime's cache.
            if (prefs.getString(CACHE_KEY, "") == runtimeKey &&
                prefs.getString(name, "") == descriptors
            ) {
                prefs.edit().remove(name).apply()
            }
        }
        return methods.orEmpty()
    }

    fun loadListCrossProcess(
        prefs: SharedPreferences,
        runtimeKey: String,
        classLoader: ClassLoader,
        name: String
    ): List<Method> {
        if (runtimeKey.isBlank()) return emptyList()
        val cachedRuntimeKey = prefs.getString(CACHE_KEY, "").orEmpty()
        if (!sameRuntimeAcrossProcesses(cachedRuntimeKey, runtimeKey)) return emptyList()
        val descriptors = prefs.getString(name, "")?.takeIf { it.isNotBlank() } ?: return emptyList()
        // Child processes resolve the whole list against their own ClassLoader,
        // but must not remove descriptors that remain valid in the main process.
        return resolveMethodList(descriptors, classLoader).orEmpty()
    }

    private fun resolveMethodList(descriptors: String, classLoader: ClassLoader): List<Method>? {
        return runCatching {
            descriptors.split('\n')
                .filter { it.isNotBlank() }
                .map { DexMethod(it).getMethodInstance(classLoader) }
        }.getOrNull()
    }

    fun saveList(
        prefs: SharedPreferences,
        runtimeKey: String,
        name: String,
        methods: List<Method>
    ) {
        if (runtimeKey.isBlank() || methods.isEmpty()) return
        runCatching {
            val editor = prefs.edit()
            if (prefs.getString(CACHE_KEY, "") != runtimeKey) {
                editor.clear()
            }
            editor
                .putString(CACHE_KEY, runtimeKey)
                .putString(name, methods.distinctBy { it.toGenericString() }.joinToString("\n") { it.toDexDescriptor() })
                .apply()
        }
    }

    fun clear(
        prefs: SharedPreferences,
        runtimeKey: String,
        name: String
    ) {
        runCatching {
            val editor = prefs.edit()
            if (prefs.getString(CACHE_KEY, "") != runtimeKey) {
                editor.clear().putString(CACHE_KEY, runtimeKey)
            }
            editor.remove(name).apply()
        }
    }

    private fun Method.toDexDescriptor(): String {
        return buildString {
            append('L')
            append(declaringClass.name.replace('.', '/'))
            append(";->")
            append(name)
            append('(')
            parameterTypes.forEach { append(it.toDexType()) }
            append(')')
            append(returnType.toDexType())
        }
    }

    private fun Constructor<*>.toDexDescriptor(): String {
        return buildString {
            append('L')
            append(declaringClass.name.replace('.', '/'))
            append(";-><init>(")
            parameterTypes.forEach { append(it.toDexType()) }
            append(")V")
        }
    }

    private fun Class<*>.toDexType(): String {
        if (isPrimitive) {
            return when (this) {
                java.lang.Void.TYPE -> "V"
                java.lang.Boolean.TYPE -> "Z"
                java.lang.Byte.TYPE -> "B"
                java.lang.Character.TYPE -> "C"
                java.lang.Short.TYPE -> "S"
                java.lang.Integer.TYPE -> "I"
                java.lang.Long.TYPE -> "J"
                java.lang.Float.TYPE -> "F"
                java.lang.Double.TYPE -> "D"
                else -> "V"
            }
        }
        if (isArray) return name.replace('.', '/')
        return "L${name.replace('.', '/')};"
    }

    private fun ensureRuntimeKey(prefs: SharedPreferences, runtimeKey: String): Boolean {
        if (runtimeKey.isBlank()) return false
        if (prefs.getString(CACHE_KEY, "") == runtimeKey) return true
        val updated = runCatching {
            prefs.edit()
                .clear()
                .putString(CACHE_KEY, runtimeKey)
                .commit()
        }.getOrDefault(false)
        return updated && prefs.getString(CACHE_KEY, "") == runtimeKey
    }

    private fun sameRuntimeAcrossProcesses(cachedRuntimeKey: String, runtimeKey: String): Boolean {
        if (cachedRuntimeKey.isBlank() || runtimeKey.isBlank()) return false
        if (cachedRuntimeKey == runtimeKey) return true
        val cachedIdentity = cachedRuntimeKey.substringBeforeLast('|', "")
        val runtimeIdentity = runtimeKey.substringBeforeLast('|', "")
        return cachedIdentity.isNotBlank() && cachedIdentity == runtimeIdentity
    }
}
