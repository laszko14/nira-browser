package com.prirai.android.nira.media.alpha

import android.content.Context
import com.prirai.android.nira.R
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Explicit, bundled licensed font: config=no prevents implicit ~~/fonts discovery. */
internal object NativeOsd {
    fun prepare(context:Context):String?=runCatching {
        val root=File(context.filesDir.canonicalFile,"alpha-internal-osd-fonts")
        require(!Files.isSymbolicLink(root.toPath()) && root.canonicalFile==root)
        require(root.mkdirs() || root.isDirectory)
        val target=File(root,"ZavroRoboto.ttf")
        require(!Files.isSymbolicLink(target.toPath()) && target.canonicalFile==target)
        val bytes=context.resources.openRawResource(R.font.lua_roboto).use {it.readBytes()}
        if(!target.isFile || !target.readBytes().contentEquals(bytes)) {
            val tmp=File.createTempFile("osd-",".tmp",root)
            try {java.io.FileOutputStream(tmp).use {it.write(bytes);it.fd.sync()}
                Files.move(tmp.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
            }finally{tmp.delete()}
        }
        root.absolutePath
    }.getOrNull()
    fun options(fonts:String)=mapOf("osd-fonts-dir" to fonts,"osd-font" to "Roboto",
        "osd-font-size" to "22","osd-level" to "1","osd-align-x" to "left","osd-align-y" to "top",
        "osd-color" to "white","osd-outline-color" to "black","osd-back-color" to "#00000000",
        "osd-border-style" to "outline-and-shadow","osd-outline-size" to "1","osd-shadow-offset" to "0.5",
        "osd-margin-x" to "10","osd-margin-y" to "64","osd-scale-by-window" to "yes")
}
