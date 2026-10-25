package com.prirai.android.nira.media.alpha.scripts

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Immutable per-engine snapshot. No native commands, loading, or engine ownership here. */
internal class CustomLuaRuntime private constructor(
    val scripts:List<CustomScript>,val paths:List<String>,val epoch:String,
    val options:Map<String,String>,val modelAvailable:Boolean,val prepared:Boolean,
    private val directory:File?,private val config:File,
) {
    fun ambientFiles():List<File> = config.listFiles().orEmpty().filter {
        com.prirai.android.nira.media.alpha.AlphaFeedbackPolicy.ambientFile(it.name) &&
            !Files.isSymbolicLink(it.toPath()) && it.isFile && it.canonicalFile==it.absoluteFile
    }
    fun property(row:CustomScript)="user-data/zavro-custom/${row.id}"
    fun cleanup() {
        // Called only AFTER the sole MPV owner destroys the engine (shutdown handlers ran).
        if(!prepared)return
        directory?.let(::safeDeleteTree)
        cleanupAmbient(config)
    }
    companion object {
        fun prepare(store:CustomLuaStore,configDir:File,template:String):CustomLuaRuntime {
            val epoch=UUID.randomUUID().toString()
            var directory:File?=null
            val config=configDir.canonicalFile
            try {
                require(!Files.isSymbolicLink(configDir.toPath()))
                val base=File(config,"alpha-custom-runtime")
                require(!Files.isSymbolicLink(base.toPath()) && base.canonicalFile==base)
                require(base.mkdirs() || base.isDirectory)
                // No other active engine: MpvEngineGate was claimed before this method.
                base.listFiles().orEmpty().filter {it.name.matches(Regex("[a-f0-9-]{36}"))}.forEach(::safeDeleteTree)
                cleanupAmbient(config)
                val frozen=store.snapshot()
                val rows=frozen.scripts
                val model=frozen.model
                if(model!=null) {
                    val opts=File(config,"script-opts")
                    require(!Files.isSymbolicLink(opts.toPath()) && opts.canonicalFile==opts)
                    require(opts.mkdirs() || opts.isDirectory)
                    atomic(File(opts,"std.rnnn"),model)
                }
                directory=File(base,epoch).also {require(it.mkdir())}
                val clients=File(directory,"clients").also {require(it.mkdir())}
                val originals=File(directory,"originals").also {require(it.mkdir())}
                val paths=rows.map {(row,bytes)->
                    val original=File(originals,row.name);atomic(original,bytes)
                    val code=com.prirai.android.nira.media.alpha.AlphaLuaEnvironment.source(config,
                        "user-data/zavro-feedback/${row.id}")+"\n"+template.replace("@SOURCE@",quote(original.absolutePath))
                        .replace("@PROPERTY@",quote("user-data/zavro-custom/${row.id}"))
                        .replace("@EPOCH@",quote(epoch))
                        .replace("@CLIENT@",quote(CustomLuaStore.clientName(row.name)))
                    File(clients,row.name).also {atomic(it,code.toByteArray())}.absolutePath
                }
                require(paths.none {it.contains(File.pathSeparatorChar)})
                return CustomLuaRuntime(rows.map {it.first},paths,epoch,frozen.options,model!=null,true,directory,config)
            }catch(_:Exception) {
                directory?.let(::safeDeleteTree)
                return CustomLuaRuntime(emptyList(),emptyList(),epoch,emptyMap(),false,false,null,config)
            }
        }
        /** Lua decimal escapes avoid all source/path interpolation and UTF-8 quoting ambiguity. */
        fun quote(value:String)="\""+value.toByteArray().joinToString("") {"\\"+(it.toInt() and 255).toString().padStart(3,'0')}+"\""
        private fun atomic(file:File,bytes:ByteArray) {
            require(!Files.isSymbolicLink(file.toPath()) && file.canonicalFile==file)
            val tmp=File.createTempFile("custom-",".tmp",file.parentFile)
            try {
                FileOutputStream(tmp).use {it.write(bytes);it.fd.sync()}
                Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE)
            } finally {tmp.delete()}
        }
        private fun safeDeleteTree(file:File) {
            if(Files.isSymbolicLink(file.toPath())) {file.delete();return}
            if(file.isDirectory)file.listFiles().orEmpty().forEach(::safeDeleteTree)
            file.delete()
        }
        private fun cleanupAmbient(config:File) {
            // Only the documented temporary files of the supplied Ambient+ v4; never a broad *.glsl delete.
            config.listFiles().orEmpty().filter {com.prirai.android.nira.media.alpha.AlphaFeedbackPolicy.ambientFile(it.name)}
                .forEach {if(!it.isDirectory || Files.isSymbolicLink(it.toPath()))it.delete()}
        }
    }
}
