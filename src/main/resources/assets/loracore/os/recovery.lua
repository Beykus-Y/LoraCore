-- =======================================================
-- LoraCore Recovery v16.0 - Driver and Timing Fix
-- =======================================================
local term = tablet.terminal
local fs = _G.fs
local os = _G.os
local colors = _G.colors
local bios = _G.bios

-- ИСПРАВЛЕНИЕ №1: Загружаем наш новый драйвер GPU
local gpu = require("drivers/gpu")

-- ИСПРАВЛЕНИЕ №2: Ждем один игровой тик.
-- Это решает проблему "гонки состояний", когда скрипт пытается рисовать
-- на еще не полностью инициализированном экране, что вызывало краш.
os.sleep(0)

-- Проверяем, существует ли ОС
if fs.exists("/boot.lua") then
    local boot_script = fs.read("/boot.lua")

    if boot_script then
        local os_func, err = load(boot_script, "/boot.lua", "t", _G)

        if os_func then
            -- Успех! Запускаем ОС.
            os_func()
            -- Если/когда ОС завершится, перезагружаемся.
            os.reboot()
        else
            -- Ошибка: файл не может быть скомпилирован (поврежден).
            term.clear()
            gpu.setTextColor(colors.red)
            term.print("FATAL: /boot.lua is corrupted!")
            term.print("Error: " .. tostring(err))
            os.sleep(5)
            os.reboot()
        end
    else
        -- Ошибка: файл существует, но не может быть прочитан.
        term.clear()
        gpu.setTextColor(colors.red)
        term.print("FATAL: Cannot read /boot.lua.")
        os.sleep(3)
        os.reboot()
    end

    -- В нормальном режиме до этой строки не дойдет.
    return
end

-- =======================================================
-- Меню установки (использует новый драйвер gpu)
-- =======================================================
local W, H = gpu.getResolution()

local function draw_window(title)
    gpu.setBackgroundColor(colors.gray); gpu.fill(1, 1, W, H, " ")
    gpu.setBackgroundColor(colors.blue); gpu.fill(2, 2, W - 2, H - 2, " ")
    gpu.setBackgroundColor(colors.black); gpu.fill(1, 1, W, 1, " ")
    gpu.setTextColor(colors.white); gpu.set(math.floor((W - #title) / 2) + 1, 1, title)
    gpu.setBackgroundColor(colors.blue)
end

local function show_menu()
    draw_window(" LoraCore Recovery ")
    gpu.setTextColor(colors.white)
    local menu_items = {"ModuOS not found.", "Please select an option:", "", " [1] Install ModuOS (LUA)", " [2] Switch to UEFI (JAVA) Boot", " [3] Reboot", " [4] Shutdown"}
    for i, line in ipairs(menu_items) do gpu.set(4, 3 + i, line) end
    term.setCursorPos(4, 12); term.write("> ")
end

local function run_installer()
    local installer_script = bios.getInstaller()
    if not installer_script then return false, "Installer script not found" end
    local installer_func, err = load(installer_script, "installer", "t", _G)
    if not installer_func then return false, "Failed to load installer: " .. tostring(err) end

    draw_window(" ModuOS Installation ")

    -- Безопасно запускаем установщик
    local success, message = pcall(installer_func)
    if not success then
        -- Если установщик упал, сообщаем об этом
        draw_window(" Installation Failed ")
        gpu.setTextColor(colors.red)
        gpu.set(3, 5, "An error occurred during installation:")
        gpu.set(3, 7, tostring(message))
        os.sleep(5)
    end
end

-- Главный цикл меню
while true do
    show_menu()
    local choice = term.read()
    if choice == "1" then
        run_installer()
        draw_window(" Installation Complete ")
        gpu.set(3, 5, "ModuOS has been installed.")
        gpu.set(3, 7, "Press any key to reboot.")
        os.pullEvent("key")
        os.reboot()
        break
    elseif choice == "2" then
        draw_window(" Switching Boot Mode ")
        fs.makeDir("/etc")
        fs.write("/etc/loracore.conf", "MODE=JAVA")
        gpu.set(3, 7, "Mode switched to JAVA.")
        gpu.set(3, 10, "Press any key to reboot.")
        os.pullEvent("key")
        os.reboot()
        break
    elseif choice == "3" then
        os.reboot()
        break
    elseif choice == "4" then
        term.clear(); term.print("System halted.")
        break
    end
end