-- Zavro custom-script client host. Imported source is immutable and NOT bundled here.
-- Each host is loaded by libmpv as its own native Lua client, under its original name.
local source_path = @SOURCE@
local status_property = @PROPERTY@
local epoch = @EPOCH@
local expected_client = @CLIENT@
local mp = require('mp')
local report = {epoch=epoch, loaded=false, syntax=false, runtime=false,
    errors=0, handlers=0, cycle=false, off=false, ambient=false, conflict=false}
local native_set = mp.set_property_native
local function publish()
    -- Never publish source, errors, document paths, handler names or return values.
    pcall(native_set, status_property, report)
end
local function guarded(fn)
    return function(...)
        local result = table.pack(pcall(fn, ...))
        if not result[1] then
            report.errors = math.min(report.errors + 1, 1000000)
            report.runtime = true
            return
        end
        return table.unpack(result, 2, result.n)
    end
end
local register_message = mp.register_script_message
local unregister_message = mp.unregister_script_message
local registered = {}
local function handlers()
    local count = 0
    for _ in pairs(registered) do count = count + 1 end
    report.handlers = count
    report.cycle = registered.cycle == true
    report.off = registered.off == true
    report.ambient = registered['rex-ambient-toggle'] == true
end
mp.register_script_message = function(name, fn)
    local result = register_message(name, guarded(fn))
    registered[name] = true
    handlers()
    return result
end
mp.unregister_script_message = function(name)
    local result = unregister_message(name)
    registered[name] = nil
    handlers()
    return result
end
-- Callback identity is preserved for unregister/unobserve, including multiple subscriptions.
for _, pair in ipairs({{'register_event','unregister_event'}, {'observe_property','unobserve_property'}}) do
    local add, remove = mp[pair[1]], mp[pair[2]]
    local wrapped = {}
    mp[pair[1]] = function(...)
        local args = table.pack(...)
        local fn = args[args.n]
        local callback = wrapped[fn] or guarded(fn)
        wrapped[fn] = callback
        args[args.n] = callback
        return add(table.unpack(args, 1, args.n))
    end
    mp[pair[2]] = function(fn)
        local result = remove(wrapped[fn] or fn)
        wrapped[fn] = nil
        return result
    end
end
for _, name in ipairs({'add_timeout','add_periodic_timer'}) do
    local add = mp[name]
    mp[name] = function(seconds, fn, ...) return add(seconds, guarded(fn), ...) end
end
-- Status is refreshed only on the host's slow probe. Never publish for every timer,
-- property callback or error: scripts observing user-data could otherwise self-trigger.
register_message('zavro_custom_status', function(session)
    if session == epoch then publish() end
end)
local fn
if mp.get_script_name() ~= expected_client then report.conflict = true
else fn = loadfile(source_path) end
if report.conflict then -- never execute under a different/colliding native client name
elseif not fn then report.syntax = true
else
    local ok = pcall(fn)
    if ok then report.loaded = true
    else report.runtime = true; report.errors = report.errors + 1 end
end
publish()
