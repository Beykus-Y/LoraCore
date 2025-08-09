-- Простой тест RAM API для проверки исправлений
-- Этот скрипт должен показывать корректные значения

-- ИСПРАВЛЕНИЕ: Показываем динамический размер RAM в заголовке
local header_text = "=== Simple RAM API Test ==="
if tablet and tablet.ram then
    local success, ram_size = pcall(function() return tablet.ram.getTotalSize() end)
    if success and ram_size and ram_size > 0 then
        header_text = string.format("=== Simple RAM API Test (%d KB) ===", ram_size)
    end
end
print(header_text)

-- Получаем общий размер памяти
local totalRam = tablet.ram.getTotalSize()
print("Total RAM: " .. totalRam .. " KB")

-- Получаем текущее использование памяти
local usedRam = tablet.ram.getUsedSize()
print("Used RAM: " .. math.floor(usedRam) .. " KB")

-- ИСПРАВЛЕНИЕ: Проверяем корректность данных перед вычислениями
if totalRam > 0 and usedRam >= 0 and usedRam <= totalRam then
    -- Вычисляем свободную память
    local freeRam = totalRam - usedRam
    print("Free RAM: " .. math.floor(freeRam) .. " KB")
    
    -- Вычисляем процент использования
    local usagePercent = (usedRam / totalRam) * 100
    print("Usage: " .. string.format("%.1f", usagePercent) .. "%")
    
    print("\n=== Memory Stress Test ===")
    print("Creating large table to test memory limits...")
    
    local bigTable = {}
    local success = true
    
    for i = 1, 1000 do
        bigTable[i] = {}
        for j = 1, 100 do
            bigTable[i][j] = "This is a test string " .. i .. "-" .. j
        end
        
        -- Проверяем использование памяти каждые 100 итераций
        if i % 100 == 0 then
            local currentUsed = tablet.ram.getUsedSize()
            print("After " .. i .. " iterations: " .. math.floor(currentUsed) .. " KB used")
            
            -- Проверяем, не превысили ли лимит памяти
            if currentUsed > totalRam then
                print("WARNING: Memory limit exceeded! Stopping test.")
                success = false
                break
            end
            
            -- Принудительно запускаем сборщик мусора
            collectgarbage("collect")
            
            -- Небольшая пауза для проверки ограничений
            os.sleep(0.1)
        end
    end

    if success then
        print("Stress test complete!")
        print("Final used RAM: " .. math.floor(tablet.ram.getUsedSize()) .. " KB")
    end
else
    print("ERROR: Invalid memory data!")
    print("Total: " .. tostring(totalRam) .. " KB, Used: " .. tostring(usedRam) .. " KB")
    print("Data validation failed!")
end
