-- =======================================================
-- LoraCore Tablet Recovery & Boot Manager v12.0 (Final Corrected)
-- =======================================================
-- Эта версия использует ЯВНЫЙ `coroutine.yield()` для асинхронных операций
-- и НЕ ИСПОЛЬЗУЕТ `pcall` на верхнем уровне, так как он мешает `yield`.
-- Критические ошибки будут пойманы Java-раннером.

-- Шаг 1: Проверяем наличие всех необходимых API
if not (tablet and tablet.terminal and tablet.gpu and _G.coroutine and _G.fs and _G.os and _G.colors and _G.bios) then
    print("FATAL: Core APIs are missing.")
    return -- Прерываем выполнение скрипта
end

-- Шаг 2: Создаем локальные псевдонимы для удобства
local term = tablet.terminal
local gpu = tablet.gpu
local coroutine = _G.coroutine
local fs = _G.fs
local os = _G.os
local colors = _G.colors
local bios = _G.bios

-- =======================================================
--                      ГЛАВНАЯ ЛОГИКА ЗАГРУЗКИ
-- =======================================================

-- Шаг 3: Асинхронно проверяем, существует ли файл ОС, и ЯВНО ждем ответа
local os_boot_file_exists = coroutine.yield(fs.exists("/boot.lua"))

if os_boot_file_exists then
    -- ===================================
    -- БЛОК ЗАГРУЗКИ СУЩЕСТВУЮЩЕЙ ОС
    -- ===================================
    -- Асинхронно читаем файл ОС и ЯВНО ждем ответа
    local boot_script, read_err = coroutine.yield(fs.read("/boot.lua"))
    if not boot_script then
        term.clear()
        term.write("FATAL: Could not read /boot.lua: " .. tostring(read_err))
        return
    end

    -- Загружаем скрипт в память (это синхронная операция)
    local os_func, load_err = load(boot_script, "/boot.lua", "t", _G)
    if not os_func then
        term.clear()
        term.write("FATAL: /boot.lua is corrupted: " .. tostring(load_err))
        return
    end

    -- Запускаем ОС. Если внутри нее произойдет ошибка, ее поймает Java-код.
    os_func()

else
    -- ===================================
    -- БЛОК РЕЖИМА ВОССТАНОВЛЕНИЯ (RECOVERY)
    -- ===================================
    local W, H = gpu.getResolution()

    -- Вспомогательные функции для отрисовки интерфейса
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
        local menu_items = {"ModuOS not found.", "Please select an option:", "", " [1] Install ModuOS", " [2] Reboot", " [3] Shutdown"}
        for i, line in ipairs(menu_items) do gpu.set(4, 3 + i, line) end
        term.setCursorPos(4, 11); term.write("> ")
    end

    -- Функция для запуска установщика
    local function run_installer()
        local installer_script = bios.getInstaller()
        if not installer_script then return false, "Installer script not found" end

        local installer_func, err = load(installer_script, "installer", "t", _G)
        if not installer_func then return false, "Failed to load installer: " .. tostring(err) end

        draw_window(" ModuOS Installation ")

        -- Используем pcall здесь ЛОКАЛЬНО, чтобы ошибка в установщике не сломала меню.
        -- Поскольку установщик сам делает yield, мы должны запустить его как корутину.
        local installer_co = coroutine.create(installer_func)
        while coroutine.status(installer_co) ~= "dead" do
            local ok, result = coroutine.resume(installer_co)
            if not ok then
                -- Ошибка внутри корутины установщика
                return false, result
            end
            if coroutine.status(installer_co) ~= "dead" then
                -- Установщик уснул, ждем события и передаем его дальше
                coroutine.yield(result)
            end
        end
        return true
    end

    -- Главный цикл меню восстановления
    while true do
        show_menu()
        local choice = term.read() -- term.read() - блокирующий, ему не нужен yield
        if choice == "1" then
            local ok, err = run_installer()
            if ok then
                draw_window(" Installation Successful ")
                gpu.setTextColor(colors.lime); gpu.set(3, 5, "ModuOS has been installed.")
                gpu.setTextColor(colors.white); gpu.set(3, 7, "Press any key to reboot.")
                os.pullEvent("key"); os.reboot()
            else
                draw_window(" Installation Failed ")
                gpu.setTextColor(colors.red); gpu.set(3, 4, "Installation failed.")
                gpu.setTextColor(colors.white); gpu.set(3, 6, "Error: " .. tostring(err)); os.pullEvent("key")
            end
        elseif choice == "2" then
            os.reboot(); break
        elseif choice == "3" then
            term.clear(); term.write("System halted."); break
        end
    end
end