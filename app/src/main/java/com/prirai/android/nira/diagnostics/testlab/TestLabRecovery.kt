package com.prirai.android.nira.diagnostics.testlab

import android.content.Context
import android.os.SystemClock
import android.util.AtomicFile
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable internal data class TestLabRecovery(
    val testType:TestType,val appVersion:LabIdentity,val status:LabPhase,val durationMs:Double,
    val errorCounts:Map<LabError,Long>,val timeline:List<LabEvent>,val schemaVersion:Int=1,val incomplete:Boolean=true,
)

/** Private, explicit opt-in; no uncaught exception handler. All disk methods run off Main. */
internal class TestLabRecoveryStore(context:Context,
    private val nanos:()->Long={SystemClock.elapsedRealtimeNanos()},
    private val wallMillis:()->Long={System.currentTimeMillis()},
) {
    private val preferences=context.getSharedPreferences("testlab",Context.MODE_PRIVATE)
    private val file=AtomicFile(File(context.noBackupFilesDir,"testlab-recovery.json"))
    @Volatile var enabled=runCatching{preferences.getBoolean("crash_recovery",false)}.getOrDefault(false);private set
    private var generation=0L
    private var active=false
    private var lastWrite:Long?=null
    private val json=Json{encodeDefaults=true;explicitNulls=true}
    @Synchronized fun setEnabled(value:Boolean):Boolean {
        if(!preferences.edit().putBoolean("crash_recovery",value).commit())return false
        enabled=value
        if(!value){generation++;active=false;file.delete();lastWrite=null}
        return true
    }
    @Synchronized fun begin():Long {active=true;lastWrite=null;return ++generation}
    @Synchronized fun isDue(owner:Long):Boolean = enabled&&active&&owner==generation&&(lastWrite==null||nanos()-lastWrite!!>=5_000_000_000L)
    @Synchronized fun save(owner:Long,input:TestLabRecovery):Boolean {
        if(!enabled||!active||owner!=generation)return false
        val at=nanos();val previous=lastWrite
        if(previous!=null&&at-previous<5_000_000_000L)return false
        var bounded=input.copy(timeline=input.timeline.takeLast(64))
        var bytes=json.encodeToString(bounded).toByteArray(Charsets.UTF_8)
        while(bytes.size>65_536&&bounded.timeline.isNotEmpty()) {
            bounded=bounded.copy(timeline=bounded.timeline.drop((bounded.timeline.size+1)/2))
            bytes=json.encodeToString(bounded).toByteArray(Charsets.UTF_8)
        }
        if(bytes.size>65_536)return false
        var stream:java.io.FileOutputStream?=null
        return try {
            stream=file.startWrite();stream.write(bytes);file.finishWrite(stream)
            file.baseFile.setLastModified(wallMillis());lastWrite=at;true
        } catch (_:java.io.IOException) {file.failWrite(stream);false}
    }
    @Synchronized fun read():TestLabRecovery? {
        if(!enabled){file.delete();return null}
        val base=file.baseFile
        if(!base.exists())return null
        if(base.length() !in 1..65_536 || wallMillis()-base.lastModified() !in 0..86_400_000L){file.delete();return null}
        return try {
            val data=file.openRead().use { stream ->
                val bytes=ByteArray(65_537);var read=0
                while(read<bytes.size) {val count=stream.read(bytes,read,bytes.size-read);if(count<0)break;read+=count}
                bytes.copyOf(read)
            }
            if(data.size>65_536){file.delete();null}
            else json.decodeFromString<TestLabRecovery>(data.toString(Charsets.UTF_8)).takeIf{
                it.schemaVersion==1&&it.incomplete&&it.durationMs.isFinite()&&it.durationMs in 0.0..60_000.0&&it.timeline.size<=64
            }.also{if(it==null)file.delete()}
        } catch (_:Exception) {file.delete();null}
    }
    @Synchronized fun end(owner:Long) {if(owner==generation){active=false;generation++;file.delete();lastWrite=null}}
    @Synchronized fun delete(){generation++;active=false;lastWrite=null;file.delete()}
}
