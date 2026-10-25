-- Zavro v1158: explicit, undoable button revision. NOT an autoload/startup script.
-- DRC: parameters specified by the user; no private button source copied here.
-- DE profiles: RexDialogueAudio.kt, MPV Alpha v54 c6107ce14a9f1768c73f975c5d60b1d8bd71aaed,
-- Apache-2.0 (see assets/licenses/mpv-alpha-apache-2.0.txt). Native Lua/MPV API only.
-- Bridge EXEC_OK is separate from the correlated DSP receipt below.
function zavro_audio_repair(kind, action, request)
    local state = rawget(_G, '__zavro_audio_repair_v1')
    if type(state) ~= 'table' then state = {claims={}, results={}};_G.__zavro_audio_repair_v1=state end
    local labels = {drc='zavro_repair_drc_v1', de='zavro_repair_de_v1'}
    local graphs = {
        drc={
            'acompressor=ratio=2:threshold=-24dB:attack=20:release=250',
            'acompressor=ratio=3:threshold=-18dB:attack=20:release=250',
            'acompressor=ratio=5:threshold=-12dB:attack=10:release=300',
        },
        de={
            'dialoguenhance=original=1:enhance=0.55:voice=2',
            'dialoguenhance=original=1:enhance=0.85:voice=3',
            'dialoguenhance=original=1:enhance=1.20:voice=4',
            'dialoguenhance=original=1:enhance=1.70:voice=6',
            'dialoguenhance=original=1:enhance=2.30:voice=8',
        },
    }
    local receipt={request=request, kind=kind, status='BAD_CONFIGURATION', level=-1,
        commands=0, rollback='NOT_NEEDED', available='UNCONFIRMED', af=false}
    local function equal(a,b,depth)
        depth=(depth or 0)+1;if depth>16 then return false end
        if type(a)~=type(b) then return false end
        if type(a)~='table' then return a==b end
        for k,v in pairs(a) do if not equal(v,b[k],depth) then return false end end
        for k in pairs(b) do if a[k]==nil then return false end end
        return true
    end
    local function read()
        local ok, chain=pcall(mp.get_property_native,'af')
        if not ok or type(chain)~='table' or #chain>64 then return nil end
        for _,f in ipairs(chain) do
            if type(f)~='table' or type(f.name)~='string' then return nil end
        end
        return chain
    end
    local function own(chain)
        local result={}
        for i,f in ipairs(chain) do if f.label==labels[kind] then result[#result+1]={node=f,index=i} end end
        return result
    end
    local function foreign(chain)
        local result={};for _,f in ipairs(chain) do if f.label~=labels[kind] then result[#result+1]=f end end
        return result
    end
    local function matches(item,level)
        if not item then return level==0 end
        local f=item.node
        if level<=0 or f.name~='lavfi' or f.enabled~=true or type(f.params)~='table' then return false end
        local parameters=0;for _ in pairs(f.params) do parameters=parameters+1 end
        return parameters==1 and (f.params.graph or f.params['@0'])==graphs[kind][level]
    end
    local function command(level)
        receipt.commands=receipt.commands+1
        local op=level==0 and 'remove' or 'add'
        local value='@'..labels[kind]..(level==0 and '' or ':lavfi=['..graphs[kind][level]..']')
        -- command_native(void) succeeds as nil WITHOUT error; nil,error is failure.
        -- Unlike the original unchecked commandv, inspect both pcall and native status.
        local called,result,err=pcall(mp.command_native,{'af',op,value})
        if not called then receipt.status='LUA_EXCEPTION';return false end
        return result~=false and err==nil
    end
    local function operation()
        if not labels[kind] or (action~='cycle' and action~='off') or type(request)~='string'
            or #request>96 or not request:match('^[%w%-]+$') then return false end
        local before=read()
        if not before then receipt.status='AF_READ_FAILED';return false end
        receipt.af=true
        local mine=own(before)
        local old=state.claims[kind] or 0
        -- A matching label alone does not establish ownership. Foreign/taken-over/duplicate
        -- labels require user intervention, never blind remove/overwrite.
        if #mine>1 or (#mine==1 and (old==0 or not matches(mine[1],old))) then
            receipt.status='CONFLICT';return false
        end
        if #mine==0 then old=0;state.claims[kind]=nil end
        receipt.level=old
        local target=action=='off' and 0 or (old+1)%(#graphs[kind]+1)
        if target==old then receipt.status='VERIFIED';return true end
        if target>0 then
            local choices=mp.get_property_native('option-info/af/choices')
            if type(choices)~='table' then receipt.status='CAPABILITY_UNCONFIRMED';return false end
            local name=kind=='drc' and 'acompressor' or 'dialoguenhance'
            local found=false;for _,v in ipairs(choices) do if v==name then found=true end end
            receipt.available=found and 'YES' or 'NO'
            if not found then receipt.status='UNSUPPORTED_FILTER';return false end
            -- Alpha's DE filter requires stereo and produces an enhanced centre channel.
            -- Do not silently downmix mono/multichannel source to fabricate compatibility.
            if kind=='de' and mp.get_property_native('audio-params/channel-count')~=2 then
                receipt.status='AUDIO_FORMAT';return false
            end
        end
        local accepted=command(target)
        local after=read()
        local current=after and own(after) or nil
        local expected=after and #current==(target==0 and 0 or 1) and matches(current[1],target)
        local foreignSame=after and equal(foreign(before),foreign(after))
        local indexSame=target==0 or (expected and current[1].index==(#mine==0 and #before+1 or mine[1].index))
        if accepted and expected and foreignSame and indexSame then
            state.claims[kind]=target>0 and target or nil
            receipt.status='VERIFIED';receipt.level=target;return true
        end
        local reason=receipt.status=='LUA_EXCEPTION' and 'LUA_EXCEPTION'
            or (not after and 'AF_READ_FAILED') or (not foreignSame and 'CONFLICT')
            or (not accepted and 'MPV_REJECTED') or 'AF_MISMATCH'
        -- Native mpv normally rolls back failed graph initialisation itself. Verify it;
        -- otherwise repair only our label if it still equals the target we just wrote.
        local restored=after and equal(before,after)
        if not restored and after and (expected or #current==0) then
            command(old)
            local rolled=read()
            restored=rolled and equal(before,rolled)
        end
        receipt.rollback=restored and 'VERIFIED' or 'UNCONFIRMED'
        receipt.status=reason
        receipt.level=restored and old or -1
        return false
    end
    local called, success=pcall(operation)
    if not called then receipt.status='LUA_EXCEPTION';success=false end
    -- Bounded cumulative mailbox survives property coalescing; no source/paths/exception
    -- text, AF parameters or button titles/IDs are ever published.
    state.results[#state.results+1]=receipt
    if #state.results>64 then table.remove(state.results,1) end
    pcall(mp.set_property_native,'user-data/zavro/drc-de-v1',{version=1,results=state.results})
    if success then
        local title=kind=='drc' and 'DRC' or 'Dialogue Enhance'
        local text=receipt.level==0 and 'WYŁĄCZONY' or tostring(receipt.level)..'/'..#graphs[kind]
        -- Only actual native command + AF/foreign-chain verification permits success OSD.
        -- Android pixel visibility is separate and must still be verified on the phone.
        pcall(mp.osd_message,title..': '..text,2.5)
    end
    return success==true
end
