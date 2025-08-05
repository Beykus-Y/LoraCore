-- /drivers/gpu.lua
-- Драйвер для встроенного GPU планшета.
-- Он использует низкоуровневый 'gpu' API и предоставляет его программам.

local native_gpu = _G.gpu -- Получаем доступ к Java-объекту GPU
if not native_gpu then
    error("Native GPU API not found!", 0)
end

local M = {}

-- Передаем вызовы напрямую, но в будущем здесь можно будет
-- добавить логику, например, проверку прав доступа.

function M.getResolution()
    return native_gpu.getResolution()
end

function M.clear()
    native_gpu.clear()
end

function M.set(x, y, text)
    native_gpu.set(x, y, text)
end

function M.fill(x, y, w, h, char)
    native_gpu.fill(x, y, w, h, char)
end

function M.setTextColor(color)
    native_gpu.setTextColor(color)
end

function M.setBackgroundColor(color)
    native_gpu.setBackgroundColor(color)
end

return M