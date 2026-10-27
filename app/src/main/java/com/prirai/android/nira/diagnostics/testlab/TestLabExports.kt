package com.prirai.android.nira.diagnostics.testlab

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Only explicit user actions call these methods. Diagnostics never request broad storage permissions. */
internal object TestLabExports {
    private val cacheMutex=Mutex()
    private fun directory(context:Context)=File(context.cacheDir,"testlab_share")
    private fun prefix(type:TestType)="testlab-${type.name.lowercase(Locale.ROOT)}-"
    suspend fun write(context:Context,uri:Uri,payload:LabExportPayload)=withContext(Dispatchers.IO) {
        val bytes=payload.content.toByteArray(Charsets.UTF_8)
        require(bytes.size<=TestLabReportGenerator.MAX_BYTES)
        val stream=context.contentResolver.openOutputStream(uri,"wt")?:error("Export unavailable")
        stream.use{it.write(bytes)}
    }
    suspend fun share(context:Context,bundle:TestLabReportBundle):Uri=withContext(Dispatchers.IO) {
        cacheMutex.withLock {
        val dir=directory(context);check(dir.isDirectory||dir.mkdirs())
        val existing=dir.listFiles()?.filter{it.isFile}?.sortedByDescending{it.lastModified()}.orEmpty()
        existing.drop(1).forEach{remove(context,it)} // At most two short-lived, unique exports.
        val file=File(dir,prefix(bundle.report.testType)+UUID.randomUUID()+".txt")
        val bytes=bundle.text.toByteArray(Charsets.UTF_8);require(bytes.size<=TestLabReportGenerator.MAX_BYTES)
        file.outputStream().use{it.write(bytes)}
        FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file)
        }
    }
    suspend fun clean(context:Context,type:TestType?=null,expiredOnly:Boolean=false)=withContext(Dispatchers.IO) {
        cacheMutex.withLock {
        val now=System.currentTimeMillis()
        directory(context).listFiles()?.filter{it.isFile&&(type==null||it.name.startsWith(prefix(type)))&&
            (!expiredOnly||now-it.lastModified() !in 0..86_400_000L)}?.forEach{remove(context,it)}
        }
    }
    private fun remove(context:Context,file:File) {
        runCatching{context.revokeUriPermission(FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file),Intent.FLAG_GRANT_READ_URI_PERMISSION)}
        file.delete()
    }
}
