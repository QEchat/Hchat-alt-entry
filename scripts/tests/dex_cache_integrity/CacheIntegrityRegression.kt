import android.content.SharedPreferences
import h.Hchat.dexkit.DexMethodCache

class HostFixture { fun first() {}; fun second() {} }

// A synchronous model of the SharedPreferences API used by DexMethodCache.
// Descriptor parsing and method resolution use the actual DexKit dependency.
class Prefs : SharedPreferences {
    val data = mutableMapOf<String, String?>()
    var writes = 0
    var commitSucceeds = true
    override fun getString(key: String, default: String?) = if (data.containsKey(key)) data[key] else default
    override fun edit() = object : SharedPreferences.Editor {
        val values = mutableMapOf<String, String?>()
        var clear = false
        override fun clear() = apply { clear = true }
        override fun putString(key: String, value: String?) = apply { values[key] = value }
        override fun remove(key: String) = apply { values[key] = null }
        override fun apply() { commit() }
        override fun commit(): Boolean {
            writes++
            if (!commitSucceeds) return false
            if (clear) data.clear()
            values.forEach { (k, v) -> if (v == null) data.remove(k) else data[k] = v }
            return true
        }
    }
}

private var checks = 0
private fun verify(condition: Boolean, label: String) {
    checks++
    check(condition) { label }
}

fun main() {
    val loader = HostFixture::class.java.classLoader
    val prefs = Prefs()
    val key = "com.tencent.mm|8.0.76|3141|1|tinker|patch|100|main"
    val child = key.substringBeforeLast('|') + "|child"
    val methods = listOf(HostFixture::class.java.getDeclaredMethod("first"), HostFixture::class.java.getDeclaredMethod("second"))
    fun save() = DexMethodCache.saveList(prefs, key, "entry", methods)
    fun load() = DexMethodCache.loadList(prefs, key, loader, "entry")
    fun cross(runtime: String = child, cl: ClassLoader = loader) = DexMethodCache.loadListCrossProcess(prefs, runtime, cl, "entry")

    save()
    val good = prefs.data["entry"]!!
    val writes = prefs.writes
    verify(load() == methods, "complete cache preserves every method and order")
    verify(prefs.writes == writes, "complete cache does not write")
    prefs.data["other"] = "preserve"
    for (invalid in listOf("LHostFixture;->missing()V", "not-a-descriptor")) {
        for (record in listOf(good + "\n" + invalid, invalid + "\n" + good, invalid)) {
            prefs.data["entry"] = record
            verify(load().isEmpty(), "any invalid member makes the entire list miss")
            verify("entry" !in prefs.data, "only the broken list is removed")
            verify(prefs.data["other"] == "preserve" && prefs.data["cache.key"] == key, "unrelated records and identity survive")
        }
    }
    prefs.data["entry"] = "\n" + good + "\n\n"
    verify(load() == methods, "blank lines remain compatible")
    prefs.data.remove("entry")
    verify(load().isEmpty(), "missing entry misses")
    prefs.data["entry"] = "  \n"
    verify(load().isEmpty(), "blank entry misses")
    save()
    verify(load() == methods, "a rebuilt list becomes reusable")
    val beforeCross = prefs.data.toMap()
    verify(cross() == methods, "different process loader fingerprint can reuse descriptors")
    verify(prefs.data == beforeCross, "cross-process success is read-only")
    val blocked = object : ClassLoader(loader) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (name == "HostFixture") throw ClassNotFoundException(name)
            return super.loadClass(name, resolve)
        }
    }
    verify(cross(cl = blocked).isEmpty(), "child loader resolution failure misses")
    verify(prefs.data == beforeCross, "child failure does not erase main-process cache")
    prefs.data["entry"] = good + "\nLHostFixture;->missing()V"
    val partialCross = prefs.data.toMap()
    verify(cross().isEmpty(), "partial child resolution cannot count as a hit")
    verify(prefs.data == partialCross, "partial child failure is read-only")
    for (index in 0..6) {
        save()
        val changed = key.split('|').toMutableList().also { it[index] += "-changed" }.joinToString("|")
        val original = prefs.data.toMap()
        verify(cross(changed).isEmpty(), "changed runtime component cannot cross process boundaries")
        verify(prefs.data == original, "identity mismatch preserves owner cache")
        verify(DexMethodCache.loadList(prefs, changed, loader, "entry").isEmpty(), "changed runtime invalidates local descriptors")
        verify(prefs.data == mapOf("cache.key" to changed), "old runtime records are cleared")
    }
    save()
    verify(DexMethodCache.loadList(prefs, child, loader, "entry").isEmpty(), "same-process API invalidates changed ClassLoader")
    save()
    val beforeBlank = prefs.data.toMap()
    verify(DexMethodCache.loadList(prefs, "", loader, "entry").isEmpty(), "blank runtime cannot hit")
    verify(cross("").isEmpty(), "blank child runtime cannot hit")
    verify(prefs.data == beforeBlank, "blank runtime does not erase known cache")
    prefs.commitSucceeds = false
    verify(DexMethodCache.loadList(prefs, child, loader, "entry").isEmpty(), "failed identity persistence cannot reuse old cache")
    verify(prefs.data == beforeBlank, "failed commit leaves old identity intact in this model")
    println("CacheIntegrityRegression: " + checks + " checks passed (real DexKit 2.0.1)")
}
