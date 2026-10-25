package com.prirai.android.nira.media.alpha

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import kotlinx.serialization.json.*

internal enum class AlphaImportError { TOO_LARGE, UNSAFE_XML, INVALID_XML, BAD_STRUCTURE, BAD_VERSION, BAD_TYPE, BAD_VALUE, DUPLICATE_KEY, BAD_BUTTONS }
internal class AlphaImportException(val code:AlphaImportError):SAXException(code.name)
internal enum class AlphaImportScope { ALL, BUTTONS_LAYOUT, APPEARANCE }

internal class AlphaImportPlan(
    val values:Map<String,AlphaPreferenceValue>,
    val slots:List<AlphaLuaButton?>?,
    val skippedCount:Int,
    val databaseRecords:Int,
    val warnings:Set<String>,
    val versionDeclared:Boolean,
    private val appearanceFields:Map<String,Set<String>> = emptyMap(),
) {
    val matchedCount:Int get()=values.size+if(slots!=null)1 else 0
    fun applyTo(base:AlphaPreferences,scope:AlphaImportScope):AlphaPreferences {
        var next=base
        for((key,value) in values) {
            val group=AlphaImportSchema.rules.getValue(key).group
            if(scope==AlphaImportScope.BUTTONS_LAYOUT && group!=AlphaSettingGroup.LAYOUT)continue
            if(scope==AlphaImportScope.APPEARANCE && group==AlphaSettingGroup.LAYOUT)continue
            next=when {
                group==AlphaSettingGroup.HUD -> next.copy(hud=next.hud+(key to value))
                isAlphaLuaLayoutKey(key) -> next.copy(buttonLayout=next.buttonLayout+(key to value))
                else -> next.copy(luaAppearance=next.luaAppearance+(key to value))
            }
        }
        if(slots!=null && scope!=AlphaImportScope.APPEARANCE)next=next.copy(slots=slots)
        if(slots!=null && scope==AlphaImportScope.APPEARANCE) {
            val byId=slots.filterNotNull().associateBy { it.id }
            next=next.copy(slots=next.slots.map { old ->
                val incoming=old?.id?.let { byId[it] }
                if(old==null || incoming==null)old else {
                    val fields=appearanceFields[old.id].orEmpty()
                    old.copy(
                        alwaysVisible=if("alwaysVisible" in fields)incoming.alwaysVisible else old.alwaysVisible,
                        backgroundColor=if("backgroundColor" in fields)incoming.backgroundColor else old.backgroundColor,
                        textColor=if("textColor" in fields)incoming.textColor else old.textColor,
                        activeBackgroundColor=if("activeBackgroundColor" in fields)incoming.activeBackgroundColor else old.activeBackgroundColor,
                        activeTextColor=if("activeTextColor" in fields)incoming.activeTextColor else old.activeTextColor,
                        icon=if("icon" in fields)incoming.icon else old.icon,
                    )
                }
            })
        }
        return next
    }
    override fun toString()="AlphaImportPlan(<redacted>)"
}

/** Offline canonical SettingsManager v54 format, with independent DTD/entity rejection. */
internal object AlphaSettingsImporter {
    const val MAX_XML=8*1024*1024
    fun parse(input:InputStream):AlphaImportPlan {
        try {
            val bytes=ByteArrayOutputStream()
            val buffer=ByteArray(8192)
            var total=0
            while(true) {
                val n=input.read(buffer)
                if(n<0)break
                if(n==0) {
                    val one=input.read()
                    if(one<0)break
                    if(++total>MAX_XML)throw AlphaImportException(AlphaImportError.TOO_LARGE)
                    bytes.write(one)
                    continue
                }
                total+=n
                if(total>MAX_XML)throw AlphaImportException(AlphaImportError.TOO_LARGE)
                bytes.write(buffer,0,n)
            }
            val decoder=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val text=decoder.decode(ByteBuffer.wrap(bytes.toByteArray())).toString().removePrefix("\uFEFF")
            // Canonical export is UTF-8. Reject before the XML parser can see a DTD,
            // even on Android implementations that do not support every SAX feature.
            if(Regex("""<!\s*(DOCTYPE|ENTITY)""",RegexOption.IGNORE_CASE).containsMatchIn(text))
                throw AlphaImportException(AlphaImportError.UNSAFE_XML)
            if(text.startsWith("<?xml")) {
                val declaration=text.substringBefore("?>")
                val version=Regex("""version\s*=\s*['"]([^'"]+)['"]""").find(declaration)?.groupValues?.get(1)
                if(version!=null && version!="1.0")throw AlphaImportException(AlphaImportError.BAD_VERSION)
                val encoding=Regex("""encoding\s*=\s*['"]([^'"]+)['"]""").find(declaration)?.groupValues?.get(1)
                if(encoding!=null && !encoding.equals("UTF-8",true))
                    throw AlphaImportException(AlphaImportError.INVALID_XML)
            }
            val handler=RootHandler()
            val factory=SAXParserFactory.newInstance().apply {
                isNamespaceAware=false
                isValidating=false
            }
            val reader=factory.newSAXParser().xmlReader
            for(feature in listOf(
                "http://xml.org/sax/features/external-general-entities",
                "http://xml.org/sax/features/external-parameter-entities",
                "http://apache.org/xml/features/nonvalidating/load-external-dtd",
            )) runCatching { reader.setFeature(feature,false) }
            runCatching { reader.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true) }
            reader.setEntityResolver { _,_ -> throw AlphaImportException(AlphaImportError.UNSAFE_XML) }
            reader.contentHandler=handler
            reader.errorHandler=handler
            reader.parse(InputSource(StringReader(text)))
            return handler.result()
        } catch(e:AlphaImportException) { throw e }
          catch(_:Exception) { throw AlphaImportException(AlphaImportError.INVALID_XML) }
    }

    private abstract class ImportHandler:DefaultHandler() {
        abstract fun result():AlphaImportPlan
        override fun error(e:org.xml.sax.SAXParseException) {throw AlphaImportException(AlphaImportError.INVALID_XML)}
        override fun fatalError(e:org.xml.sax.SAXParseException) {throw AlphaImportException(AlphaImportError.INVALID_XML)}
    }
    private class RootHandler:ImportHandler() {
        private var delegate:ImportHandler?=null
        override fun startElement(uri:String?,localName:String?,qName:String,a:Attributes) {
            val target=delegate?:when(qName) {
                "mpvExSettings"->ExportHandler();"customButtons"->ButtonExportHandler()
                else->throw AlphaImportException(AlphaImportError.BAD_STRUCTURE)
            }.also {delegate=it}
            target.startElement(uri,localName,qName,a)
        }
        override fun endElement(uri:String?,localName:String?,qName:String) {delegate?.endElement(uri,localName,qName)}
        override fun characters(ch:CharArray,start:Int,length:Int) {delegate?.characters(ch,start,length)}
        override fun result()=delegate?.result()?:throw AlphaImportException(AlphaImportError.BAD_STRUCTURE)
    }
    /** Canonical CustomButtonScreen v54 <customButtons><button slot="…">, without regex XML parsing. */
    private class ButtonExportHandler:ImportHandler() {
        private val stack=mutableListOf<String>()
        private val rows=MutableList<AlphaLuaButton?>(43){null}
        private val seen=mutableSetOf<Int>()
        private val fields=linkedMapOf<String,String>()
        private val content=StringBuilder()
        private var slot=-1
        private var closed=false
        private var nodes=0
        private val appearances=mutableMapOf<String,Set<String>>()
        private val appearance=setOf("alwaysVisible","backgroundColor","textColor","activeBackgroundColor","activeTextColor","icon")
        private val allowed=appearance+setOf("id","title","content","longPressContent","onStartup","enabled")
        private fun reject():Nothing=throw AlphaImportException(AlphaImportError.BAD_BUTTONS)
        override fun startElement(uri:String?,localName:String?,qName:String,a:Attributes) {
            if(++nodes>1000)reject()
            when(stack.size) {
                0->if(qName!="customButtons" || a.length!=0 || closed)reject()
                1->{
                    if(qName!="button" || a.length!=1 || a.getQName(0)!="slot")reject()
                    slot=a.getValue("slot").toIntOrNull()?:reject()
                    if(slot !in 0..42 || !seen.add(slot))reject()
                    fields.clear()
                }
                2->{if(qName !in allowed || a.length!=0 || fields.containsKey(qName))reject();content.clear()}
                else->reject()
            }
            stack.add(qName)
        }
        override fun characters(ch:CharArray,start:Int,length:Int) {
            if(stack.size==3) {if(content.length+length>1_048_576)reject();content.append(ch,start,length)}
            else if((start until start+length).any {!ch[it].isWhitespace()})reject()
        }
        override fun endElement(uri:String?,localName:String?,qName:String) {
            if(stack.lastOrNull()!=qName)reject()
            if(stack.size==3)fields[qName]=content.toString()
            if(stack.size==2) {
                fun value(key:String)=fields[key]
                fun bool(key:String):Boolean?=value(key)?.let {if(it=="true")true else if(it=="false")false else reject()}
                fun color(key:String):Int?=value(key)?.let {it.toIntOrNull()?:reject()}
                val id=value("id")?.takeIf {it.isNotBlank()}?:reject()
                val row=AlphaLuaButton(id,value("title")?:reject(),value("content")?:reject(),
                    value("longPressContent")?:"",value("onStartup")?:"",bool("enabled")?:true,
                    bool("alwaysVisible"),color("backgroundColor"),color("textColor"),
                    color("activeBackgroundColor"),color("activeTextColor"),value("icon"))
                if(rows.filterNotNull().any {it.id==id})reject()
                rows[slot]=row;appearances[id]=fields.keys.intersect(appearance)
            }
            stack.removeAt(stack.lastIndex)
            if(stack.isEmpty())closed=true
        }
        override fun result():AlphaImportPlan {
            if(!closed || stack.isNotEmpty())reject()
            return AlphaImportPlan(emptyMap(),AlphaSlots.normalize(rows),0,0,emptySet(),false,appearances)
        }
    }
    private class ExportHandler:ImportHandler() {
        private val stack=mutableListOf<String>()
        private val seen=mutableSetOf<String>()
        private val values=linkedMapOf<String,AlphaPreferenceValue>()
        private var slots:List<AlphaLuaButton?>?=null
        private var appearanceFields:Map<String,Set<String>> = emptyMap()
        private val sections=mutableSetOf<String>()
        private var preferences=false
        private var closed=false
        private var nodes=0
        private var skipped=0
        private var records=0
        private var versionDeclared=false
        private val warnings=mutableSetOf<String>()
        private fun reject(code:AlphaImportError=AlphaImportError.BAD_STRUCTURE):Nothing=throw AlphaImportException(code)
        override fun startElement(uri:String?,localName:String?,qName:String,a:Attributes) {
            if(++nodes>20000 || stack.size>=32)reject()
            val parent=stack.lastOrNull()
            if(parent==null) {
                if(closed || qName!="mpvExSettings")reject()
                val version=a.getValue("version")
                versionDeclared=version!=null
                if(version!=null && version !in setOf("1","1.0"))reject(AlphaImportError.BAD_VERSION)
                for(i in 0 until a.length)if(a.getQName(i) !in setOf("version","exportDate"))reject()
            } else if(stack.size==1) {
                if(!sections.add(qName))reject()
                when(qName) {
                    "preferences" -> { if(preferences || a.length!=0)reject();preferences=true }
                    "database","networkConnections" -> Unit
                    else -> reject()
                }
            } else if(stack[1]=="preferences") {
                if(parent!="preferences" || qName!="preference" || a.length!=3)reject()
                if((0 until a.length).map { a.getQName(it) }.toSet()!=setOf("key","type","value"))reject()
                val key=a.getValue("key")?:reject()
                if(key.length>256)reject()
                if(!seen.add(key))reject(AlphaImportError.DUPLICATE_KEY)
                val type=a.getValue("type")?:reject()
                val raw=a.getValue("value")?:reject()
                if(key=="custom_buttons_json") {
                    if(type!="string")reject(AlphaImportError.BAD_TYPE)
                    try {
                        if(raw.length>1_048_576)reject(AlphaImportError.BAD_BUTTONS)
                        rejectDuplicateJsonKeys(raw)
                        slots=AlphaSlots.decode(raw)
                        val decoded=Json.parseToJsonElement(raw)
                        val array=if(decoded is JsonArray)decoded else decoded.jsonObject["slots"]?.jsonArray?:JsonArray(emptyList())
                        val allowed=setOf("alwaysVisible","backgroundColor","textColor","activeBackgroundColor","activeTextColor","icon")
                        appearanceFields=array.filter { it!=JsonNull }.associate {
                            val obj=it.jsonObject
                            obj.getValue("id").jsonPrimitive.content to obj.keys.intersect(allowed)
                        }
                        val ids=slots!!.filterNotNull().map { it.id }
                        if(ids.any { it.isBlank() } || ids.toSet().size!=ids.size)reject(AlphaImportError.BAD_BUTTONS)
                    } catch(e:AlphaImportException) { throw e }
                      catch(_:Exception) { reject(AlphaImportError.BAD_BUTTONS) }
                } else {
                    val rule=AlphaImportSchema.rules[key]
                    if(rule==null)skipped++ else {
                        if(type!=rule.type)reject(AlphaImportError.BAD_TYPE)
                        val value=when(type) {
                            "int" -> raw.toIntOrNull()?.let { AlphaPreferenceValue.IntValue(it) }
                            "float" -> raw.toFloatOrNull()?.takeIf { it.isFinite() }?.let { AlphaPreferenceValue.FloatValue(it) }
                            "boolean" -> when(raw) {
                                "true" -> AlphaPreferenceValue.Bool(true)
                                "false" -> AlphaPreferenceValue.Bool(false)
                                else -> null
                            }
                            "string" -> raw.takeIf { it.length<=4096 }?.let { AlphaPreferenceValue.Text(it) }
                            else -> null
                        }?:reject(AlphaImportError.BAD_VALUE)
                        val number=when(value) {
                            is AlphaPreferenceValue.IntValue -> value.value.toDouble()
                            is AlphaPreferenceValue.FloatValue -> value.value.toDouble()
                            else -> null
                        }
                        if(number!=null && rule.min!=null && rule.max!=null &&
                            number !in rule.min..rule.max)warnings.add(key)
                        if(type=="string" && (key.endsWith("_color") || key.endsWith("_color_hex"))) {
                            val named=setOf("black","darkgray","gray","lightgray","white","red","green","blue",
                                "yellow","cyan","magenta","aqua","fuchsia","darkgrey","grey","lightgrey",
                                "lime","maroon","navy","olive","purple","silver","teal")
                            if(!Regex("""#[0-9a-fA-F]{6}(?:[0-9a-fA-F]{2})?""").matches(raw) &&
                                raw.lowercase(java.util.Locale.ROOT) !in named)reject(AlphaImportError.BAD_VALUE)
                        }
                        if(key=="alpha_hud_layout_v2" && raw.isNotBlank()) {
                            val tokens=raw.split('|',',').map { it.trim().lowercase(java.util.Locale.ROOT) }.filter { it.isNotEmpty() }
                            val known=setOf("battery","clock","adjusted","remaining","speed","download")
                            if(raw.count { it=='|' }!=1 || tokens.any { it !in known } ||
                                tokens.toSet().size!=tokens.size)warnings.add("layout_items_ignored")
                            if("download" in tokens)warnings.add("download_hidden")
                        }
                        if(key=="lua_button_position_y_dp" && number!=null && number<92)warnings.add(key)
                        values[key]=value
                    }
                }
            } else if(qName=="networkConnection")records++ // No attributes or values retained.
            stack.add(qName)
        }
        override fun endElement(uri:String?,localName:String?,qName:String) {
            if(stack.isEmpty() || stack.removeAt(stack.lastIndex)!=qName)reject()
            if(stack.isEmpty())closed=true
        }
        override fun characters(ch:CharArray,start:Int,length:Int) {
            if(stack.size>1 && stack[1] in setOf("database","networkConnections"))return
            if((start until start+length).any { !ch[it].isWhitespace() })reject()
        }
        override fun error(e:org.xml.sax.SAXParseException) { reject(AlphaImportError.INVALID_XML) }
        override fun fatalError(e:org.xml.sax.SAXParseException) { reject(AlphaImportError.INVALID_XML) }
        override fun result():AlphaImportPlan {
            if(!closed || !preferences)reject()
            return AlphaImportPlan(values.toMap(),slots?.toList(),skipped,records,warnings.toSet(),versionDeclared,appearanceFields)
        }
    }

    /** String-aware duplicate-key guard; the existing strict serializer validates the schema. */
    private data class JsonFrame(val keys:MutableSet<String>?,var expectingKey:Boolean)
    private fun rejectDuplicateJsonKeys(text:String) {
        val frames=mutableListOf<JsonFrame>()
        var i=0
        var nodes=0
        while(i<text.length) {
            when(text[i]) {
                '{','[' -> {
                    if(frames.size>=64 || ++nodes>10000)throw AlphaImportException(AlphaImportError.BAD_BUTTONS)
                    frames.add(JsonFrame(if(text[i]=='{')mutableSetOf() else null,text[i]=='{'));i++
                }
                '}',']' -> { if(frames.isEmpty())throw AlphaImportException(AlphaImportError.BAD_BUTTONS);frames.removeAt(frames.lastIndex);i++ }
                ',' -> { frames.lastOrNull()?.let { if(it.keys!=null)it.expectingKey=true };i++ }
                ':' -> { frames.lastOrNull()?.expectingKey=false;i++ }
                '"' -> {
                    val start=i++
                    while(i<text.length && text[i]!='"') { if(text[i]=='\\')i++;i++ }
                    if(i>=text.length)throw AlphaImportException(AlphaImportError.BAD_BUTTONS)
                    i++
                    val frame=frames.lastOrNull()
                    if(frame?.keys!=null && frame.expectingKey) {
                        val key=Json.decodeFromString<String>(text.substring(start,i))
                        if(!frame.keys.add(key))throw AlphaImportException(AlphaImportError.BAD_BUTTONS)
                    }
                }
                else -> i++
            }
        }
    }
}
