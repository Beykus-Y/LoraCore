-- Очень простой тест событий
-- Этот скрипт просто выводит все события

print("=== Simple Event Test ===")
print("This script will show all events received")
print("Press any key to see events")

local event_count = 0

while event_count < 20 do
    print("Waiting for event...")
    local event = os.pullEvent()
    
    event_count = event_count + 1
    print("Event #" .. event_count .. ": " .. tostring(event))
    
    if type(event) == "table" then
        print("  Event[1] = " .. tostring(event[1]))
        print("  Event[2] = " .. tostring(event[2]))
    end
    
    if event == "key" then
        print("  -> KEY EVENT DETECTED!")
        break
    end
end

print("Test completed!")
print("=== Test Complete ===")
