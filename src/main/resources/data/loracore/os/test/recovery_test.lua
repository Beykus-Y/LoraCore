-- Простой тест для проверки работы recovery.lua
-- Этот скрипт должен запуститься и показать меню

print("=== Recovery Test ===")
print("Testing recovery.lua functionality...")

-- Проверяем доступность основных API
if not tablet then
    print("ERROR: tablet API not available!")
    return
end

if not tablet.gpu then
    print("ERROR: tablet.gpu API not available!")
    return
end

if not fs then
    print("ERROR: fs API not available!")
    return
end

if not os then
    print("ERROR: os API not available!")
    return
end

print("All APIs available. Starting recovery...")

-- Загружаем и запускаем recovery.lua
local recovery_path = "/os/recovery.lua"
if fs.exists(recovery_path) then
    print("Recovery script found. Loading...")
    
    local success, result = pcall(function()
        local recovery_script = fs.read(recovery_path)
        if recovery_script then
            local func = load(recovery_script, recovery_path, "t", _G)
            if func then
                return func()
            else
                error("Failed to compile recovery script")
            end
        else
            error("Failed to read recovery script")
        end
    end)
    
    if success then
        print("Recovery script executed successfully")
    else
        print("ERROR: Recovery script failed: " .. tostring(result))
    end
else
    print("ERROR: Recovery script not found at " .. recovery_path)
end

print("=== Test Complete ===")
