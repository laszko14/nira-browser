# Zavro Clean — dowody izolowanych prób 4B

Baza kodu: b7d62f79f285ac81c29a79b326529d42e9b9fde4. Wejściowy HEAD dokumentacji: 6a1efa81710d336f04ac96f5afb4b1680bef1c94. Zakres i granice oceny: [STAGE_04.md](STAGE_04.md). Żaden plik aplikacji ani test aplikacji nie został zmieniony.

## Wykonanie i ograniczenia

Java 21, Kotlin 2.4.0 z już dostępnego Gradle 9.7.1; nie uruchamiano Gradle, nie pobierano zależności. Funkcje createNamedGroupFromUngrouped, createGroup, removeTabFromGroup, persistPositions i moveDrag wyodrębniono bez przepisywania z aktualnych źródeł. DragPlacement.kt oraz ciała DragPlacementTest skompilowano bezpośrednio z repozytorium. Minimalny adapter adnotacji i asercji zastępuje JUnit; wywołania przez refleksję nie są wynikiem testDebugUnitTest.

Atrapy: ViewModel/StateFlow otoczony w fixture, DAO w pamięci, no-op transakcja Room, diagnostyka, widoczne wiersze i geometria Compose. tabMatchesContext zwraca true, ponieważ wszystkie syntetyczne karty mają zgodny, domyślny profil; test nie weryfikuje filtrowania profili. Manager używa reentrant mutex i kontrolowanego zawieszenia przed przyjęciem mutacji zamiast pełnego oryginalnego mechanizmu IO/Room. Pozwala to narzucić kolejność dwóch operacji; nie mierzy prawdopodobieństwa wyścigu na urządzeniu. Zapisy kolejności i kolektory VM są stubami, więc nie jest to test prawdziwego Room/DataStore/BrowserStore.

Kontrola: odzyskiwanie karty już należącej do grupy zwraca false i pozostawia członkostwo. Reprodukcja: odzyskiwanie sprawdza wolną kartę a, po czym czeka przed mutacją; druga oryginalna createGroup tworzy Other=[a,b]; wznowiona recovery tworzy Recovery=[a], usuwając a z Other i zwracając true. Końcowe rekordy in-memory DAO także zawierają Other=[b], Recovery=[a]. Dowód dotyczy luki między walidacją i mutacją, nie gwarancji transakcyjnych Room.

Hover: wyodrębnione moveDrag zapisuje group_B przy y75; layout po symulowanym scroll zmienia wiersze z B:50..100 na B:0..50 i C:50..100, bez nowego zdarzenia palca. Aktualny DragPlacement wybiera C, zapisany hover nadal B. Nie wykonano Compose LaunchedEffect, frame clock ani ewentualnych zdarzeń syntetycznych platformy. Dlatego skutek użytkowy ZC-AUD-010 ma LIKELY, a nie CONFIRMED; wymaga rzeczywistego testu pointer input i automatycznego przewijania.

## Rzeczywisty output

```text
PASS isolated existing DragPlacementTest bodies: 3/3 (assertion adapter, not JUnit)
PASS control: already grouped input rejected before recovery
FAIL recovery membership: returned=true; concurrently grouped a taken from Other; Other=[b], Recovery=[a] (reproduced)
FAIL hover consistency: stationary auto-scroll keeps hover=group_B, current drop=group_C (source model, no Compose scheduler)
```

Compiler zgłosił wyłącznie redundantne key?.toString() w fixture moveDrag: wiersz testowy ma key typu String, podczas gdy oryginalny LazyListItemInfo ma key typu Any. Nie jest to nowy błąd kompilacji aplikacji. Proces zakończył się kodem 0: check wymagał reprodukcji niezgodności. Napis FAIL oznacza naruszony kontrakt, nie niepowodzenie uruchomienia programu ani PASS funkcjonalny.

Osobno wykonano 6 wybranych istniejących kontroli JS: 6 PASS, 0 FAIL. To asercje struktury źródeł, nie odtwarzanie gestów ani wyścigów:

```sh
node --test --test-name-pattern='side panel|side drawer|side-drawer|ungrouped|creating groups' app/src/test/js/grouped-strip-and-side-drag-v1122.test.cjs
```

## Odtworzenie próby JVM

Wyodrębnić poniższy blok do pliku tymczasowego i uruchomić Pythonem z katalogu repozytorium. Wymaga istniejącej dystrybucji Gradle 9.7.1 pod /workspace/.gradle/wrapper/dists oraz java. Tworzy wyłącznie nowy katalog tymczasowy, bez sieci, APK i danych aplikacji. Nie ponawiano prób 2B/4A.

```python
from pathlib import Path
import subprocess, tempfile, re

root = Path.cwd()
main = root / "app/src/main/java/com/prirai/android/nira"
out = Path(tempfile.mkdtemp(prefix="zc-aud-4b-"))
lib = next(Path("/workspace/.gradle/wrapper/dists").glob("gradle-9.7.1-bin/*/gradle-9.7.1/lib"))
cp = ":".join(str(x) for pat in ["kotlin-stdlib-*.jar","kotlinx-coroutines-core-jvm-*.jar","annotations-*.jar"] for x in lib.glob(pat))
vm = (main / "browser/tabs/compose/TabViewModel.kt").read_text()
gm = (main / "browser/tabgroups/UnifiedTabGroupManager.kt").read_text()
side = (main / "browser/tabs/TabsBottomSheetFragment.kt").read_text()
def function(source, signature):
    start = source.index(signature)
    brace = source.index("{", start)
    depth = 1
    end = brace + 1
    while depth:
        if source[end] == "{": depth += 1
        elif source[end] == "}": depth -= 1
        end += 1
    return source[start:end]

fixture = r'''
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.*
import java.util.UUID
import com.prirai.android.nira.browser.tabs.compose.*

data class Content(val private: Boolean = false)
data class Tab(val id: String, val contextId: String? = null, val content: Content = Content())
data class TabGroupData(val id: String, val name: String, val color: Int, val tabIds: List<String>, val createdAt: Long, val isCollapsed: Boolean = false, val contextId: String? = null)
data class TabGroup(val id: String, val name: String, val color: String, val createdAt: Long, val isActive: Boolean, val contextId: String?)
data class TabGroupMember(val tabId: String, val groupId: String, val position: Int)
object LabOperation { const val GROUP_CREATE = 1; const val TAB_MOVE = 2 }
object GroupEvent {
    data class GroupCreated(val group: TabGroupData)
    data class TabRemovedFromGroup(val tabId: String, val groupId: String)
}
class Dao {
    val members = mutableMapOf<String,List<String>>()
    suspend fun insertGroup(group: TabGroup) {}
    suspend fun insertTabGroupMember(m: TabGroupMember) { members[m.groupId] = members[m.groupId].orEmpty() + m.tabId }
    suspend fun removeAllTabsFromGroup(id: String) { members.remove(id) }
    suspend fun removeTabFromGroup(tab: String, group: String) { members[group] = members[group].orEmpty() - tab }
}
class Database
suspend fun <T> Database.withTransaction(block: suspend () -> T): T = block()
class Manager {
    val groupsCache = linkedMapOf<String,TabGroupData>()
    val tabToGroupMap = mutableMapOf<String,String>()
    val dao = Dao(); val database = Database()
    val AVAILABLE_COLORS = listOf(1)
    var beforeAdmission: (suspend () -> Unit)? = null
    val mutex = Mutex()
    private class Owner(val owner: Manager): AbstractCoroutineContextElement(Key) { companion object Key: CoroutineContext.Key<Owner> }
    suspend fun <T> mutate(op: Int, block: suspend () -> T): T {
        if (coroutineContext[Owner]?.owner === this) return block()
        val gate = beforeAdmission
        beforeAdmission = null
        gate?.invoke()
        return mutex.withLock { withContext(Owner(this)) { block() } }
    }
    suspend fun awaitInitialized() = true
    fun tabMatchesContext(id: String, context: String?) = true // all fixture tabs are default, non-private
    fun colorToString(color: Int) = color.toString()
    fun emitStateUpdate() {}
    fun recordEvent(event: Any) {}
    fun getAllGroups() = groupsCache.values.toList()
    suspend fun deleteGroup(id: String, notifyChange: Boolean) { error("unused: fixture retains another member") }
__CREATE_GROUP__
__REMOVE_FROM_GROUP__
__PERSIST_POSITIONS__
}
class Recovery(val groupManager: Manager) {
    val _currentProfileId = MutableStateFlow<String?>("default")
    val _tabs = MutableStateFlow(listOf(Tab("a"),Tab("b")))
    val _expandedGroups = MutableStateFlow(emptySet<String>())
    fun contextIdForProfile(profile: String): String? = if (profile == "default") null else "profile_$profile"
    suspend fun saveCurrentOrder(profile: String) {}
    suspend fun refreshGroupsForProfile(profile: String) {}
__RECOVERY__
}
data class Offset(val x: Float,val y: Float)
data class RowInfo(val key: String,val offset: Int,val size: Int)
class LayoutInfo(var visibleItemsInfo: List<RowInfo>)
class ListState(val layoutInfo: LayoutInfo)
class Hover {
    var dragY = 0f; var dragX = 0f; var listTopY = 0f; var listLeftX = 0f
    var hoveredKey: String? = null
    val listState = ListState(LayoutInfo(listOf(RowInfo("group_B",50,50))))
__MOVE_DRAG__
}
fun main() = runBlocking {
    // Compile and execute the three real pure test methods with an assertion adapter.
    val test = com.prirai.android.nira.browser.tabs.DragPlacementTest()
    var count = 0
    for (method in test.javaClass.declaredMethods.sortedBy { it.name }) {
        if (method.isAnnotationPresent(org.junit.Test::class.java)) {
            try { method.invoke(test) }
            catch (e: java.lang.reflect.InvocationTargetException) { throw e.targetException }
            count++
        }
    }
    check(count == 3)
    println("PASS isolated existing DragPlacementTest bodies: 3/3 (assertion adapter, not JUnit)")

    val m = Manager(); val recovery = Recovery(m)
    val other = m.createGroup(listOf("a","b"), name="Other", groupId="other")
    check(!recovery.createNamedGroupFromUngrouped(listOf("a"),"Recovery"))
    check(m.groupsCache["other"]!!.tabIds == listOf("a","b"))
    println("PASS control: already grouped input rejected before recovery")
    // New fixture: pause the recovery after its check, at createGroup admission.
    val concurrent = Manager(); val vm = Recovery(concurrent)
    val waiting = CompletableDeferred<Unit>(); val resume = CompletableDeferred<Unit>()
    concurrent.beforeAdmission = { waiting.complete(Unit); resume.await() }
    val pending = async(start=CoroutineStart.UNDISPATCHED) { vm.createNamedGroupFromUngrouped(listOf("a"),"Recovery") }
    waiting.await()
    concurrent.createGroup(listOf("a","b"),name="Other",groupId="other")
    check(concurrent.groupsCache["other"]!!.tabIds == listOf("a","b"))
    resume.complete(Unit)
    check(pending.await())
    val created = concurrent.getAllGroups().single { it.name == "Recovery" }
    check(concurrent.groupsCache["other"]!!.tabIds == listOf("b") && created.tabIds == listOf("a"))
    check(concurrent.dao.members["other"] == listOf("b") && concurrent.dao.members[created.id] == listOf("a"))
    println("FAIL recovery membership: returned=true; concurrently grouped a taken from Other; Other=[b], Recovery=[a] (reproduced)")

    val h = Hover()
    h.moveDrag(Offset(10f,75f))
    check(h.hoveredKey == "group_B")
    // Simulated layout after scrollBy: pointer and effect keys remain unchanged.
    h.listState.layoutInfo.visibleItemsInfo = listOf(RowInfo("group_B",0,50),RowInfo("group_C",50,50))
    val target = DragPlacement.sideTarget(h.dragY,0,100,h.listState.layoutInfo.visibleItemsInfo.map { DropRow(it.key,it.offset,it.size) })!!.key
    check(h.hoveredKey == "group_B" && target == "group_C")
    println("FAIL hover consistency: stationary auto-scroll keeps hover=group_B, current drop=group_C (source model, no Compose scheduler)")
}
'''
substitutions = {
"__CREATE_GROUP__": function(gm, "suspend fun createGroup("),
"__REMOVE_FROM_GROUP__": function(gm, "suspend fun removeTabFromGroup("),
"__PERSIST_POSITIONS__": function(gm, "private suspend fun persistPositions("),
"__RECOVERY__": function(vm, "suspend fun createNamedGroupFromUngrouped("),
"__MOVE_DRAG__": function(side, "fun moveDrag(pointerRoot: Offset)")
}
for marker,value in substitutions.items(): fixture = fixture.replace(marker,value)
(out / "Probe.kt").write_text(fixture)
assertions = r'''
package org.junit.Assert
fun assertNull(value: Any?) { check(value == null) { "assertNull: $value" } }
fun assertEquals(expected: Any?, actual: Any?) { check(expected == actual) { "$expected != $actual" } }
fun assertEquals(message: String, expected: Any?, actual: Any?) { check(expected == actual) { "$message: $expected != $actual" } }
'''
(out / "Assertions.kt").write_text(assertions)
(out / "Test.kt").write_text("package org.junit\n@Target(AnnotationTarget.FUNCTION)\n@Retention(AnnotationRetention.RUNTIME)\nannotation class Test\n")
sources = list(out.glob("*.kt")) + [main / "browser/tabs/compose/DragPlacement.kt", root / "app/src/test/java/com/prirai/android/nira/browser/tabs/DragPlacementTest.kt"]
classes = out / "classes"
subprocess.run(["java","-cp",str(lib/"*"),"org.jetbrains.kotlin.cli.jvm.K2JVMCompiler","-no-stdlib","-no-reflect","-classpath",cp,"-d",str(classes)]+[str(x) for x in sources],check=True,timeout=60)
subprocess.run(["java","-cp",str(classes)+":"+cp,"ProbeKt"],check=True,timeout=15)
print("Temporary evidence:",out)

```
