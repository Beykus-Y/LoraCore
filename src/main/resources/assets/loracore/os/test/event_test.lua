-- Простой тест для проверки обработки событий
-- Этот скрипт тестирует базовую функциональность событий

print("=== Event Test ===")
print("Testing event handling...")

-- Проверяем доступность os API
if not os then
    print("ERROR: os API not available!")
    return
end

if not os.pullEvent then
    print("ERROR: os.pullEvent not available!")
    return
end

print("os.pullEvent available. Testing event loop...")

-- Простой тест событий
local event_count = 0
local max_events = 10

print("Press any key " .. max_events .. " times to test event handling...")

while event_count < max_events do
    local event = os.pullEvent()
    
    if event then
        event_count = event_count + 1
        print("Event " .. event_count .. ": " .. tostring(event))
        
        if event == "key" then
            local key = event[2]
            if key then
                print("  Key pressed: " .. tostring(key))
            else
                print("  Key is nil")
            end
        end
    else
        print("Event is nil")
    end
    
    -- Небольшая задержка
    os.sleep(0.1)
end

print("Event test completed successfully!")
print("=== Test Complete ===")
