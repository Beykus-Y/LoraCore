; =================================================================
; LoraCore UNIVERSAL BIOS v3.0 (Dynamic Hardware Support)
; Features: Full PnP Scan, Dynamic VRAM calculation, Error Codes
; =================================================================

; --- System Constants ---
const STACK_TOP_HI   = 0x0001
const STACK_TOP_LO   = 0xFFFC
const PNP_ROM_ADDR   = 0xFFF000  ; Адрес таблицы PnP
const BOOT_LOAD_ADDR = 0x1000    ; Адрес загрузки Bootloader

; --- BIOS Variables (Zero Page Protocol) ---
; Ядро LoraOS читает эти адреса, чтобы узнать конфигурацию железа.
const VAR_GPU_ADDR   = 0x0400    ; [OUT] Адрес GPU Base
const VAR_DISK_ADDR  = 0x0404    ; [OUT] Адрес Disk Controller
const VAR_KEYB_ADDR  = 0x0408    ; [OUT] Адрес Keyboard Controller
const VAR_GPU_SIZE   = 0x0410    ; [INTERNAL] Размер GPU (для расчета регистров)

; --- Device Types (Must match Java PnpEntry) ---
const TYPE_GPU       = 2
const TYPE_STORAGE   = 3
const TYPE_INPUT     = 5

; =================================================================
; ENTRY POINT
; =================================================================
_start:
    cli
    ; 1. Инициализация стека
    lui SP, STACK_TOP_HI
    ori SP, STACK_TOP_LO

    ; 2. Очистка переменных (обнуление)
    ; Это гарантирует, что если устройство не найдено, адрес будет 0
    ldi R0, 0
    ldi R1, VAR_GPU_ADDR
    st R1, R0
    ldi R1, VAR_DISK_ADDR
    st R1, R0
    ldi R1, VAR_KEYB_ADDR
    st R1, R0

    ; 3. Сканирование PnP (Поиск устройств)
    call pnp_scan_auto

    ; 4. POST: Проверка GPU
    ; Если GPU не найден, мы не можем даже показать ошибку. Halt.
    call check_gpu_critical

    ; 5. Инициализация видео (Dynamic Resolution Support)
    call init_video_dynamic

    ; 6. POST: Проверка Диска
    ; Если диска нет, красим экран в оранжевый и стоим.
    call check_disk_critical

    ; 7. Загрузка Bootloader (LBA 0 -> 0x1000)
    call load_boot_sector

    ; 8. Передача управления загрузчику
    jmp BOOT_LOAD_ADDR

; =================================================================
; ROUTINE: pnp_scan_auto
; Сканирует таблицу PnP и заполняет VAR_*
; =================================================================
pnp_scan_auto:
    ; R1 = Указатель на текущую запись PnP
    lui R1, 0x00FF
    ori R1, 0xF000              ; 0xFFF000

    ; Пропускаем Header (Magic 4b + Count 4b) = 8 байт
    addi R1, 8

    ldi R10, 32                 ; Макс. 32 устройства (защита от зацикливания)

scan_loop:
    cmpi R10, 0
    jz scan_ret                 ; Лимит исчерпан

    ld R2, R1                   ; R2 = Device Type (Offset 0)

    ; -- Проверка типа --
    cmpi R2, 0                  ; Type 0 = Конец списка/Пусто
    jz scan_ret

    cmpi R2, TYPE_GPU
    jz found_gpu

    cmpi R2, TYPE_STORAGE
    jz found_disk

    cmpi R2, TYPE_INPUT
    jz found_keyb

    jmp next_entry              ; Неизвестное устройство, пропускаем

found_gpu:
    ; Сохраняем Base Address (Offset 4)
    mov R3, R1
    addi R3, 4
    ld R4, R3                   ; R4 = Base Address
    ldi R5, VAR_GPU_ADDR
    st R5, R4

    ; Сохраняем Device Size (Offset 8) - ВАЖНО для HiDPI!
    mov R3, R1
    addi R3, 8
    ld R4, R3                   ; R4 = SizeBytes
    ldi R5, VAR_GPU_SIZE
    st R5, R4                   ; Сохраняем во временную переменную

    jmp next_entry

found_disk:
    mov R3, R1
    addi R3, 4
    ld R4, R3
    ldi R5, VAR_DISK_ADDR
    st R5, R4
    jmp next_entry

found_keyb:
    mov R3, R1
    addi R3, 4
    ld R4, R3
    ldi R5, VAR_KEYB_ADDR
    st R5, R4
    jmp next_entry

next_entry:
    addi R1, 16                 ; Переход к следующей записи (16 байт)
    subi R10, 1
    jmp scan_loop

scan_ret:
    ret

; =================================================================
; ROUTINE: init_video_dynamic
; Вычисляет адрес регистров GPU и очищает экран синим.
; Formula: MMIO_Base = GPU_Base + GPU_Size - 64
; =================================================================
init_video_dynamic:
    ; 1. Загружаем Base Address
    ldi R1, VAR_GPU_ADDR
    ld R1, R1                   ; R1 = GPU Base

    ; 2. Загружаем Total Size
    ldi R2, VAR_GPU_SIZE
    ld R2, R2                   ; R2 = GPU Size (e.g. 2MB + 64)

    ; 3. Вычисляем MMIO Base = Base + Size - 64
    add R1, R2                  ; R1 = End of Device
    subi R1, 64                 ; R1 = Start of Registers (MMIO Base)

    ; Теперь R1 указывает точно на начало регистров, независимо от разрешения!

    ; 4. Установка цвета (Синий: 0xFF0000AA)
    lui R3, 0xFF00
    ori R3, 0x00AA

    mov R4, R1
    addi R4, 24                 ; Reg Color (Offset 24)
    st R4, R3

    ; 5. Команда очистки
    ldi R3, 1                   ; CMD_CLEAR
    mov R4, R1
    addi R4, 4                  ; Reg Cmd (Offset 4)
    st R4, R3
    ret

; =================================================================
; ROUTINE: load_boot_sector
; Читает LBA 0 с диска в память
; =================================================================
load_boot_sector:
    ldi R1, VAR_DISK_ADDR
    ld R1, R1                   ; R1 = Disk MMIO Base

    ; LBA (Offset 8) = 0
    ldi R2, 0
    mov R3, R1
    addi R3, 8
    st R3, R2

    ; Count (Offset 12) = 1
    ldi R2, 1
    mov R3, R1
    addi R3, 12
    st R3, R2

    ; DMA Addr (Offset 16) = BOOT_LOAD_ADDR
    ldi R2, BOOT_LOAD_ADDR
    mov R3, R1
    addi R3, 16
    st R3, R2

    ; Command (Offset 0) = 1 (Read)
    ldi R2, 1
    st R1, R2

    ; Wait for Busy flag (0)
disk_wait_loop:
    ld R2, R1                   ; Читаем Status (Offset 0)
    cmpi R2, 0
    jnz disk_wait_loop
    ret

; =================================================================
; ERROR HANDLERS
; =================================================================

check_gpu_critical:
    ldi R1, VAR_GPU_ADDR
    ld R1, R1
    cmpi R1, 0
    jz halt_cpu                 ; GPU нет -> Смерть
    ret

check_disk_critical:
    ldi R1, VAR_DISK_ADDR
    ld R1, R1
    cmpi R1, 0
    jz panic_screen_orange      ; Диска нет -> Оранжевый экран
    ret

halt_cpu:
    halt
    jmp halt_cpu

panic_screen_orange:
    ; Мы знаем, что GPU есть (прошли check_gpu), но диска нет.
    ; Вычисляем адрес регистров заново (R1 уже может быть испорчен)

    ldi R1, VAR_GPU_ADDR
    ld R1, R1
    ldi R2, VAR_GPU_SIZE
    ld R2, R2
    add R1, R2
    subi R1, 64                 ; R1 = MMIO Base

    ; Оранжевый цвет (0xFF FFA500)
    lui R3, 0xFFFF
    ori R3, 0xA500

    mov R4, R1
    addi R4, 24                 ; Reg Color
    st R4, R3

    ldi R3, 1                   ; CMD_CLEAR
    mov R4, R1
    addi R4, 4
    st R4, R3

    halt