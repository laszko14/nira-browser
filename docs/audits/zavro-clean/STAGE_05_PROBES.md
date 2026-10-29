# Zavro Clean — dowody izolowanych prób etapu 5

Baza kodu: b7d62f79f285ac81c29a79b326529d42e9b9fde4. Wejściowy HEAD dokumentacji: 545be94a41ad49e81cf3de645e9fbfa8d35c465a. Data: 2026-10-29 UTC. Zakres: [STAGE_05.md](STAGE_05.md).

## Callback dialogu po zniszczeniu panelu

Użyto istniejącej Java21 i kompilatora Kotlin2.4.0 z Gradle9.7.1 (projekt: Kotlin2.4.10). Bez Gradle, nowych zależności, APK i danych aplikacji. Oryginalne showAddFolderDialog, setBookmarkList i onDestroyView wyodrębniono z BookmarksBottomSheetFragment.kt bez przepisywania ich treści. Właściciel, widoki, Fragment/requireContext, adapter, manager, zasoby i sam dialog są atrapami. Kolejność save→callback w dialogu odpowiada odczytanym AddBookmarkFolderDialog.kt:74–78; nie skompilowano tej klasy Androida ani nie wykonano prawdziwego obrotu/FragmentManagera.

Pierwsza kompilacja fixture nie powiodła się z powodu brakującej deklaracji bookmarkAdapter w atrapie Owner. Uzupełniono wyłącznie fixture; drugi przebieg zakończył się poniższym wynikiem. Nie był to błąd kompilacji projektu ani ponowienie bez nowej informacji.

```text
PASS control: live owner receives dialog callback and refreshes list
FAIL late dialog callback: original setBookmarkList reaches binding!! after onDestroyView (reproduced)
FAIL late dialog callback: detached owner reaches requireContext before binding (reproduced)
```

Kod0 oznacza, że check potwierdził odtworzenie nieprawidłowej obsługi spóźnionego callbacku; nie jest to PASS poprawności panelu. Drugi i trzeci przypadek mają osobne fixture: zachowany kontekst przy zniszczonym widoku oraz odłączony właściciel. Zapis managera jest atrapą; nie testowano trwałości zakładek. Dostarczenie callbacku na prawdziwym Androidzie po obrocie i usunięciu child Fragmentu: NOT_TESTED. Ścieżkę utrzymania dialogu przez kontekst Activity i brak zamknięcia/odpięcia jego listenera w lifecycle panelu sprawdzono statycznie.

## Sortowanie — osobna kontrola statyczna

Python odczytał oryginalny blok showSortDialog i gałąź sortBookmarks(A_Z), bez ich uruchamiania jako UI. Potwierdził mapę {0:A_Z,1:A_Z,2:A_Z} i sortBy(title.lowercase). Output:

```text
FAIL static sort contract: Name=A_Z, URL=A_Z, Date=A_Z; adapter A_Z sorts by title
```

To nie test działania dialogu ani zapisu kolejności na telefonie. Dla syntetycznych kart o tytułach A/B i URL kończących się z/a sort po nazwie da A,B, po URL powinien dać B,A; obecny wybór URL kieruje zawsze do pierwszego algorytmu.

## Odtworzenie próby JVM

Wyodrębnić blok do pliku tymczasowego i uruchomić Pythonem z katalogu repozytorium. Wymaga java i istniejącego Gradle9.7.1 pod /workspace/.gradle/wrapper/dists. Tworzy wyłącznie nowy katalog tymczasowy; nie uruchamia Gradle ani sieci i nie dodaje testów do app/src/test.

```python
from pathlib import Path
import subprocess, tempfile
root = Path.cwd()
source = (root / "app/src/main/java/com/prirai/android/nira/browser/bookmark/ui/BookmarksBottomSheetFragment.kt").read_text()
out = Path(tempfile.mkdtemp(prefix="zc-aud-5-"))
lib = next(Path("/workspace/.gradle/wrapper/dists").glob("gradle-9.7.1-bin/*/gradle-9.7.1/lib"))
cp = ":".join(str(x) for pat in ["kotlin-stdlib-*.jar","annotations-*.jar"] for x in lib.glob(pat))
def function(signature):
    start = source.index(signature); brace = source.index("{",start); depth = 1; end = brace+1
    while depth:
        if source[end] == "{": depth += 1
        elif source[end] == "}": depth -= 1
        end += 1
    return source[start:end]
fixture = r'''
import com.prirai.android.nira.R
open class View {
    var visibility = 0
    var text: Any? = null
    companion object { const val GONE=8; const val VISIBLE=0 }
}
class Recycler: View() {
    var adapter: BookmarkAdapter? = null
    fun removeOnScrollListener(listener: Any) {}
}
class Binding {
    val bookmarksRecyclerView=Recycler(); val backButton=View()
    val bookmarksTitle=View(); val pathView=View()
}
class BookmarkFolderItem(val title: String="synthetic",val itemList: MutableList<Int> = mutableListOf(1))
class BookmarkAdapter(context: Any,list: MutableList<Int>,listener: Any)
class Manager {
    val root=BookmarkFolderItem()
    var saves=0
    fun save(): Boolean { saves++; return true }
}
object Dialogs { lateinit var last: AddBookmarkFolderDialog }
class AddBookmarkFolderDialog(context: Any,val mManager: Manager?,title: String?,mParent: BookmarkFolderItem?,item: Any?) {
    private var listener: ((Any?,Int)->Unit)? = null
    fun setOnClickListener(block: (Any?,Int)->Unit) { listener=block }
    fun show() { Dialogs.last=this }
    // Callback ordering matches original AddBookmarkFolderDialog.show:74-78.
    fun pressPositive() { if(mManager!!.save()) listener?.invoke(null,1) }
}
class Scroll { fun finish(outcome: Any) {} }
open class Base { open fun onDestroyView() {} }
class Owner: Base() {
    var _binding: Binding?=Binding()
    private val binding get() = _binding!!
    private lateinit var bookmarkAdapter: BookmarkAdapter
    val manager=Manager()
    var currentFolder=manager.root
    var attached=true
    val testLabScroll=Scroll(); val testLabScrollListener=Any()
    fun requireContext(): Any { check(attached) { "Fragment not attached to a context" }; return this }
    fun getString(id: Int) = "synthetic"
    fun showEmptyState() {}
    fun hideEmptyState() {}
    fun clearEmbeddedSidePanelCallbacks() {}
    fun open() { showAddFolderDialog() }
__SHOW_FOLDER__
__SET_LIST__
__DESTROY__
}
fun main() {
    val live=Owner()
    live.open(); Dialogs.last.pressPositive()
    check(live.manager.saves==1 && live._binding!!.bookmarksRecyclerView.adapter!=null)
    println("PASS control: live owner receives dialog callback and refreshes list")
    val deadView=Owner()
    deadView.open(); val retained=Dialogs.last
    deadView.onDestroyView()
    check(deadView._binding==null)
    try { retained.pressPositive(); error("Expected destroyed view failure") }
    catch (e: NullPointerException) {
        check(deadView.manager.saves==1)
        println("FAIL late dialog callback: original setBookmarkList reaches binding!! after onDestroyView (reproduced)")
    }
    val detached=Owner()
    detached.open(); val retainedDetached=Dialogs.last
    detached.onDestroyView(); detached.attached=false
    try { retainedDetached.pressPositive(); error("Expected detached fragment failure") }
    catch (e: IllegalStateException) {
        check(e.message=="Fragment not attached to a context" && detached.manager.saves==1)
        println("FAIL late dialog callback: detached owner reaches requireContext before binding (reproduced)")
    }
}
'''
for marker,sig in [
("__SHOW_FOLDER__","private fun showAddFolderDialog()"),
("__SET_LIST__","private fun setBookmarkList(folder: BookmarkFolderItem)"),
("__DESTROY__","override fun onDestroyView()")
]: fixture=fixture.replace(marker,function(sig))
(out/"Probe.kt").write_text(fixture)
(out/"R.kt").write_text("package com.prirai.android.nira\nobject R { object string { const val action_bookmarks=1 } }\n")
(out/"Outcome.kt").write_text("package com.prirai.android.nira.diagnostics.testlab\nenum class OperationOutcome { INTERRUPTED }\n")
classes=out/"classes"
subprocess.run(["java","-cp",str(lib/"*"),"org.jetbrains.kotlin.cli.jvm.K2JVMCompiler","-no-stdlib","-no-reflect","-classpath",cp,"-d",str(classes)]+[str(x) for x in out.glob("*.kt")],check=True,timeout=60)
subprocess.run(["java","-cp",str(classes)+":"+cp,"ProbeKt"],check=True,timeout=15)
print("Temporary evidence:",out)

```
