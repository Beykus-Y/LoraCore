-- Файл: src/main/resources/data/loracore/os/test/vfs_test.lua
-- Тест для проверки новой системы ImageVfs с разделами

local gpu = tablet.gpu
local fs = _G.fs
local colors = _G.colors
local os = _G.os

local W, H = 480, 270
local line = 1

local function println(text, color)
    gpu.drawText(10, 10 + line * 12, text, color or colors.white)
    line = line + 1
    os.sleep(0.1) -- Небольшая задержка для наглядности
end

gpu.fill(0, 0, W, H, colors.black)
println("=== ImageVFS Test Suite v1.0 ===")
line = line + 1

-- 1. Тест записи
println("1. Writing to partitions...", colors.yellow)
local write_boot = fs.write("/boot/test.txt", "Hello from boot partition!")
local write_home = fs.write("/home/test.txt", "Hello from home partition!")

if write_boot and write_home then
    println(" > Write successful.", colors.lime)
else
    println(" > Write FAILED.", colors.red)
    return
end

-- 2. Тест чтения
line = line + 1
println("2. Reading from partitions...", colors.yellow)
local content_boot = fs.read("/boot/test.txt")
local content_home = fs.read("/home/test.txt")

println(" > /boot/test.txt: " .. tostring(content_boot))
println(" > /home/test.txt: " .. tostring(content_home))

if content_boot == "Hello from boot partition!" and content_home == "Hello from home partition!" then
    println(" > Read successful.", colors.lime)
else
    println(" > Read FAILED. Content mismatch.", colors.red)
    return
end

-- 3. Тест листинга
line = line + 1
println("3. Listing partitions...", colors.yellow)
local boot_files = fs.list("/boot")
local home_files = fs.list("/home")

println(" > /boot files: " .. tostring(boot_files))
println(" > /home files: " .. tostring(home_files))

-- Простая проверка, что вернулся не nil
if boot_files and home_files then
    println(" > List successful.", colors.lime)
else
    println(" > List FAILED.", colors.red)
    return
end

-- 4. Тест удаления
line = line + 1
println("4. Deleting files...", colors.yellow)
fs.delete("/boot/test.txt")
fs.delete("/home/test.txt")

local exists_after_delete = fs.exists("/boot/test.txt")
if not exists_after_delete then
    println(" > Delete successful.", colors.lime)
else
    println(" > Delete FAILED. File still exists.", colors.red)
    return
end


line = line + 2
println("=== All VFS tests passed! ===", colors.lime)