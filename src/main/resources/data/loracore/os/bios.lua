-- LoraBIOS v3.0
local fs = _G.fs
local os = _G.os
local gpu = tablet.gpu
local colors = _G.colors

local W, H = 480, 270
local PADDING, LINE_HEIGHT, CHAR_W = 10, 12, 7
local line = 1

local function print_line(n, txt, color)
  gpu.drawText(PADDING, PADDING + n * LINE_HEIGHT, txt, color or colors.white)
end

local function print_status(n, txt, status, status_color)
  local y = PADDING + n * LINE_HEIGHT
  gpu.drawText(PADDING, y, txt, colors.white)
  local s = "[ " .. status .. " ]"
  local x = W - PADDING - (#s * CHAR_W)
  gpu.drawText(x, y, s, status_color)
end

local function scan_boot()
  local has_kernel = fs.exists("/boot/kernel.jar")
  local has_lua = fs.exists("/boot/boot.lua")
  return has_kernel, has_lua
end

local function boot_menu()
  local k, l = scan_boot()
  if k and not l then return 1 end
  if l and not k then return 2 end
  if not k and not l then return 3 end

  print_line(line, "Select boot option:", colors.white) ; line = line + 1
  print_line(line, "1. LoraOS Java Kernel (/boot/kernel.jar)") ; line = line + 1
  print_line(line, "2. Lua Operating System (/boot/boot.lua)") ; line = line + 1
  print_line(line, "3. LoraCore Recovery (ROM)") ; line = line + 2

  -- Примитивный авто-выбор через таймер: сначала Java, затем Lua
  os.sleep(1.0)
  return 1
end

local function main()
  gpu.fill(0, 0, W, H, colors.black)
  gpu.drawText(PADDING, PADDING, "LoraBIOS v3.0", colors.gray)
  gpu.fill(PADDING, PADDING + LINE_HEIGHT, W - PADDING * 2, 1, colors.gray)
  os.sleep(0.2)
  line = 2

  print_line(line, "CPU: lora_mobile_v1 @ 25 MHz") ; print_status(line, "", "OK", colors.lime) ; line = line + 1
  print_line(line, "RAM: 512 KB") ; print_status(line, "", "OK", colors.lime) ; line = line + 2
  print_line(line, "Scanning devices...") ; os.sleep(0.2)
  print_line(line, "Found 1 HDD.") ; line = line + 2

  local choice = boot_menu()
  if choice == 1 then
    os.boot_java("/boot/kernel.jar")
  elseif choice == 2 then
    local code = fs.read("/boot/boot.lua")
    if code then
      local func, err = load(code, "@/boot/boot.lua", "t", _G)
      if func then func() else os.reboot() end
    else
      os.reboot()
    end
  else
    local recovery_func = loadfile("/os/recovery.lua", "t", _G)
    if recovery_func then recovery_func() else os.reboot() end
  end
end

main()