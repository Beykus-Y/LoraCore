-- Memory library for LoraCore OS
-- Предоставляет удобные функции для работы с памятью

local memory = {}

-- Получить общий размер памяти в KB
function memory.getTotalKB()
    return tablet.ram.getTotalSize()
end

-- Получить использованную память в KB  
function memory.getUsedKB()
    return tablet.ram.getUsedSize()
end

-- Получить свободную память в KB
function memory.getFreeKB()
    return memory.getTotalKB() - memory.getUsedKB()
end

-- Получить процент использования памяти (0-100)
function memory.getUsagePercent()
    local total = memory.getTotalKB()
    local used = memory.getUsedKB()
    return (used / total) * 100
end

-- Проверить, достаточно ли свободной памяти
function memory.hasEnoughFree(requiredKB)
    return memory.getFreeKB() >= requiredKB
end

-- Получить отформатированную строку с информацией о памяти
function memory.getStatusString()
    local used = math.floor(memory.getUsedKB())
    local total = memory.getTotalKB()
    local percent = math.floor(memory.getUsagePercent())
    return used .. "/" .. total .. " KB (" .. percent .. "%)"
end

-- Принудительно запустить сборщик мусора и вернуть освобожденную память
function memory.forceGC()
    local beforeGC = memory.getUsedKB()
    collectgarbage("collect")
    local afterGC = memory.getUsedKB()
    return beforeGC - afterGC -- возвращаем количество освобожденной памяти
end

-- Вывести детальную информацию о памяти
function memory.printStatus()
    print("=== Memory Status ===")
    print("Total: " .. memory.getTotalKB() .. " KB")
    print("Used:  " .. math.floor(memory.getUsedKB()) .. " KB")
    print("Free:  " .. math.floor(memory.getFreeKB()) .. " KB")
    print("Usage: " .. math.floor(memory.getUsagePercent()) .. "%")
end

-- Предупреждение о нехватке памяти
function memory.checkLowMemory(warningThresholdPercent)
    warningThresholdPercent = warningThresholdPercent or 90
    local usage = memory.getUsagePercent()
    
    if usage >= warningThresholdPercent then
        print("WARNING: High memory usage (" .. math.floor(usage) .. "%)!")
        return true
    end
    return false
end

return memory
