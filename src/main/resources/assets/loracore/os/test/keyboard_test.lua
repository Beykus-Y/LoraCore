-- Тест для проверки работы клавиатуры
-- Этот скрипт тестирует обработку событий клавиатуры

print("=== Keyboard Test ===")
print("Press keys to test keyboard handling...")
print("Press 'q' to quit")

local running = true
local key_count = 0

while running do
    local event = os.pullEvent()
    
    if event then
        print("Event: " .. tostring(event) .. " (type: " .. type(event) .. ")")
        
        if type(event) == "table" then
            print("  Event[1] = " .. tostring(event[1]))
            print("  Event[2] = " .. tostring(event[2]))
            print("  Event[3] = " .. tostring(event[3]))
        end
        
        if event == "key" and type(event) == "table" then
            local key = event[2]
            key_count = key_count + 1
            print("  KEY EVENT #" .. key_count .. ": " .. tostring(key))
            
            -- Проверяем различные клавиши
            if key == 49 then print("    -> Key 1 pressed")
            elseif key == 50 then print("    -> Key 2 pressed")
            elseif key == 51 then print("    -> Key 3 pressed")
            elseif key == 52 then print("    -> Key 4 pressed")
            elseif key == 53 then print("    -> Key 5 pressed")
            elseif key == 54 then print("    -> Key 6 pressed")
            elseif key == 257 then print("    -> Enter pressed")
            elseif key == 259 then print("    -> Backspace pressed")
            elseif key == 81 then print("    -> Q pressed - quitting")
                running = false
            else
                print("    -> Unknown key: " .. tostring(key))
            end
        elseif event == "char" and type(event) == "table" then
            local char = event[2]
            print("  CHAR EVENT: '" .. tostring(char) .. "'")
            
            if char == "q" or char == "Q" then
                print("    -> Q character - quitting")
                running = false
            end
        end
    end
    
    os.sleep(0.01)
end

print("Keyboard test completed!")
print("Total key events: " .. key_count)
print("=== Test Complete ===")
