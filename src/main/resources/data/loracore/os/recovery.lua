-- =======================================================
-- LoraCore Recovery v24.0 - С корректным запуском установщика
-- =======================================================
-- Описание: Эта версия вызывает внешний скрипт установщика
-- и правильно обрабатывает результат его работы.
-- =======================================================

-- --- Глобальные переменные и API ---
local fs = _G.fs
local os = _G.os
local gpu = tablet.gpu
local colors = _G.colors

-- --- Константы и состояние ---
local W, H = 480, 270
local state = "menu"
local prompt_message = ""

-- --- Коды клавиш GLFW ---
local KEY_1, KEY_2, KEY_3, KEY_4, KEY_5, KEY_6 = 49, 50, 51, 52, 53, 54
local KEY_Y, KEY_N = 89, 78

-- =======================================================
-- Функции отрисовки (UI)
-- =======================================================

local function draw_window(title)
    gpu.fill(0, 0, W, H, colors.blue)
    gpu.fill(10, 10, W - 20, H - 20, colors.black)
    gpu.fill(10, 10, W - 20, 25, colors.gray)
    gpu.drawText(math.floor((W - (#title * 7)) / 2), 15, title, colors.white)
end

local function draw_menu()
    draw_window(" LoraCore Recovery Environment ")
    local menu_items = {
        "Boot device not found or corrupted.",
        "Please select an option:", "",
        "[1] Install Simple Lua OS",
        "[2] Create Java Boot File Stub (Not Implemented)",
        "[3] Reboot",
        "[4] Shutdown"
    }
    for i, line in ipairs(menu_items) do
        gpu.drawText(25, 40 + (i * 12), line, colors.white)
    end
end

local function draw_confirm_install_screen()
    draw_window(" Confirm Installation ")
    gpu.drawText(30, 60, "This will format the disk and install a new OS.", colors.yellow)
    gpu.drawText(30, 80, "All data on the disk will be lost.", colors.red)
    gpu.drawText(30, 120, "Are you sure you want to continue? [Y/N]", colors.white)
end

local function draw_prompt_screen()
    draw_window(" System Message ")
    gpu.drawText(30, 80, prompt_message, colors.white)
    gpu.drawText(30, 120, "Press any key to return to menu...", colors.gray)
end

-- =======================================================
-- Функции-действия
-- =======================================================

local function action_install_os()
    draw_window(" Installing OS ")
    gpu.drawText(25, 40, "Loading installer from ROM...", colors.white)
    os.sleep(0.5)

    local installer_func, err = loadfile("/os/installer.txt")
    if not installer_func then
        prompt_message = "ERROR: Installer script is missing or corrupted!"
        state = "prompt"
        return
    end

    gpu.drawText(25, 60, "Running installer...", colors.white)
    os.sleep(0.5)

    -- pcall безопасно вызывает установщик
    local success, install_result = pcall(installer_func)

    if success and install_result then
        prompt_message = "Installation successful! Rebooting..."
        state = "prompt"
        os.sleep(3)
        os.reboot()
    else
        prompt_message = "Installation FAILED! See installer logs."
        state = "prompt"
    end
end

-- =======================================================
-- Главный цикл программы
-- =======================================================

while true do
    -- Шаг 1: Отрисовка
    if state == "menu" then
        draw_menu()
    elseif state == "install_confirm" then
        draw_confirm_install_screen()
    elseif state == "prompt" then
        draw_prompt_screen()
    end

    -- Шаг 2: Ожидание события
    local event, key_code = os.pullEvent("key")

    -- Шаг 3: Обработка события и смена состояния
    if state == "menu" then
        if key_code == KEY_1 then state = "install_confirm"
        elseif key_code == KEY_2 then
            prompt_message = "This feature is not yet implemented."
            state = "prompt"
        elseif key_code == KEY_3 then os.reboot()
        elseif key_code == KEY_4 then os.shutdown()
        end
    elseif state == "install_confirm" then
        if key_code == KEY_Y then
            action_install_os() -- Запускаем установку
        elseif key_code == KEY_N then
            state = "menu" -- Возвращаемся в меню
        end
    elseif state == "prompt" then
        state = "menu" -- Любая клавиша возвращает в меню
    end

    os.sleep(0.01)
end