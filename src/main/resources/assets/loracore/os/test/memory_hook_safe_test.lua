-- Безопасный тест нового механизма контроля памяти
-- Этот скрипт должен работать нормально, не превышая лимиты памяти

print("=== Safe Memory Hook Test ===")
print("Этот тест проверяет работу системы контроля памяти в нормальных условиях")

-- Получаем информацию о памяти
if tablet and tablet.ram then
    local totalRam = tablet.ram.getTotalSize()
    print("Total RAM: " .. totalRam .. " KB")
else
    print("ERROR: tablet.ram API not available!")
    return
end

-- Создаем небольшой массив для тестирования
local testArray = {}
local maxItems = 100 -- Безопасное количество элементов

print("Создаем " .. maxItems .. " элементов для тестирования...")

for i = 1, maxItems do
    testArray[i] = "Test item " .. i
    
    -- Периодически проверяем использование памяти
    if i % 20 == 0 then
        local usedRam = tablet.ram.getUsedSize()
        print("Item " .. i .. " - RAM usage: " .. math.floor(usedRam) .. " KB")
        
        -- Проверяем, что использование памяти разумное
        if usedRam > totalRam * 0.8 then
            print("WARNING: Memory usage is getting high!")
            break
        end
    end
end

print("Тест завершен успешно!")
print("Система контроля памяти работает корректно в нормальных условиях")

-- Очищаем память
testArray = nil
collectgarbage("collect")

print("Memory cleaned up")
print("=== Test Complete ===")
