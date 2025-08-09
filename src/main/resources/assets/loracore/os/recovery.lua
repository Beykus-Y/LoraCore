-- =======================================================
-- LoraCore Recovery v23.0 - Полная исправленная версия
-- =======================================================
-- Описание: Эта версия содержит все необходимые функции,
-- исправленную систему событий и корректный запуск
-- дочерних скриптов с обновлением экрана.
-- =======================================================

-- --- Глобальные переменные и API ---
local term = tablet.terminal
local fs = _G.fs
local os = _G.os
local colors = _G.colors
local bios = _G.bios
local gpu = tablet.gpu

-- --- Константы и состояние ---
local W, H = 480, 270
local state = "menu"
local prompt_message = ""
local input_line = ""

-- --- Коды клавиш GLFW ---
local KEY_1 = 49
local KEY_2 = 50
local KEY_3 = 51
local KEY_4 = 52
local KEY_5 = 53
local KEY_6 = 54
local KEY_ENTER = 257
local KEY_BACKSPACE = 259

-- =======================================================
-- Функции отрисовки (UI)
-- =======================================================

-- Рисует базовое окно с заголовком
local function draw_window(title)
    gpu.fill(0, 0, W, H, colors.blue)
    gpu.fill(10, 10, W - 20, H - 20, colors.black)
    gpu.fill(10, 10, W - 20, 25, colors.gray)
    gpu.drawText(math.floor((W - (#title * 7)) / 2), 15, title, colors.white)
end

-- Рисует главное меню
local function draw_menu()
    draw_window(" LoraCore Recovery Environment ")
    local menu_items = {
        "Boot device not found or corrupted.",
        "Please select an option:",
        "",
        "[1] Install ModuOS (Minimal LUA OS)",
        "[2] Create Java Boot File Stub",
        "[3] Full RAM & Memory API Test",
        "[4] Simple RAM Test",
        "[5] Reboot",
        "[6] Shutdown"
    }
    for i, line in ipairs(menu_items) do
        gpu.drawText(25, 40 + (i * 12), line, colors.white)
    end
end

-- Рисует экран с сообщением
local function draw_prompt_screen()
    draw_window(" System Message ")
    gpu.drawText(30, 80, prompt_message, colors.white)
    gpu.drawText(30, 120, "Press any key to return to menu...", colors.gray)
end


-- =======================================================
-- Функции-действия (Сохранены в полном объеме)
-- =======================================================

-- Установка минимальной ОС
local function action_install_moduos()
    gpu.fill(0,0,W,H,colors.black)
    gpu.drawText(10, 10, "=== Installing ModuOS ===", colors.white)

    local installer_script, err = loadfile("/os/installer.txt")
    if not installer_script then
        gpu.drawText(10, 30, "ERROR: Installer script not found or corrupted!", colors.red)
        gpu.drawText(10, 42, tostring(err), colors.red)
        os.sleep(5)
        state = "menu" -- Возвращаемся в меню
        return
    end

    local success, result = pcall(installer_script)

    if success and result then
        gpu.drawText(10, 200, "Installation successful! Rebooting...", colors.green)
        os.sleep(2)
        os.reboot()
    else
        gpu.drawText(10, 200, "Installation FAILED! Check logs.", colors.red)
        gpu.drawText(10, H - 20, "Press any key to return to menu...", colors.gray)
        state = "wait_for_key" -- Ждем нажатия
    end
end

-- Создание заглушки для Java-ядра
local function action_create_java_stub()
    prompt_message = "This feature is not yet implemented."
    state = "prompt"
end

-- Тестирование RAM
local function action_test_ram()
    local test_script, err = loadfile("/os/test/ram_test.lua")
    if not test_script then
        prompt_message = "ERROR: RAM test script not found!"
        state = "prompt"
        return
    end

    gpu.fill(0,0,W,H,colors.black) -- Очищаем экран ПЕРЕД тестом
    pcall(test_script)
    gpu.drawText(10, H - 20, "Test finished. Press any key to return to menu...", colors.gray)
    state = "wait_for_key"
end

-- Простой тест RAM
local function action_simple_ram_test()
    local test_script, err = loadfile("/os/test/simple_ram_test.lua")
    if not test_script then
        prompt_message = "ERROR: Simple RAM test script not found!"
        state = "prompt"
        return
    end

    gpu.fill(0,0,W,H,colors.black) -- Очищаем экран ПЕРЕД тестом
    pcall(test_script)
    gpu.drawText(10, H - 20, "Test finished. Press any key to return to menu...", colors.gray)
    state = "wait_for_key"
end


-- =======================================================
-- Главный цикл программы
-- =======================================================

-- Проверяем, есть ли уже установленная ОС
if fs.exists("/boot.lua") then
    local boot_script = fs.read("/boot.lua")
    if boot_script then pcall(load(boot_script, "/boot.lua", "t", _G)) end
    os.reboot()
    return
end

-- Основной цикл рекавери
while true do
    -- ШАГ 1: Отрисовка интерфейса в зависимости от текущего состояния
    if state == "menu" then
        draw_menu()
    elseif state == "prompt" then
        draw_prompt_screen()
    end

    -- ШАГ 2: Ожидаем событие от пользователя (это приостанавливает цикл)
    local event, param1 = os.pullEvent()

    -- ШАГ 3: Обрабатываем событие и меняем состояние для СЛЕДУЮЩЕЙ итерации
    if event == "key" then
        if state == "menu" then
            -- Выполняем действия в зависимости от нажатой клавиши
            if param1 == KEY_1 then action_install_moduos()
            elseif param1 == KEY_2 then action_create_java_stub()
            elseif param1 == KEY_3 then action_test_ram()
            elseif param1 == KEY_4 then action_simple_ram_test()
            elseif param1 == KEY_5 then os.reboot()
            elseif param1 == KEY_6 then os.shutdown()
            end
        elseif state == "wait_for_key" or state == "prompt" then
            -- Если мы были в состоянии ожидания, любое нажатие возвращает нас в меню
            state = "menu"
        end
    end

    -- ШАГ 4: Небольшая задержка для снижения нагрузки на CPU
    os.sleep(0.01)
end