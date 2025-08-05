-- ===============================================
-- LoraCore Tablet BIOS v1.2 - Enhanced & Resilient
-- ===============================================

-- === Константы и конфигурация BIOS ===
local BIOS_VERSION = "1.2"
local OS_BOOT_FILE = "/boot.lua"
local CRASH_LOG_PATH = "/last_crash.log"

-- === Вспомогательные функции ===

--- Логирует ошибку в файл.
-- @param err (any) Ошибка для записи в лог.
local function logCrash(err)
    local log, open_err = fs.open(CRASH_LOG_PATH, "w")
    if log then
        log.write("BIOS v" .. BIOS_VERSION .. " Crash Report:\n\n")
        log.write(tostring(err))
        log.close()
    end
end

--- Показывает "анимацию" процесса.
-- @param message (string) Сообщение для отображения.
local function show_progress(message)
    term.write(message)
    for _ = 1, 3 do
        -- В нашей среде нет os.sleep(), поэтому просто имитируем
        -- небольшую задержку через неблокирующий pullEvent, если он будет доступен,
        -- или просто выводим точки.
        term.write(".")
    end
    term.print("") -- Перевод строки
end

-- === Функции режимов работы BIOS ===

--- Основная процедура загрузки операционной системы.
-- @return boolean, any Статус успеха и результат (nil или сообщение об ошибке).
local function boot_os()
    term.clear()
    term.setCursorPos(1, 1)
    show_progress("LoraCore BIOS v" .. BIOS_VERSION .. " initializing")

    if not fs.exists(OS_BOOT_FILE) then
        return false, "Boot file not found at " .. OS_BOOT_FILE
    end

    show_progress("Boot file found, reading")
    local boot_script_content, read_err = fs.read(OS_BOOT_FILE)

    if not boot_script_content then
        local err_msg = "Failed to read " .. OS_BOOT_FILE .. ": " .. tostring(read_err or "file is empty")
        return false, err_msg
    end

    show_progress("Validating boot script")
    -- Компилируем прочитанную СТРОКУ с помощью load(), а не loadfile()
    -- Последний аргумент _G указывает, что скрипт будет выполняться в глобальном окружении.
    local func, loadErr = load(boot_script_content, OS_BOOT_FILE, "t", _G)

    if not func then
        return false, "Boot script is corrupted or has syntax errors: " .. tostring(loadErr)
    end

    show_progress("Starting ModuOS")
    term.clear()
    term.setCursorPos(1, 1)

    -- pcall выполнит func() и перехватит любую ошибку времени выполнения.
    local ok, err = pcall(func)

    if not ok then
        -- Ошибка произошла ВНУТРИ операционной системы во время ее загрузки.
        return false, err
    else
        -- Операционная система завершила свою работу штатно (например, через команду shutdown).
        term.clear()
        term.setCursorPos(1, 1)
        term.print("System halted.")
        return true
    end
end

--- Запускает установщик ОС.
local function run_installer()
    term.clear()
    term.setCursorPos(1, 1)
    show_progress("Loading installer")
    local installer_script = bios.getInstaller()

    if installer_script then
        local installer_func, err = load(installer_script, "installer.txt")
        if installer_func then
            local ok, install_err = pcall(installer_func)
            if not ok then
                debug.printError("\n[FATAL] Installation script crashed:")
                debug.printError(tostring(install_err))
                logCrash("Installer crashed: " .. tostring(install_err))
            end
        else
            debug.printError("Failed to load installer script: " .. tostring(err))
        end
    else
        debug.printError("Could not retrieve installer script from BIOS.")
    end

    term.print("\nPress any key to return to the recovery menu.")
    os.pullEvent("key")
end

--- Показывает содержимое последнего лога крушения.
local function view_log()
    term.clear()
    term.setCursorPos(1, 1)
    term.print("--- Last Crash Log (" .. CRASH_LOG_PATH .. ") ---\n\n")

    if fs.exists(CRASH_LOG_PATH) then
        local content = fs.read(CRASH_LOG_PATH)
        term.print(content or "Log file is empty.")
    else
        term.print("No crash log found.")
    end

    term.print("\n\n--------------------------------------")
    term.print("\nPress any key to return to the menu.")
    os.pullEvent("key")
end

--- Главное меню восстановления системы.
-- @param last_error (any, optional) Последняя ошибка, приведшая к входу в этот режим.
local function recovery_mode(last_error)
    while true do
        term.clear()
        term.setCursorPos(1, 1)
        term.print("--- LoraCore Recovery ---\n")
        term.print("BIOS Version: " .. BIOS_VERSION .. "\n\n")

        if last_error then
            -- Выводим ошибку красным цветом для наглядности
            debug.printError("Last error: " .. tostring(last_error))
            term.print("\n")
            -- После первого показа очищаем ошибку, чтобы не мешала.
            last_error = nil
        end

        term.print("1. Try to boot again\n")
        term.print("2. Re-install ModuOS\n")
        term.print("3. View last crash log\n") -- НОВЫЙ ПУНКТ
        term.print("4. Shutdown\n\n")
        term.write("> ")

        local choice = term.read()
        if choice then
            -- Очищаем ввод от пробелов и переводим в нижний регистр
            choice = choice:gsub("%s+", ""):lower()

            if choice == "1" then
                os.reboot()
            elseif choice == "2" then
                run_installer()
                -- После установки цикл продолжится, и меню отобразится снова
            elseif choice == "3" then
                view_log()
            elseif choice == "4" then
                term.clear()
                term.print("System halted by user.")
                break -- Выход из цикла и завершение работы BIOS
            else
                term.print("\nInvalid option. Press any key to continue.")
                os.pullEvent("key")
            end
        end
    end
end

-- ===============================================
--               ГЛАВНЫЙ ПОТОК BIOS
-- ===============================================

-- Оборачиваем всю логику в pcall для максимальной отказоустойчивости.
-- Если даже сам BIOS сломается, мы хотя бы увидим ошибку.
local bios_ok, bios_result = pcall(function()
    local boot_ok, result = boot_os()
    if not boot_ok then
        -- Если загрузка не удалась, логируем ошибку и уходим в режим восстановления.
        logCrash(result)
        term.print("\nPress any key to enter Recovery Mode.")
        os.pullEvent("key")
        recovery_mode(result)
    end
end)

if not bios_ok then
    -- Этот блок сработает только в случае КРИТИЧЕСКОЙ ошибки в самом BIOS.
    term.clear()
    term.setCursorPos(1,1)
    debug.printError("!!! BIOS KERNEL PANIC !!!")
    debug.printError("This is a critical error within the BIOS itself.")
    debug.printError(tostring(bios_result))
end