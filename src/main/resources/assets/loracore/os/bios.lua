-- =======================================================
-- LoraBIOS v2.1 - Corrected Bootloader
-- =======================================================
-- Описание: Улучшенный BIOS с имитацией Power-On Self-Test,
-- отображением статусов проверки оборудования и более
-- реалистичной задержкой загрузки.
-- Исправлена обработка сигнала перезагрузки из recovery.
-- =======================================================

-- Локальные переменные для производительности и удобства
local fs = _G.fs
local os = _G.os
local gpu = tablet.gpu
local colors = _G.colors

-- --- Константы для отображения ---
local W, H = 480, 270 -- Размеры экрана
local PADDING = 10
local LINE_HEIGHT = 12
local CHAR_WIDTH = 7
local current_line = 1

-- --- Вспомогательные функции для красивого вывода ---

-- Печатает строку на определенной "консольной" строке (не пиксельной Y)
local function print_line(line_num, text, color)
    local y = PADDING + (line_num * LINE_HEIGHT)
    gpu.drawText(PADDING, y, text, color or colors.white)
end

-- Печатает статус операции (например, "[  OK  ]") справа на экране
local function print_status(line_num, text, status_text, status_color)
    local y = PADDING + (line_num * LINE_HEIGHT)
    -- Сначала печатаем основной текст
    gpu.drawText(PADDING, y, text, colors.white)

    -- Затем вычисляем позицию и печатаем статус справа
    local status_str = "[  " .. status_text .. "  ]"
    local status_x = W - PADDING - (#status_str * CHAR_WIDTH)
    gpu.drawText(status_x, y, status_str, status_color)
end


-- --- Основная логика BIOS ---
local function main()
    -- 1. Начальная очистка и заголовок
    gpu.fill(0, 0, W, H, colors.black)
    gpu.drawText(PADDING, PADDING, "LoraBIOS v2.1", colors.gray)
    gpu.fill(PADDING, PADDING + LINE_HEIGHT, W - (PADDING * 2), 1, colors.gray)
    os.sleep(0.5)

    current_line = 2

    -- 2. Симуляция POST (Power-On Self-Test)
    print_line(current_line, "Initializing Power-On Self-Test...", colors.white)
    current_line = current_line + 1
    os.sleep(0.75)

    -- Проверка CPU
    print_status(current_line, "CPU             :", "OK", colors.lime)
    print_line(current_line + 1, "  > LoraChip L-4o @ 25 MHz", colors.gray)
    current_line = current_line + 2
    os.sleep(0.75)

    -- Проверка RAM (с анимацией)
    print_line(current_line, "RAM Check       :", colors.white)
    local total_ram_kb = 512 -- Значение по умолчанию
    if tablet and tablet.ram then
        local success, ram_size = pcall(function() return tablet.ram.getTotalSize() end)
        if success and ram_size and ram_size > 0 then
            total_ram_kb = ram_size
        end
    end

    for i = 0, total_ram_kb, 32 do
        local progress_text = string.format("%d KB OK", i)
        -- Очищаем область для текста и рисуем новый
        gpu.fill(PADDING + 18 * CHAR_WIDTH, PADDING + (current_line * LINE_HEIGHT), 150, LINE_HEIGHT, colors.black)
        gpu.drawText(PADDING + 18 * CHAR_WIDTH, PADDING + (current_line * LINE_HEIGHT), progress_text, colors.white)
        os.sleep(0.01) -- Маленькая задержка для эффекта "бегущих цифр"
    end
    -- Финальный статус OK
    print_status(current_line, "RAM Check       :", "OK", colors.lime)
    current_line = current_line + 2
    os.sleep(0.5)

    -- Проверка VFS (Virtual File System)
    print_status(current_line, "VFS             :", fs.exists("/") and "OK" or "FAIL", fs.exists("/") and colors.lime or colors.red)
    current_line = current_line + 2
    os.sleep(1)

    -- 3. Поиск загрузочного устройства
    print_line(current_line, "Scanning for bootable kernel...", colors.white)
    os.sleep(1.5)
    current_line = current_line + 1

    if fs.exists("/boot/kernel.jar") then
        print_line(current_line, " > Java Kernel found at /boot/kernel.jar", colors.lime)
        os.sleep(0.5)
        current_line = current_line + 2
        print_line(current_line, "Handing over control to Kernel...", colors.white)
        os.sleep(1)
        os.boot_java("/boot/kernel.jar")
    else
        print_line(current_line, " > Java Kernel not found. Checking for recovery...", colors.yellow)
        os.sleep(0.5)
        current_line = current_line + 2
        print_line(current_line, "Starting LoraCore Recovery Environment...", colors.white)
        os.sleep(1)

        -- ИСПРАВЛЕННЫЙ БЛОК ЗАГРУЗКИ RECOVERY
        -- Загружаем скрипт восстановления напрямую.
        local recovery_func, err_msg = loadfile("/os/recovery.lua", "t", _G)

        if recovery_func then
            -- Если функция загружена, просто вызываем ее.
            -- Больше нет pcall, который ловил бы RebootSignal.
            recovery_func()
        else
            -- Если файл recovery.lua не найден или содержит синтаксическую ошибку
            gpu.fill(0,0, W, H, colors.red)
            gpu.drawText(10, 10, "CRITICAL: Recovery script is missing or corrupted!", colors.white)
            gpu.drawText(10, 25, tostring(err_msg), colors.white)
            os.sleep(5)
            os.reboot()
        end
    end
end

-- Запускаем основную функцию
main()