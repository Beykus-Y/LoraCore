; --- START OF FILE bios.asm ---

; --- LORA BIOS v2.2 (High Mem MMIO) ---
; Map:
; RAM:      0x000000
; VRAM:     0x004000
; KEYBOARD: 0xF00000
; DISK:     0xF01000
; NIC:      0xF02000
; CMOS:     0xF04000
; ======================================

; Константы для .DEF оставим для справки,
; но в коде будем использовать LUI/ORI для 32-битных адресов.
.DEF RAM_TEST     0x1000

; Коды клавиш
.DEF KEY_DEL      127
.DEF KEY_ENTER    10

setup_entry:
    LDI R15, 0x1FFC      ; Init Stack (Stack в пределах первых 8KB RAM)

    ; 1. Инициализация видео (Очистка)
    CALL clear_screen_blue

    ; 2. POST (Power-On Self Test)
    LDI R12, 10   ; X
    LDI R13, 10   ; Y

    LDI R0, L_POST_MSG
    CALL print_str

    ; Тест RAM (быстрый)
    LDI R0, 0x55AA
    ST  RAM_TEST, R0
    LD  R1, RAM_TEST
    CMP R0, R1
    JNZ hardware_fail

    LDI R0, L_RAM_OK
    CALL print_str

    ; 3. Ожидание ввода (Press DEL to enter Setup)
    LDI R12, 10
    LDI R13, 100
    LDI R0, L_PRESS_DEL
    CALL print_str

    ; Ждем 200 циклов
    LDI R10, 200

post_wait_loop:
    ; Проверка клавиатуры (Addr: 0xF00000)
    ; LDI не умеет грузить 0xF00000, используем LUI
    LUI R0, 0x00F0     ; R0 = 0xF00000
    ; ORI R0, 0x0000   ; Не нужно, смещение 0

    LD  R1, R0         ; Читаем клавишу

    CMPI R1, KEY_DEL   ; Если DEL
    JZ bios_setup_menu

    CMPI R1, 0         ; Если ничего
    JZ _wait_tick

_wait_tick:
    ; Небольшая задержка
    LDI R0, 1000
_delay:
    ADDI R0, -1
    JNZ _delay

    ADDI R10, -1
    JNZ post_wait_loop

    ; Тайм-аут вышел -> ЗАГРУЗКА ОС
    JMP boot_sequence


; =========================================================
; BIOS SETUP UTILITY
; =========================================================
bios_setup_menu:
    CALL clear_screen_blue

    ; Заголовок
    LDI R12, 80
    LDI R13, 10
    LDI R0, L_SETUP_TITLE
    CALL print_str

    ; Меню
    LDI R12, 20
    LDI R13, 50
    LDI R0, L_OPT_1
    CALL print_str

    ; Читаем CMOS (Addr: 0xF04000)
    LUI R0, 0x00F0
    ORI R0, 0x4000     ; R0 = 0xF04000

    LD  R1, R0
    ANDI R1, 0xFF

    LDI R12, 150
    CMPI R1, 1
    JZ _draw_net_label
    LDI R0, L_VAL_HDD
    JMP _print_val
_draw_net_label:
    LDI R0, L_VAL_NET
_print_val:
    CALL print_str

    ; Инструкция
    LDI R12, 20
    LDI R13, 200
    LDI R0, L_SETUP_HINT
    CALL print_str

setup_loop:
    ; Клавиатура (0xF00000)
    LUI R0, 0x00F0
    LD  R1, R0

    CMPI R1, KEY_ENTER
    JZ _toggle_setting

    JMP setup_loop

_toggle_setting:
    ; CMOS Read/Modify/Write
    LUI R0, 0x00F0
    ORI R0, 0x4000     ; R0 = 0xF04000

    LD  R1, R0
    ADDI R1, 1
    ANDI R1, 1
    ST  R0, R1

    JMP setup_entry    ; Reboot


; =========================================================
; BOOT SEQUENCE
; =========================================================
boot_sequence:
    CALL clear_screen_black

    ; Читаем приоритет (CMOS)
    LUI R0, 0x00F0
    ORI R0, 0x4000
    LD  R1, R0
    ANDI R1, 1
    CMPI R1, 1
    JZ boot_network

boot_hdd:
    LDI R12, 10
    LDI R13, 10
    LDI R0, L_BOOT_HDD
    CALL print_str

    ; Загрузка ядра
    LDI R0, 0       ; LBA
    LDI R1, 0x0500  ; RAM Target (0x0500 fits in LDI)

    ; [KOWALSKI FIX 2.0]:
    ; 16 секторов (8KB) убивали стек на 0x1FFC.
    ; Ядро весит 3.5KB. 8 секторов (4KB) хватит с запасом.
    LDI R10, 8      ; Sectors count (4KB load)

_boot_loop:
    PUSH R0
    PUSH R1
    PUSH R10
    LDI R2, 1       ; READ CMD
    CALL disk_op
    POP R10
    POP R1
    POP R0
    ADDI R0, 1
    ADDI R1, 512
    SUBI R10, 1
    JNZ _boot_loop

    JMP 0x0500      ; JUMP TO KERNEL
boot_network:
    LDI R12, 10
    LDI R13, 10
    LDI R0, L_BOOT_NET
    CALL print_str
    HLT


; =========================================================
; SYSTEM ERROR
; =========================================================
hardware_fail:
    LDI R12, 100
    LDI R13, 100
    LDI R0, L_FAIL
    CALL print_str
    HLT


; =========================================================
; DRIVERS & UTILS
; =========================================================

; --- PRINT STRING ---
print_str:
    PUSH R0
    PUSH R1
    PUSH R7
_ps_loop:
    LD R7, R0
    ANDI R7, 0xFF
    CMPI R7, 0
    JZ _ps_end

    CALL draw_char

    ADDI R12, 8
    ADDI R0, 1
    JMP _ps_loop

_ps_end:
    POP R7
    POP R1
    POP R0
    RET

; --- DISK OP ---
; R0=LBA, R1=RAM, R2=CMD
; Disk Ctrl Addr: 0xF01000
disk_op:
    PUSH R3
    PUSH R4
    PUSH R5

    ; Load Disk Base Addr (0xF01000)
    LUI R3, 0x00F0
    ORI R3, 0x1000  ; R3 = 0xF01000

_d_w1:
    LD R4, R3       ; Read STATUS (Offset 0)
    CMPI R4, 0
    JNZ _d_w1       ; Wait if Busy

    ; Write Registers
    ; R3 is Base. We need Base + Offset.

    ; REG_LBA (Index 2 -> Offset 8)
    LDI R5, 8
    ADD R5, R3
    ST  R5, R0

    ; REG_ADDR (Index 3 -> Offset 12)
    LDI R5, 12
    ADD R5, R3
    ST  R5, R1

    ; REG_CMD (Index 1 -> Offset 4)
    LDI R5, 4
    ADD R5, R3
    ST  R5, R2      ; Execute

_d_w2:
    LD R4, R3       ; Wait for finish
    CMPI R4, 0
    JNZ _d_w2

    POP R5
    POP R4
    POP R3
    RET

; --- CLEAR SCREEN (BLUE) ---
clear_screen_blue:
    LDI R0, 0x4000      ; VRAM Base (0x4000 fits LDI)
    LUI R1, 0x0004      ; Size
    ADDI R1, 0x4000     ; End Addr
    LUI R2, 0xFF00
    ORI R2, 0x00AA      ; Color
_csb_loop:
    ST R0, R2
    ADDI R0, 4
    CMP R0, R1
    JL _csb_loop
    RET

; --- CLEAR SCREEN (BLACK) ---
clear_screen_black:
    LDI R0, 0x4000
    LUI R1, 0x0004
    ADDI R1, 0x4000
    LDI R2, 0
_csblk_loop:
    ST R0, R2
    ADDI R0, 4
    CMP R0, R1
    JL _csblk_loop
    RET

; --- DRAW CHAR ---
draw_char:
    PUSH R0
    PUSH R1
    PUSH R2
    PUSH R3
    PUSH R4
    PUSH R5
    PUSH R10
    PUSH R11

    LUI R10, 0xFFFF
    ORI R10, 0xFFFF ; White
    LDI R11, 0

    MOV R0, R7
    SHL R0, 3
    ADDI R0, 0x2000 ; Font Base
    LDI R2, 0       ; Row
_dc_row:
    LD R3, R0
    ANDI R3, 0xFF
    MOV R4, R13
    SHL R4, 10
    MOV R5, R12
    SHL R5, 2
    ADD R4, R5
    ADDI R4, 0x4000 ; VRAM Base
    LDI R5, 0x80
_dc_bit:
    MOV R1, R3
    AND R1, R5
    JZ _dc_zero
    ST R4, R10
    JMP _dc_next
_dc_zero:
    ; Skip background
_dc_next:
    ADDI R4, 4
    SHR R5, 1
    JNZ _dc_bit

    ADDI R13, 1
    ADDI R0, 1
    ADDI R2, 1
    CMPI R2, 8
    JL _dc_row

    SUBI R13, 8

    POP R11
    POP R10
    POP R5
    POP R4
    POP R3
    POP R2
    POP R1
    POP R0
    RET


; =========================================================
; STRINGS
; =========================================================
.ORG 0x0400

L_POST_MSG:
.ASCII "LORA BIOS v2.2 (HighMem)"
L_RAM_OK:
.ASCII " RAM OK"
L_PRESS_DEL:
.ASCII "Press DEL for Setup..."
L_FAIL:
.ASCII "HARDWARE FAILURE"
L_SETUP_TITLE:
.ASCII "=== BIOS SETUP ==="
L_OPT_1:
.ASCII "Boot Device: "
L_VAL_HDD:
.ASCII "[HDD]"
L_VAL_NET:
.ASCII "[NETWORK]"
L_SETUP_HINT:
.ASCII "ENTER: Toggle  RESET: Auto"
L_BOOT_HDD:
.ASCII "Booting from Disk..."
L_BOOT_NET:
.ASCII "PXE Boot..."