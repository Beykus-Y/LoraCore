; --- LORA OS v1.2: NETWORK SHELL ---
; Полная версия с поддержкой сети и графики

; --- КОНСТАНТЫ ---
.DEF GPU_VRAM   0x4000    ; База VRAM (пиксели)
.DEF GPU_REGS   0x44000   ; База регистров GPU (MMIO)
.DEF KEYBOARD   0x3000    ; База клавиатуры
.DEF FONT_ROM   0x2000    ; База шрифтов
.DEF NIC_BASE   0x60000   ; База Сетевой карты

; Системные цвета (ARGB)
.DEF COLOR_BG   0xFF0000AA ; Синий фон
.DEF COLOR_FG   0xFFFFFFFF ; Белый текст

; Глобальные регистры:
; R10 = Cursor X (Pixels)
; R11 = Cursor Y (Pixels)
; R12 = Keyboard Address (0x3000)

.ORG 0x0500

_start:
    ; 1. Инициализация глобальных переменных
    LDI R10, 8          ; Start X = 8
    LDI R11, 8          ; Start Y = 8
    LDI R12, KEYBOARD   ; R12 = 0x3000

    ; 2. Очистка экрана (Синий)
    CALL _clear_screen

    ; 3. Подключение к сети (localhost:5000)
    CALL _nic_connect

    ; 4. Главный цикл (Event Loop)
_main_loop:
    ; Проверяем входящие пакеты из сети
    CALL _check_network

    ; Проверяем нажатия клавиш
    CALL _check_keyboard

    ; Повторяем бесконечно
    JMP _main_loop


; =============================================================
; ДРАЙВЕР СЕТИ: Подключение
; =============================================================
_nic_connect:
    PUSH R0
    PUSH R1

    ; База регистров NIC = 0x60000 + 0x1000 = 0x61000
    LUI R0, 6           ; R0 = 0x60000
    ADDI R0, 0x1000     ; R0 = 0x61000

    ; Устанавливаем PORT = 5000 (0x1388)
    ; Смещение REG_PORT = 16
    LDI R1, 16
    ADD R1, R0          ; Адрес регистра порта
    LDI R2, 0x1388      ; 5000 decimal
    ST  R1, R2

    ; Отправляем команду CONNECT (CMD=2)
    ; Смещение REG_CMD = 4
    LDI R1, 4
    ADD R1, R0
    LDI R2, 2           ; CMD 2
    ST  R1, R2

    POP R1
    POP R0
    RET


; =============================================================
; ДРАЙВЕР СЕТИ: Чтение входящих данных
; =============================================================
_check_network:
    PUSH R0
    PUSH R1
    PUSH R2
    PUSH R3
    PUSH R4
    PUSH R5

    ; Адрес регистров NIC (0x61000)
    LUI R0, 6
    ADDI R0, 0x1000

    ; Читаем STATUS (Offset 0)
    LD R1, R0
    CMPI R1, 2          ; 2 = Has Data
    JNZ _net_exit       ; Если нет данных, выходим

    ; Читаем длину данных (Offset 12)
    LDI R2, 12
    ADD R2, R0
    LD  R3, R2          ; R3 = RX Length

    ; Адрес буфера приема (0x60000)
    LUI R4, 6           ; R4 = 0x60000

    ; Цикл чтения байтов
    LDI R5, 0           ; Counter i = 0
_net_read_loop:
    LD R1, R4           ; Читаем 4 байта (нам нужен младший)
    ANDI R1, 0xFF       ; R1 = ASCII символ

    ; Рисуем символ на экране
    CALL _draw_char_vram_safe

    ; Двигаем курсор
    CALL _advance_cursor

    ; Инкремент адресов и счетчика
    ADDI R4, 1          ; Следующий байт буфера
    ADDI R5, 1          ; i++
    CMP R5, R3          ; if i < length
    JL _net_read_loop

    ; Подтверждаем чтение (ACK) -> Очистка буфера
    ; CMD = 3 (Offset 4)
    LDI R2, 4
    ADD R2, R0
    LDI R1, 3
    ST  R2, R1

_net_exit:
    POP R5
    POP R4
    POP R3
    POP R2
    POP R1
    POP R0
    RET


; =============================================================
; КЛАВИАТУРА: Обработка ввода и отправка
; =============================================================
_check_keyboard:
    PUSH R0
    PUSH R1
    PUSH R2

    ; Читаем клавиатуру (адрес в R12)
    LD R1, R12
    CMPI R1, 0
    JZ _kbd_exit        ; Если 0, ничего не нажато

    ; 1. Рисуем символ локально (Эхо)
    CALL _draw_char_vram_safe

    ; 2. Двигаем курсор
    CALL _advance_cursor

    ; 3. Отправляем в сеть
    ; TX Buffer = 0x60800
    LUI R2, 6
    ADDI R2, 0x0800
    ST  R2, R1          ; Пишем символ в буфер

    ; Registers = 0x61000
    LUI R2, 6
    ADDI R2, 0x1000

    ; TX Len = 1 (Offset 8)
    LDI R0, 8
    ADD R0, R2
    LDI R1, 1
    ST  R0, R1

    ; CMD = 1 (Send) (Offset 4)
    LDI R0, 4
    ADD R0, R2
    LDI R1, 1
    ST  R0, R1

_kbd_exit:
    POP R2
    POP R1
    POP R0
    RET


; =============================================================
; УТИЛИТА: Продвижение курсора
; =============================================================
_advance_cursor:
    ADDI R10, 8         ; X += 8
    CMPI R10, 248       ; Граница экрана
    JG _new_line
    RET
_new_line:
    LDI R10, 8          ; X = 8
    ADDI R11, 10        ; Y += 10
    ; Тут можно добавить проверку низа экрана
    RET


; =============================================================
; ГРАФИКА: Очистка экрана (GPU MMIO)
; =============================================================
_clear_screen:
    PUSH R0
    PUSH R1
    PUSH R2

    ; Адрес регистров GPU (0x44000)
    LUI R1, 4
    ADDI R1, 0x4000

    ; Цвет фона (Синий 0xFF0000AA)
    LUI R0, 0xFF00
    LDI R2, 0x00AA
    OR  R0, R2

    ; Пишем цвет (Offset 0x18)
    LDI R2, 0x18
    ADD R2, R1
    ST  R2, R0

    ; Команда CLEAR (Offset 0x04)
    LDI R2, 0x04
    ADD R2, R1
    LDI R0, 1
    ST  R2, R0

    ; Ждем GPU
_wait_gpu:
    LD R0, R1
    CMPI R0, 0
    JNZ _wait_gpu

    POP R2
    POP R1
    POP R0
    RET


; =============================================================
; ГРАФИКА: Рисование символа (Безопасная обертка)
; =============================================================
_draw_char_vram_safe:
    ; Просто вызывает основную функцию, она сама сохраняет регистры R0-R7.
    ; R10, R11 меняться не должны внутри draw_char (они меняются снаружи).
    CALL _draw_char_vram
    RET

; =============================================================
; ГРАФИКА: Рисование символа (Software Rendering)
; Вход: R1=ASCII, R10=X, R11=Y
; =============================================================
_draw_char_vram:
    PUSH R0
    PUSH R2
    PUSH R3
    PUSH R4
    PUSH R5
    PUSH R6
    PUSH R7

    ; 1. Адрес паттерна в FontROM (ASCII * 8 + Base)
    MOV R2, R1
    SHL R2, 3
    ADDI R2, FONT_ROM

    ; 2. Рисуем 8 строк
    LDI R3, 0           ; Row Counter

_char_row_loop:
    LD R4, R2           ; Читаем паттерн
    ANDI R4, 0xFF

    LDI R5, 0x80        ; Маска бита
    MOV R6, R10         ; Текущий X

_char_bit_loop:
    MOV R7, R4
    AND R7, R5
    JZ _skip_pixel

    ; Адрес пикселя VRAM = 0x4000 + ((Y+Row)*256 + X) * 4
    MOV R0, R11
    ADD R0, R3
    SHL R0, 8           ; * 256
    ADD R0, R6
    SHL R0, 2           ; * 4 bytes
    ADDI R0, GPU_VRAM

    ; Цвет Белый (0xFFFFFFFF) с использованием ORI
    LUI R7, 0xFFFF
    ORI R7, 0xFFFF      ; <--- ИСПОЛЬЗУЕМ ТВОЮ НОВУЮ ИНСТРУКЦИЮ
    ST R0, R7

_skip_pixel:
    ADDI R6, 1          ; X++
    SHR R5, 1           ; Mask >> 1
    CMPI R5, 0
    JNZ _char_bit_loop

    ADDI R2, 1          ; Next Font Byte
    ADDI R3, 1          ; Row++
    CMPI R3, 8
    JL _char_row_loop

    POP R7
    POP R6
    POP R5
    POP R4
    POP R3
    POP R2
    POP R0
    RET