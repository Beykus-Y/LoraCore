; ============================================
; LoraCore PnP BIOS v4.1 (Stable High-DPI)
; ============================================

start:
    ldi sp, 0x0FFC

    ; --- 1. Поиск GPU ---
    lui r1, 0x00FF
    ori r1, 0xF000
    ld r3, r1           ; Проверка Magic
    addi r1, 4
    ld r3, r1           ; r3 = Кол-во устройств
    addi r1, 4          ; r1 указывает на первое устройство

find_gpu:
    cmpi r3, 0
    jz die
    ld r4, r1           ; r4 = Тип устройства
    cmpi r4, 2          ; 2 = GPU
    jz gpu_found
    addi r1, 16
    subi r3, 1
    jmp find_gpu

gpu_found:
    addi r1, 4
    ld r5, r1           ; r5 = VRAM Base
    addi r1, 4
    ld r8, r1           ; r8 = VRAM Size (в байтах)

    ; --- 2. Очистка экрана ---
    lui r7, 0xFF08
    ori r7, 0x1030      ; Цвет фона
    ldi r6, 0
cls_loop:
    cmp r6, r8
    jz draw_logo
    mov r9, r5
    add r9, r6
    st r9, r7
    addi r6, 4
    jmp cls_loop

draw_logo:
    lui r7, 0xFFFF
    ori r7, 0xFFFF      ; Белый цвет для букв

    ldi r11, 380        ; X
    ldi r12, 240        ; Y

    ; Рисуем L-O-R-A
    ldi r13, font_l
    call plot_char
    addi r11, 50        ; Шаг между буквами

    ldi r13, font_o
    call plot_char
    addi r11, 50

    ldi r13, font_r
    call plot_char
    addi r11, 50

    ldi r13, font_a
    call plot_char

    jmp die

; ============================================
; Подпрограмма отрисовки (Scale 4x4)
; r11=X, r12=Y, r13=Ptr
; Свободные регистры: r0-r4, r6, r9, r10, r14
; ============================================
plot_char:
    ldi r14, 0          ; Номер строки данных (0-6)
p_line:
    cmpi r14, 7
    jz p_end

    ; Загрузка 4-байтового слова данных строки
    mov r10, r13
    mov r4, r14
    ldi r0, 4
    mul r4, r0          ; Офсет = строка * 4
    add r10, r4
    ld r2, r10          ; r2 = битовая маска строки

    ldi r3, 0           ; Счётчик битов (0-7)
p_bit:
    cmpi r3, 8
    jz p_next_line

    mov r4, r2
    andi r4, 0x80       ; Проверяем самый левый бит
    cmpi r4, 0x80
    jnz p_skip_pixel    ; Если бит 0, пропускаем блок 4x4

    ; --- Рисуем блок 4x4 пикселя ---
    ldi r1, 0          ; локальный y (0-3)
y_q:
    cmpi r1, 4
    jz p_skip_pixel
    ldi r0, 0          ; локальный x (0-3)
x_q:
    cmpi r0, 4
    jz y_q_n

    ; Адрес = Base + ((Y + line*4 + ly)*960 + (X + bit*4 + lx)) * 4
    ; 1. Считаем Y координату
    mov r9, r14
    ldi r4, 4
    mul r9, r4          ; line * 4
    add r9, r12         ; + Y
    add r9, r1          ; + local_y
    ldi r4, 960
    mul r9, r4          ; Y_total * 960

    ; 2. Считаем X координату
    mov r6, r3
    ldi r4, 4
    mul r6, r4          ; bit * 4
    add r6, r11         ; + X
    add r6, r0          ; + local_x

    add r9, r6          ; Y_total * 960 + X_total
    ldi r4, 4
    mul r9, r4          ; * 4 байта на пиксель
    add r9, r5          ; + VRAM Base

    st r9, r7           ; Красим!

    addi r0, 1
    jmp x_q
y_q_n:
    addi r1, 1
    jmp y_q

p_skip_pixel:
    shl r2, 1           ; Сдвигаем маску влево
    addi r3, 1
    jmp p_bit

p_next_line:
    addi r14, 1
    jmp p_line
p_end:
    ret

die:
    hlt
    jmp die

; Данные шрифта (каждое значение - 4-байтовое слово для ld)
font_l: data 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0xFC
font_o: data 0x7C, 0x82, 0x82, 0x82, 0x82, 0x82, 0x7C
font_r: data 0xFC, 0x82, 0x82, 0xFC, 0x90, 0x88, 0x84
font_a: data 0x10, 0x28, 0x44, 0x44, 0x7C, 0x44, 0x44