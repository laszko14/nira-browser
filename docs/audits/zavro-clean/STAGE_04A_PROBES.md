# Zavro Clean — dowody izolowanych prób 4A

Baza aplikacji: b7d62f79f285ac81c29a79b326529d42e9b9fde4. Zakres i ograniczenia: [STAGE_04.md](STAGE_04.md). Ten dokument jest protokołem audytu, nie nowym testem aplikacji.

Użyto istniejącej Javy 21.0.12.1 oraz Kotlin 2.4.0 z Gradle 9.7.1 (projekt deklaruje 2.4.10). JUnit i Robolectric nie były w lokalnym cache; niczego nie pobrano. Oryginalne źródła i 23 istniejące ciała testowe skompilowano z minimalnym adapterem asercji/adnotacji, a metody wywołano przez refleksję. PASS tych funkcji nie jest wynikiem Gradle/JUnit/Androida. Resize używa oryginalnej lambdy wyodrębnionej z setupSidePanelResizeGrip; atrapami są Resources, SharedPreferences, MotionEvent, widok i diagnostyka. TestLab.capture zwraca null, jak bez aktywnej sesji diagnostycznej.

## Wyniki rzeczywistych uruchomień

Pierwsze uruchomienie wykonało funkcje testowe i próbę resize. Następnie doprecyzowano sekwencję resize: oba palce zaczynają w pasie uchwytu [380,400), drugi wychodzi z tego pasa jeszcze przed podniesieniem pierwszego. Usuwa to zależność scenariusza od wyłączonego splitMotionEvents. Dwa dalsze uruchomienia z --resize-only sprawdzały tylko ten doprecyzowywany scenariusz i kontrole fixture; nie ponawiano 23 niezmienionych funkcji testowych. Poniższy zapis łączy wynik funkcji z pierwszej próby i wynik końcowej próby resize; wartości kontrolek były jednakowe.

```text
PASS isolated existing test bodies: 23/23 (assertion adapter, not JUnit/Robolectric)
PASS control: one finger resizes and saves portrait width=430
PASS control: landscape writes only width_landscape=420
FAIL resize identity: stationary remaining pointer changes width 400->250; ACTION_UP persists 250 (reproduced)
```

Kod zakończenia 0 oznacza, że check potwierdził oczekiwaną reprodukcję błędu, a nie poprawność resize. Sekwencja: ID0 DOWN przy x395; ID1 POINTER_DOWN przy x390; ID1 przesuwa się do x245, ID0 pozostaje x395; ID0 POINTER_UP; nieruchomy ID1 staje się indeksem 0; MOVE/UP z rawX245 daje 400+245−395=250. Dane wszystkich współrzędnych są syntetyczne. Fixture nie wykonuje systemowego dostarczania zdarzeń ani hit-testu. Rzeczywisty widok i wielodotyk na Androidzie/telefonie: NOT_TESTED. RTL z ZC-AUD-007 potwierdzono wyłącznie analizą źródeł, bez udawania próby platformowej.

## Odtworzenie

Wyodrębnić poniższy blok Python do pliku tymczasowego i uruchomić z katalogu repozytorium. Wymaga istniejącej dystrybucji Gradle 9.7.1 pod /workspace/.gradle/wrapper/dists i java. Nie wywołuje Gradle, nie tworzy APK, nie używa sieci ani danych aplikacji. Zapisuje tylko nowy katalog tymczasowy. Opcja --resize-only pomija uruchomienie metod istniejących testów. Przy braku narzędzi kończy się błędem zamiast instalować zależności.

```python
from pathlib import Path
import subprocess, tempfile, re, sys

root = Path.cwd()
out = Path(tempfile.mkdtemp(prefix="zc-aud-4a-"))
lib = next(Path("/workspace/.gradle/wrapper/dists").glob("gradle-9.7.1-bin/*/gradle-9.7.1/lib"))
cp = ":".join(str(x) for pat in ["kotlin-stdlib-*.jar", "annotations-*.jar"] for x in lib.glob(pat))
main = root / "app/src/main/java/com/prirai/android/nira"
test = root / "app/src/test/java/com/prirai/android/nira/browser/panels"

fixtures = {
"Assertions.kt": r'''
package org.junit.Assert
fun assertTrue(value: Boolean) { check(value) { "assertTrue" } }
fun assertFalse(value: Boolean) { check(!value) { "assertFalse" } }
fun assertNull(value: Any?) { check(value == null) { "assertNull: $value" } }
fun assertEquals(expected: Any?, actual: Any?) { check(expected == actual) { "$expected != $actual" } }
fun assertEquals(expected: Float, actual: Float, delta: Float) {
    check(expected == actual || kotlin.math.abs(expected - actual) <= delta) { "$expected != $actual (delta=$delta)" }
}
fun fail(message: String): Nothing = error(message)
''',
"Test.kt": r'''
package org.junit
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Test
''',
"Runner.kt": r'''
package com.prirai.android.nira.browser.panels
fun main() {
    var count = 0
    for (cls in listOf(SidePanelStateMachineTest::class.java,
                       SidePanelMotionControllerTest::class.java,
                       SidePanelFunctionalSequenceTest::class.java)) {
        for (method in cls.declaredMethods.sortedBy { it.name }) {
            if (!method.isAnnotationPresent(org.junit.Test::class.java)) continue
            try { method.invoke(cls.getDeclaredConstructor().newInstance()) }
            catch (e: java.lang.reflect.InvocationTargetException) { throw e.targetException }
            count++
        }
    }
    check(count == 23)
    println("PASS isolated existing test bodies: $count/23 (assertion adapter, not JUnit/Robolectric)")
}
''',
"MotionEvent.kt": r'''
package android.view
data class MotionEvent(val actionMasked: Int, val rawX: Float, val pointerCount: Int = 1) {
    companion object {
        const val ACTION_DOWN = 0; const val ACTION_UP = 1; const val ACTION_MOVE = 2
        const val ACTION_CANCEL = 3; const val ACTION_POINTER_DOWN = 5; const val ACTION_POINTER_UP = 6
    }
}
''',
"Configuration.kt": r'''
package android.content.res
object Configuration { const val ORIENTATION_LANDSCAPE = 2 }
''',
"Context.kt": r'''
package android.content
object Context { const val MODE_PRIVATE = 0 }
''',
"PreDraw.kt": r'''
package androidx.core.view
object OneShotPreDrawListener {
    fun add(view: Any, action: () -> Unit) { action() }
}
''',
"ResizeProbe.kt": r'''
import android.view.MotionEvent
enum class OperationOutcome { INTERRUPTED, CANCELLED }
enum class CompletionProof { FIRST_PREDRAW }
enum class LabOperation { PANEL_RESIZE }
class Token
class Capture {
    fun operation(op: LabOperation): Token? = null
    fun end(op: Token, outcome: OperationOutcome = OperationOutcome.CANCELLED, proof: CompletionProof? = null) {}
}
object TestLab { fun capture(): Capture? = null }
class Metrics { var widthPixels = 500; var density = 1f }
class Config { var orientation = 1 }
class Resources { val displayMetrics = Metrics(); val configuration = Config() }
class Root { var width = 400 }
class Binding { val root = Root() }
class Attributes { var width = 400 }
class Window { val attributes = Attributes(); val decorView = Root() }
class Dialog { val window: Window? = Window() }
class Prefs {
    val values = mutableMapOf<String,Int>()
    fun edit() = this
    fun putInt(key: String, value: Int): Prefs { values[key] = value; return this }
    fun apply() {}
}
class FakeContext {
    val prefs = Prefs()
    fun getSharedPreferences(name: String, mode: Int) = prefs
}
class Grip {
    lateinit var listener: (Any?, MotionEvent) -> Boolean
    fun setOnTouchListener(block: (Any?, MotionEvent) -> Boolean) { listener = block }
}
class ResizeFixture {
    val binding = Binding(); val resources = Resources(); val context = FakeContext()
    val isEmbeddedSidePanel = true
    val dialog: Dialog? = null
    val grip = Grip()
    var testLabResize: Pair<Capture,Token>? = null
    var busy = false
    fun requireContext() = context
    fun sidePanelWidth() = 400
    fun setEmbeddedChildInteraction(value: Boolean) { busy = value }
    fun applySidePanelWidth(value: Int) { binding.root.width = value }
    fun setupOriginal() {
__ORIGINAL_LISTENER__
    }
    fun send(action: Int, x: Float, pointers: Int = 1) = grip.listener(null, MotionEvent(action,x,pointers))
}
fun main() {
    val normal = ResizeFixture().apply { setupOriginal() }
    normal.send(MotionEvent.ACTION_DOWN,400f)
    normal.send(MotionEvent.ACTION_MOVE,430f)
    normal.send(MotionEvent.ACTION_UP,430f)
    check(normal.binding.root.width == 430 && normal.context.prefs.values["width_portrait"] == 430 && !normal.busy)
    println("PASS control: one finger resizes and saves portrait width=430")
    val landscape = ResizeFixture().apply { resources.configuration.orientation=2; setupOriginal() }
    landscape.send(MotionEvent.ACTION_DOWN,400f); landscape.send(MotionEvent.ACTION_UP,420f)
    check(landscape.context.prefs.values == mapOf("width_landscape" to 420))
    println("PASS control: landscape writes only width_landscape=420")
    val f = ResizeFixture().apply { setupOriginal() }
    f.send(MotionEvent.ACTION_DOWN,395f)               // ID 0 inside the [380,400) grip
    f.send(MotionEvent.ACTION_POINTER_DOWN,395f,2)    // ID 1 at 390, inside the same 20 px grip
    f.send(MotionEvent.ACTION_MOVE,395f,2)            // ID 1 moves to 245; ID 0 stays at 395
    check(f.binding.root.width == 400)               // listener reads only index 0
    f.send(MotionEvent.ACTION_POINTER_UP,395f,2)      // lift ID 0
    f.send(MotionEvent.ACTION_MOVE,245f)              // stationary ID 1 is now index 0
    check(f.binding.root.width == 250 && f.busy)
    f.send(MotionEvent.ACTION_UP,245f)
    check(f.context.prefs.values["width_portrait"] == 250 && !f.busy)
    println("FAIL resize identity: stationary remaining pointer changes width 400->250; ACTION_UP persists 250 (reproduced)")
}
'''
}
source = (main / "browser/tabs/TabsBottomSheetFragment.kt").read_text()
section = source.split("private fun setupSidePanelResizeGrip()", 1)[1]
original = re.search(r"(        var downX = 0f.*?\n        \})\n    \}", section, re.S).group(1)
fixtures["ResizeProbe.kt"] = fixtures["ResizeProbe.kt"].replace("__ORIGINAL_LISTENER__", original)
for name, value in fixtures.items():
    (out / name).write_text(value)
classes = out / "classes"
sources = [main / "browser/panels/SidePanelStateMachine.kt", main / "browser/panels/SidePanelMotionController.kt"]
sources += [test / (name + ".kt") for name in ["SidePanelStateMachineTest","SidePanelMotionControllerTest","SidePanelFunctionalSequenceTest"]]
sources += list(out.glob("*.kt"))
cmd = ["java","-cp",str(lib/"*"),"org.jetbrains.kotlin.cli.jvm.K2JVMCompiler","-no-stdlib","-no-reflect","-classpath",cp,"-d",str(classes)]
subprocess.run(cmd + [str(x) for x in sources],check=True,timeout=60)
for cls in ["com.prirai.android.nira.browser.panels.RunnerKt","ResizeProbeKt"]:
    if "--resize-only" in sys.argv and cls.endswith("RunnerKt"): continue
    subprocess.run(["java","-cp",str(classes)+":"+cp,cls],check=True,timeout=15)
print("Temporary evidence:", out)

```
