-- Тест нового механизма контроля памяти через Lua debug hooks
-- Этот скрипт должен быть остановлен системой контроля памяти

print("=== Memory Hook Test ===")
print("Этот тест проверяет работу нового механизма контроля памяти")
print("Скрипт будет намеренно потреблять много памяти и должен быть остановлен")

-- Получаем информацию о памяти
if tablet and tablet.ram then
    local totalRam = tablet.ram.getTotalSize()
    print("Total RAM: " .. totalRam .. " KB")
    print("Начинаем потребление памяти...")
else
    print("ERROR: tablet.ram API not available!")
    return
end

-- Создаем массив для потребления памяти
local memoryHog = {}
local counter = 0

print("Начинаем цикл потребления памяти...")
print("Система должна остановить выполнение через несколько секунд")

-- Бесконечный цикл, который будет потреблять память
while true do
    counter = counter + 1
    
    -- Добавляем данные в массив для потребления памяти
    memoryHog[counter] = string.rep("X", 1000) -- 1KB строки
    
    -- Периодически выводим информацию
    if counter % 1000 == 0 then
        print("Добавлено " .. counter .. " элементов (" .. counter .. " KB)")
        
        -- Проверяем текущее использование памяти
        if tablet and tablet.ram then
            local usedRam = tablet.ram.getUsedSize()
            print("Current RAM usage: " .. math.floor(usedRam) .. " KB")
        end
    end
    
    -- Небольшая задержка, чтобы не перегружать систему
    os.sleep(0.01)
end

-- Этот код никогда не должен выполниться
print("ERROR: Цикл завершился, но должен был быть остановлен системой контроля памяти!")
