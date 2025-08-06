-- =======================================================
-- LoraCore GPU Driver v1.0
-- =======================================================
-- Описание: Этот драйвер предоставляет стандартизированный интерфейс
-- для работы с графическим процессором планшета.

-- Получаем доступ к "родному" Java API, предоставленному в таблице 'tablet'
local native_gpu = tablet.gpu
if not native_gpu then
    -- Если API не найдено, работа невозможна.
    -- error() с уровнем 0 не будет показывать имя этого файла в ошибке.
    error("Native GPU API not found!", 0)
end

-- Создаем таблицу нашего модуля
local M = {}

--[[
    Все функции ниже являются обертками над нативными вызовами.
    Это позволяет в будущем легко добавлять новую логику (например,
    проверку прав доступа, оптимизацию вызовов) в одном месте,
    не изменяя все программы, которые используют этот драйвер.
]]

function M.getResolution()
    return native_gpu.getResolution()
end

function M.clear()
    native_gpu.clear()
end

function M.set(x, y, text)
    -- Проверка на nil, чтобы избежать ошибок при передаче пустых значений
    if text == nil then text = "" end
    return native_gpu.set(x, y, tostring(text))
end

function M.fill(x, y, w, h, char)
    if char == nil then char = " " end
    return native_gpu.fill(x, y, w, h, tostring(char))
end

function M.setTextColor(color)
    return native_gpu.setTextColor(color)
end

function M.setBackgroundColor(color)
    return native_gpu.setBackgroundColor(color)
end

-- Возвращаем готовую таблицу функций, чтобы ее можно было использовать через require()
return M