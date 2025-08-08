-- =======================================================
-- LoraCore Recovery v20.0 - Final pcall fix
-- =======================================================
local term = tablet.terminal
local fs = _G.fs
local os = _G.os
local colors = _G.colors
local bios = _G.bios
local gpu = tablet.gpu

os.sleep(0)

if fs.exists("/boot.lua") then
    local boot_script = fs.read("/boot.lua")
    if boot_script then
        -- ИСПРАВЛЕНИЕ: Правильная и безопасная загрузка и запуск
        local compiled_func, err = load(boot_script, "/boot.lua", "t", _G)
        if compiled_func then
            pcall(compiled_func) -- Просто безопасно запускаем
        else
            -- Этот код выполнится, если boot.lua поврежден
            gpu.fill(0, 0, 480, 270, colors.red)
            gpu.drawText(10, 10, "FATAL: /boot.lua is corrupted!", colors.white)
            gpu.drawText(10, 22, "Error: " .. tostring(err), colors.white)
            os.sleep(5)
        end
    end
    os.reboot()
    return
end

-- =======================================================
-- Код меню установки (остается без изменений)
-- =======================================================
local W, H = 480, 270
local state = "menu"
local input_line = ""
local cursor_blink = true
local last_blink_time = os.time()
local prompt_message = ""

local function draw_window(title)
    gpu.fill(0, 0, W, H, colors.gray)
    gpu.fill(10, 10, W - 20, H - 20, colors.blue)
    gpu.fill(10, 10, W - 20, 20, colors.black)
    gpu.drawText(math.floor((W - (#title * 7)) / 2), 16, title, colors.white)
end

local function draw_generic_prompt()
    draw_window(" LoraCore Recovery ")
    gpu.drawText(30, 80, prompt_message, colors.white)
    gpu.drawText(30, 100, "Press any key to continue...", colors.white)
end

local function draw_menu()
    draw_window(" LoraCore Recovery ")
    local menu_items = { "ModuOS not found.", "Please select an option:", "", "[1] Install ModuOS (LUA)", "[2] Create Java Boot File Stub", "[3] Reboot", "[4] Shutdown" }
    for i, line in ipairs(menu_items) do gpu.drawText(25, 40 + (i * 12), line, colors.white) end
    local input_y = 40 + (#menu_items * 12) + 24
    gpu.drawText(25, input_y, "> " .. input_line, colors.white)
    if os.time() - last_blink_time > 0.5 then cursor_blink = not cursor_blink; last_blink_time = os.time() end
    if cursor_blink then gpu.fill(25 + (#input_line + 2) * 7, input_y, 7, 9, colors.white) end
end

-- Главный цикл
while true do
    if state == "menu" then draw_menu()
    elseif state == "reboot_prompt" or state == "shutdown" then draw_generic_prompt()
    end

    local event, p1 = os.pullEvent()

    if state == "menu" then
        if event == "char" then input_line = input_line .. p1
        elseif event == "key" then
            if p1 == 259 and #input_line > 0 then input_line = input_line:sub(1, -2)
            elseif p1 == 257 then
                local choice = input_line; input_line = ""
                if choice == "1" then state = "installing"
                elseif choice == "2" then state = "creating_stub"
                elseif choice == "3" then os.reboot(); break
                elseif choice == "4" then state = "shutdown"; prompt_message = "System halted."
                end
            end
        end
    elseif state == "reboot_prompt" then if event == "key" then os.reboot(); break end
    end

    if state == "installing" then
        term.clear()
        local installer_script = bios.getInstaller()
        local installer_func, err = installer_script and load(installer_script, "installer", "t", _G)
        if installer_func then pcall(installer_func) end
        prompt_message = "ModuOS has been installed."
        state = "reboot_prompt"
    elseif state == "creating_stub" then
        fs.makeDir("/boot"); fs.write("/boot/kernel.jar", "Placeholder")
        prompt_message = "/boot/kernel.jar created."
        state = "reboot_prompt"
    end
end