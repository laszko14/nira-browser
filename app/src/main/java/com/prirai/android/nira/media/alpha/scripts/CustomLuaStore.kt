package com.prirai.android.nira.media.alpha.scripts

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class CustomImportException(val reason:String):Exception(reason)
@Serializable internal data class CustomScript(
    val id:String,val name:String,val hash:String,val enabled:Boolean=false,val previous:String?=null,
) { override fun toString()="CustomScript(<private>)" }
@Serializable internal data class CustomScriptManifest(
    val version:Int=1,val scripts:List<CustomScript> = emptyList(),val modelHash:String?=null,
    val options:Map<String,String> = emptyMap(),val valid:Boolean=true,
) { override fun toString()="CustomScriptManifest(<private>)" }

internal class CustomLuaSnapshot(val scripts:List<Pair<CustomScript,ByteArray>>,val model:ByteArray?,val options:Map<String,String>) {
    override fun toString()="CustomLuaSnapshot(<private>)"
}

/** Private immutable objects + one atomic manifest. Import/read NEVER executes source. */
internal class CustomLuaStore(directory:File) {
    private val root=directory.absoluteFile
    private val mutableState=MutableStateFlow(load())
    val state=mutableState.asStateFlow()
    private fun fail(reason:String):Nothing=throw CustomImportException(reason)
    private fun checked():CustomScriptManifest {
        if(!state.value.valid)fail("CORRUPT_STORE")
        return state.value
    }
    private fun validate(m:CustomScriptManifest) {
        require(m.version==1 && m.valid && m.scripts.size<=32)
        require(m.scripts.map {it.id}.toSet().size==m.scripts.size)
        require(m.scripts.map {clientName(it.name)}.toSet().size==m.scripts.size)
        m.scripts.forEach { require(it.id.matches(Regex("[a-f0-9-]{36}")));name(it.name);hash(it.hash);it.previous?.let(::hash) }
        m.modelHash?.let(::hash)
        require(CustomMpvConf.validate(m.options))
    }
    private fun load():CustomScriptManifest=try {
        ensureRoot()
        val target=safe("manifest.json")
        if(!target.exists())CustomScriptManifest() else {
            require(target.length()<=65536)
            Json.decodeFromString<CustomScriptManifest>(target.readText()).also(::validate)
        }
    }catch(_:Exception){CustomScriptManifest(valid=false)}
    private fun ensureRoot() {
        require(!Files.isSymbolicLink(root.toPath()))
        require(root.mkdirs() || root.isDirectory)
    }
    private fun safe(relative:String):File {
        ensureRoot()
        val base=root.canonicalFile
        val f=File(base,relative)
        require(f.canonicalFile==File(base,relative) && !Files.isSymbolicLink(f.toPath()))
        return f
    }
    private fun write(target:File,bytes:ByteArray) {
        require(!Files.isSymbolicLink(target.toPath()))
        var tmp:File?=null
        try {
            tmp=File.createTempFile("pending-",".tmp",target.parentFile)
            FileOutputStream(tmp).use {it.write(bytes);it.fd.sync()}
            Files.move(tmp.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
        } finally {tmp?.delete()}
    }
    private fun objectFile(sha:String):File {hash(sha);return safe("object-$sha")}
    private fun objectBytes(sha:String):ByteArray {
        val f=objectFile(sha);require(f.isFile && f.length()<=MAX_MODEL)
        val bytes=f.readBytes();require(digest(bytes)==sha);return bytes
    }
    private fun put(bytes:ByteArray):String {
        val sha=digest(bytes);val f=objectFile(sha)
        if(!f.exists()) {
            require(root.listFiles().orEmpty().sumOf {if(it.isFile)it.length() else 0}+bytes.size<=64L*1024*1024)
            write(f,bytes)
        } else require(objectBytes(sha).contentEquals(bytes))
        return sha
    }
    private fun commit(next:CustomScriptManifest) {
        validate(next)
        if(next==state.value)return
        val target=safe("manifest.json")
        if(target.exists())write(safe("manifest.previous.json"),target.readBytes())
        write(target,Json.encodeToString(next).toByteArray())
        mutableState.value=next
        runCatching {collectGarbage()}
    }
    private fun collectGarbage() {
        val keep=mutableSetOf<String>()
        fun retain(m:CustomScriptManifest) {
            m.scripts.forEach {keep.add(it.hash);it.previous?.let(keep::add)}
            m.modelHash?.let(keep::add)
        }
        retain(state.value)
        val previous=safe("manifest.previous.json")
        if(previous.isFile) {
            require(previous.length()<=65536)
            val old=Json.decodeFromString<CustomScriptManifest>(previous.readText());validate(old);retain(old)
        }
        root.listFiles().orEmpty().filter {it.name.matches(Regex("object-[a-f0-9]{64}")) && it.name.removePrefix("object-") !in keep}
            .forEach {if(!it.isDirectory || Files.isSymbolicLink(it.toPath()))it.delete()}
    }
    @Synchronized fun importScripts(files:List<Pair<String,ByteArray>>) {
        val old=checked();require(files.isNotEmpty() && files.size<=32)
        runCatching {collectGarbage()}
        val inputs=files.map { (filename,bytes)->name(filename);decode(bytes,MAX_SCRIPT);filename to bytes }
        if(inputs.map {clientName(it.first)}.toSet().size!=inputs.size)fail("DUPLICATE_CLIENT")
        val rows=old.scripts.toMutableList()
        inputs.forEach { (filename,bytes)->
            val index=rows.indexOfFirst {clientName(it.name)==clientName(filename)}
            if(index>=0 && rows[index].name!=filename)fail("DUPLICATE_CLIENT")
            val sha=digest(bytes)
            if(index<0)rows.add(CustomScript(UUID.randomUUID().toString(),filename,sha))
            else if(rows[index].hash!=sha)rows[index]=rows[index].copy(hash=sha,previous=rows[index].hash,enabled=false)
        }
        val next=old.copy(scripts=rows);validate(next)
        inputs.forEach {put(it.second)}
        commit(next)
    }
    @Synchronized fun enable(id:String,value:Boolean,consent:Boolean,expectedHash:String?=null) {
        val old=checked();val row=old.scripts.singleOrNull {it.id==id}?:fail("MISSING_SCRIPT")
        if(value && !consent)fail("CONSENT_REQUIRED")
        if(value && expectedHash!=null && row.hash!=expectedHash)fail("STALE_APPROVAL")
        if(value)decode(objectBytes(row.hash),MAX_SCRIPT)
        commit(old.copy(scripts=old.scripts.map {if(it.id==id)it.copy(enabled=value) else it}))
    }
    @Synchronized fun delete(id:String) {val old=checked();commit(old.copy(scripts=old.scripts.filterNot {it.id==id}))}
    @Synchronized fun restore(id:String) {
        val old=checked();val row=old.scripts.singleOrNull {it.id==id}?:fail("MISSING_SCRIPT")
        val previous=row.previous?:fail("NO_PREVIOUS");decode(objectBytes(previous),MAX_SCRIPT)
        commit(old.copy(scripts=old.scripts.map {if(it.id==id)it.copy(hash=previous,previous=it.hash,enabled=false) else it}))
    }
    @Synchronized fun source(id:String):String {
        val row=checked().scripts.singleOrNull {it.id==id}?:fail("MISSING_SCRIPT")
        return decode(objectBytes(row.hash),MAX_SCRIPT)
    }
    @Synchronized fun selected():List<Pair<CustomScript,ByteArray>> {
        if(!state.value.valid)return emptyList()
        return checked().scripts.filter {it.enabled}.map {it to objectBytes(it.hash).also {b->decode(b,MAX_SCRIPT)}}
    }
    @Synchronized fun snapshot():CustomLuaSnapshot {
        val m=checked()
        return CustomLuaSnapshot(selected(),model(),m.options.toMap())
    }
    @Synchronized fun importModel(bytes:ByteArray) {
        val old=checked();RnnnModel.validate(bytes)
        commit(old.copy(modelHash=put(bytes)))
    }
    @Synchronized fun model():ByteArray?=checked().modelHash?.let {objectBytes(it).also(RnnnModel::validate)}
    @Synchronized fun saveOptions(plan:CustomConfPlan) {val old=checked();require(CustomMpvConf.validate(plan.values));commit(old.copy(options=plan.values))}
    companion object {
        const val MAX_SCRIPT=1024*1024
        const val MAX_MODEL=4*1024*1024
        fun read(input:InputStream,limit:Int):ByteArray {
            val bytes=input.readBytesBounded(limit)
            if(bytes.size>limit)throw CustomImportException("TOO_LARGE")
            return bytes
        }
        private fun InputStream.readBytesBounded(limit:Int):ByteArray {
            val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(true) {
                val n=read(buffer);if(n<0)break
                if(n==0) {val one=read();if(one<0)break;out.write(one)} else out.write(buffer,0,n)
                if(out.size()>limit)throw CustomImportException("TOO_LARGE")
            }
            return out.toByteArray()
        }
        fun decode(bytes:ByteArray,limit:Int):String {
            if(bytes.isEmpty() || bytes.size>limit || 0.toByte() in bytes)throw CustomImportException("INVALID_FILE")
            return try {Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()}
            catch(_:Exception){throw CustomImportException("INVALID_UTF8")}
        }
        fun name(value:String) {
            if(!Regex("[A-Za-z0-9_][A-Za-z0-9_-]{0,59}\\.lua").matches(value) ||
                clientName(value) in setOf("mpv_alpha_buttons_bridge","main","zavro_custom_monitor",
                    "osc","ytdl_hook","stats","console","auto_profiles","select","positioning","commands","context_menu"))
                throw CustomImportException("UNSAFE_NAME")
        }
        fun clientName(name:String)=name.removeSuffix(".lua").map {if(it.isLetterOrDigit() && it.code<128)it else '_'}.joinToString("")
        fun digest(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)}
        private fun hash(value:String) {require(value.matches(Regex("[a-f0-9]{64}")))}
    }
}

/** RNNoise v1 dense/GRU layer dimensions/weight counts. DSP acceptance is separate. */
internal object RnnnModel {
    fun validate(bytes:ByteArray) {
        val lines=CustomLuaStore.decode(bytes,CustomLuaStore.MAX_MODEL).trim().lineSequence().filter {it.isNotBlank()}.take(23).toList()
        if(lines.firstOrNull()!="rnnoise-nu model file version 1")throw CustomImportException("INVALID_MODEL")
        var at=1
        fun numbers(expected:Int?=null):List<Int> {
            val row=lines.getOrNull(at++)?:throw CustomImportException("INVALID_MODEL")
            if(expected!=null && row.length>expected*8+16)throw CustomImportException("INVALID_MODEL")
            val parts=row.trim().split(Regex("\\s+"),limit=(expected?:3)+1)
            if(expected!=null && parts.size!=expected)throw CustomImportException("INVALID_MODEL")
            return parts.map {it.toIntOrNull()?:throw CustomImportException("INVALID_MODEL")}
        }
        val sizes=mutableListOf<Pair<Int,Int>>()
        for(gru in listOf(false,true,true,true,false,false)) {
            val dimensions=numbers(3);val (inputs,neurons,activation)=dimensions
            require(inputs in 1..512 && neurons in 1..128 && activation in 0..2)
            sizes.add(inputs to neurons)
            val mul=if(gru)3 else 1
            val weights=numbers(inputs*neurons*mul)
            require(weights.all {it in -128..127})
            if(gru)require(numbers(neurons*neurons*3).all {it in -128..127})
            require(numbers(neurons*mul).all {it in -128..127})
        }
        require(at==lines.size)
        require(sizes[0].first==42 && sizes[1].first==sizes[0].second &&
            sizes[2].first==sizes[0].second+sizes[1].second+42 &&
            sizes[3].first==sizes[1].second+sizes[2].second+42 &&
            sizes[4]==(sizes[3].second to 22) && sizes[5]==(sizes[1].second to 1))
    }
}
