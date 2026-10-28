# Etap 2B — odtwarzalne próby JVM bez APK

Baza aplikacji: `b7d62f79f285ac81c29a79b326529d42e9b9fde4`. Badano niezmienione pliki Kotlin aplikacji, kompilatorem Kotlin 2.4.0 i bibliotekami już obecnymi w dystrybucji Gradle 9.7.1. Nie uruchomiono Gradle, Androida ani GeckoView. Kompilator projektu ma wersję 2.4.10; próby nie zastępują testów pełnej aplikacji.

Zastąpiono zależności: Android/SharedPreferences, AddonManager, dispatcher Main i Compose SnackbarHostState. Zawieszenie pierwszego getAddons jest kontrolowanym harmonogramem, nie pomiarem prawdopodobieństwa wyścigu. TabGroupData wycięto z oryginalnej deklaracji. Nie przepisano badanych funkcji do innego języka.

Każdy program sprawdza przez `check`, że usterka została odtworzona, dlatego kończy się **kodem 0 przy udanej reprodukcji**. Napis FAIL oznacza naruszenie badanego wymagania. Wynik nie jest PASS poprawności aplikacji. Kontrole PASS sprawdzają przygotowanie scenariusza. Pierwsza kompilacja pomocniczego SnackbarProbe wymagała uzupełnienia obowiązkowego argumentu onConfirm; poniżej jest skorygowany, rzeczywiście wykonany wariant. Nie zmieniano aplikacji.

## Zaobserwowane wyniki

### ZC-AUD-003 — starsza polityka profilu

```text
PASS control: current profile B enables the extension
FAIL current-profile policy invariant: active=B expectedEnabled=true actualEnabled=false operations=[enable, disable] (stale A reproduced)
```

### ZC-AUD-004 — zamknięte identyfikatory

```text
PASS control: partial restore retains its pending slot
FAIL final-close cleanup: live=1 orderSlots=501 retainedClosed=500 (reproduced)
OBSERVED group snapshot: missing member retained; Undo/restore distinction requires lifecycle evidence
```

### ZC-AUD-005 — anulowany scope komunikatu Cofnij

```text
PASS control: live chrome scope reaches snackbar host
FAIL Undo UI dispatch invariant: chromeActive=true retainedScopeActive=false newHostCalls=0 (reproduced)
```

## Odtworzenie

Blok Python uruchamia się z katalogu głównego repozytorium po wyodrębnieniu go do pliku tymczasowego. Czyta fixture z tego dokumentu; zapisuje i kompiluje tylko w nowym katalogu tymczasowym. Wymaga istniejącej dystrybucji Gradle 9.7.1 w `/workspace/.gradle/wrapper/dists` oraz Javy. Gdy ich brak, kończy się błędem; nie pobiera zależności. Nie używa danych aplikacji ani sieci.

```python
from pathlib import Path
import re, subprocess, tempfile
root = Path.cwd()
doc = root / "docs/audits/zavro-clean/STAGE_02B_PROBES.md"
fixtures = dict(re.findall(r"### ([A-Za-z]+\.kt)\n\n```kotlin\n(.*?)\n```", doc.read_text(), re.S))
out = Path(tempfile.mkdtemp(prefix="zc-aud-2b-"))
for name, source in fixtures.items():
    (out / name).write_text(source + "\n")
code = root / "app/src/main/java/com/prirai/android/nira"
groups = (code / "browser/tabgroups/UnifiedTabGroupManager.kt").read_text()
a = groups.index("data class TabGroupData(")
b = groups.index("\n/**", a)
(out / "TabGroupData.kt").write_text("package com.prirai.android.nira.browser.tabgroups\n" + groups[a:b])
lib = next(Path("/workspace/.gradle/wrapper/dists").glob("gradle-9.7.1-bin/*/gradle-9.7.1/lib"))
cp = ":".join(str(x) for pat in ["kotlin-stdlib-*.jar", "kotlinx-coroutines-core-jvm-*.jar", "kotlinx-serialization-core-jvm-*.jar", "annotations-*.jar"] for x in lib.glob(pat))
cases = [
    ("Retention", "browser/tabs/compose/UnifiedTabOrder.kt", ["TabGroupData.kt", "RetentionProbe.kt"]),
    ("Policy", "browser/profile/ProfileAddonPolicy.kt", ["AndroidStub.kt", "EditStub.kt", "ComponentsStub.kt", "ProbeMainDispatcher.kt", "PolicyProbe.kt"]),
    ("Snackbar", "browser/tabs/compose/GlobalSnackbarManager.kt", ["SnackbarStubs.kt", "LogStub.kt", "SnackbarProbe.kt"]),
]
for name, actual, fixture_names in cases:
    classes = out / (name + "-classes")
    subprocess.run(["java", "-cp", str(lib / "*"), "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect", "-classpath", cp, "-d", str(classes), str(code / actual)] + [str(out / x) for x in fixture_names], check=True, timeout=60)
    if name == "Policy":
        service = classes / "META-INF/services/kotlinx.coroutines.internal.MainDispatcherFactory"
        service.parent.mkdir(parents=True, exist_ok=True)
        service.write_text("audit.probe.ProbeFactory\n")
    subprocess.run(["java", "-cp", str(classes) + ":" + cp, name + "ProbeKt"], check=True, timeout=15)
print("Probe files:", out)
```

## Fixture

To zależności i scenariusze zapisane jako dokumentacja audytu; nie są nowymi testami w `app/src/test`. Pliki aplikacji są bezpośrednim wejściem kompilatora.

### RetentionProbe.kt

```kotlin
import com.prirai.android.nira.browser.tabs.compose.UnifiedTabOrder
import com.prirai.android.nira.browser.tabgroups.TabGroupData
fun main() {
    val seed = UnifiedTabOrder("default", listOf(UnifiedTabOrder.OrderItem.SingleTab("live"), UnifiedTabOrder.OrderItem.SingleTab("not-restored-yet")))
    check(seed.reconciled(listOf("live"), emptyList()).getAllTabIds().contains("not-restored-yet"))
    println("PASS control: partial restore retains its pending slot")
    var order = UnifiedTabOrder("default", listOf(UnifiedTabOrder.OrderItem.SingleTab("live")))
    repeat(500) { i ->
        order = order.reconciled(listOf("live", "closed-$i"), emptyList())
        order = order.reconciled(listOf("live"), emptyList())
    }
    val retainedClosed = order.getAllTabIds().count { it.startsWith("closed-") }
    check(retainedClosed == 500)
    println("FAIL final-close cleanup: live=1 orderSlots=${order.primaryOrder.size} retainedClosed=$retainedClosed (reproduced)")
    val group = TabGroupData("g", "Test group", 0, listOf("live", "closed-group-member"), 0L)
    val grouped = UnifiedTabOrder("default", emptyList()).reconciled(listOf("live"), listOf(group))
    check("closed-group-member" in grouped.getAllTabIds())
    println("OBSERVED group snapshot: missing member retained; Undo/restore distinction requires lifecycle evidence")
}
```

### AndroidStub.kt

```kotlin
package android.content
import com.prirai.android.nira.ext.Components
class Context(val componentsValue: Components) {
    companion object { const val MODE_PRIVATE=0 }
    val prefs=SharedPreferences()
    fun getSharedPreferences(name:String,mode:Int)=prefs
}
class SharedPreferences {
    private val values=mutableMapOf<String,Set<String>>()
    fun contains(key:String)=values.containsKey(key)
    fun getStringSet(key:String,fallback:Set<String>?)=values[key]?:fallback
    fun edit()=Editor()
    inner class Editor {
        fun putStringSet(key:String,value:Set<String>):Editor { values[key]=value;return this }
        fun apply() {}
    }
}
```

### EditStub.kt

```kotlin
package androidx.core.content
import android.content.SharedPreferences
inline fun SharedPreferences.edit(action:SharedPreferences.Editor.()->Unit) { val editor=edit();editor.action();editor.apply() }
```

### ComponentsStub.kt

```kotlin
package com.prirai.android.nira.ext
import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicInteger
val Context.components get()=componentsValue
class Components(val addonManager:Manager) { val store=Store() }
class Store { val state=State() }
class State { val extensions=emptyMap<String,Extension>() }
class Extension(val isBuiltIn:Boolean,val enabled:Boolean)
class Addon(private val enabled:Boolean) { val id="extension-under-test";fun isInstalled()=true;fun isEnabled()=enabled }
class Manager {
    val enteredOld=CompletableDeferred<Unit>();val releaseOld=CompletableDeferred<Unit>()
    val calls=AtomicInteger();var enabled=false;val applied=mutableListOf<String>()
    suspend fun getAddons():List<Addon> {
        if(calls.incrementAndGet()==1) { enteredOld.complete(Unit);releaseOld.await() }
        return listOf(Addon(enabled))
    }
    fun enableAddon(addon:Addon) { enabled=true;applied.add("enable") }
    fun disableAddon(addon:Addon) { enabled=false;applied.add("disable") }
}
```

### ProbeMainDispatcher.kt

```kotlin
package audit.probe
import kotlinx.coroutines.*
import kotlinx.coroutines.internal.MainDispatcherFactory
import kotlin.coroutines.CoroutineContext
@OptIn(InternalCoroutinesApi::class)
class ProbeFactory:MainDispatcherFactory {
    override val loadPriority=1000
    override fun createDispatcher(allFactories:List<MainDispatcherFactory>):MainCoroutineDispatcher=object:MainCoroutineDispatcher() {
        override val immediate:MainCoroutineDispatcher get()=this
        override fun isDispatchNeeded(context:CoroutineContext)=false
        override fun dispatch(context:CoroutineContext,block:Runnable)=block.run()
    }
    override fun hintOnError()="Audit standalone dispatcher"
}
```

### PolicyProbe.kt

```kotlin
import android.content.Context
import com.prirai.android.nira.ext.*
import com.prirai.android.nira.browser.profile.ProfileAddonPolicy
import kotlinx.coroutines.*
fun main()=runBlocking {
    val manager=Manager();val context=Context(Components(manager))
    context.prefs.edit().putStringSet("enabled_A",emptySet()).putStringSet("enabled_B",setOf("extension-under-test")).apply()
    val old=launch { ProfileAddonPolicy.applyForProfile(context,"A") }
    manager.enteredOld.await()
    val current=launch { ProfileAddonPolicy.applyForProfile(context,"B") }
    current.join()
    check(manager.enabled)
    println("PASS control: current profile B enables the extension")
    manager.releaseOld.complete(Unit);old.join()
    check(!manager.enabled)
    println("FAIL current-profile policy invariant: active=B expectedEnabled=true actualEnabled=${manager.enabled} operations=${manager.applied} (stale A reproduced)")
}
```

### SnackbarStubs.kt

```kotlin
package androidx.compose.material3
enum class SnackbarDuration { Long }
enum class SnackbarResult { ActionPerformed,Dismissed }
class SnackbarHostState {
    var calls=0
    suspend fun showSnackbar(message:String,actionLabel:String?,duration:SnackbarDuration,withDismissAction:Boolean):SnackbarResult { calls++;return SnackbarResult.Dismissed }
}
```

### LogStub.kt

```kotlin
package android.util
object Log { fun w(tag:String,message:String)=0;fun d(tag:String,message:String)=0;fun e(tag:String,message:String,error:Throwable)=0 }
```

### SnackbarProbe.kt

```kotlin
import com.prirai.android.nira.browser.tabs.compose.GlobalSnackbarManager
import kotlinx.coroutines.*
fun main() {
    val manager=GlobalSnackbarManager.getInstance()
    val chrome=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
    val panel=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
    manager.coroutineScope=chrome
    manager.showUndoSnackbar("control",{}, {})
    check(manager.snackbarHostState.calls==1)
    println("PASS control: live chrome scope reaches snackbar host")
    manager.coroutineScope=panel
    panel.cancel()
    manager.showUndoSnackbar("after panel closes",{}, {})
    check(chrome.isActive && manager.coroutineScope===panel && manager.snackbarHostState.calls==1)
    println("FAIL Undo UI dispatch invariant: chromeActive=true retainedScopeActive=${manager.coroutineScope!!.isActive} newHostCalls=0 (reproduced)")
    chrome.cancel()
}
```
