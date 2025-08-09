-- Простой тест RAM API для проверки исправлений
-- Этот скрипт должен показывать корректные значения

print("=== Simple RAM API Test ===")

-- Проверяем доступность API
if not tablet or not tablet.ram then
    print("ERROR: tablet.ram API not available!")
    return
end

-- Получаем общий размер памяти
local totalRam = tablet.ram.getTotalSize()
print("Total RAM: " .. totalRam .. " KB")

-- Получаем текущее использование памяти
local usedRam = tablet.ram.getUsedSize()
print("Used RAM: " .. math.floor(usedRam) .. " KB")

-- Проверяем корректность данных
if totalRam > 0 and usedRam >= 0 and usedRam <= totalRam then
    local freeRam = totalRam - usedRam
    local usagePercent = (usedRam / totalRam) * 100

    print("Free RAM: " .. math.floor(freeRam) .. " KB")
    print("Usage: " .. string.format("%.1f", usagePercent) .. "%")
    print("Test PASSED - Data is valid!")
else
    print("Test FAILED - Invalid data!")
    print("Total: " .. tostring(totalRam) .. " KB")
    print("Used: " .. tostring(usedRam) .. " KB")
end

print("=== Test Complete ===")
