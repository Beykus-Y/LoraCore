; =================================================================
; LoraCore PROFESSIONAL BIOS v2.0
; Architect: D. Ritchie / L. Torvalds
; =================================================================

; --- System Constants ---
const STACK_TOP     = 0x0FFC    ; Вершина стека (в конце первых 4КБ)
const PNP_ROM_ADDR  = 0xFFF000  ; Адрес PnP таблицы
const BOOT_LOAD_ADDR = 0x1000   ; Куда грузим Bootloader

; --- BIOS Variables (Zero Page Protocol) ---
; Эти адреса — стандарт. Загрузчик и Ядро читают их, чтобы узнать о железе.
const VAR_GPU_ADDR  = 0x0400    ; Базовый адрес GPU
const VAR_DISK_ADDR = 0x0404    ; Базовый адрес Disk Controller

; --- Device Types ---
const TYPE_GPU      = 2
const TYPE_STORAGE  = 3

; --- Entry Point ---
_start:
    ldi SP, STACK_TOP           ; 1. Инициализация стека

    ; 2. Очистка переменных BIOS (Safety first)
    ldi R0, 0
    ldi R1, VAR_GPU_ADDR
    st R1, R0
    ldi R1, VAR_DISK_ADDR
    st R1, R0

    ; 3. Сканирование шины (PnP Discovery)
    call pnp_scan

    ; 4. Проверка критического оборудования (POST)
    call post_check_gpu         ; Если нет GPU -> Halt
    call init_video             ; Инициализация видео (Blue Screen of Life)

    call post_check_disk        ; Если нет Диска -> Orange Screen -> Halt

    ; 5. Загрузка Bootloader (LBA 0 -> RAM 0x1000)
    call load_boot_sector

    ; 6. Передача управления
    jmp BOOT_LOAD_ADDR

; =================================================================
; Routine: pnp_scan
; Ищет устройства и заполняет таблицу VAR_*
; =================================================================
pnp_scan:
    lui R1, 0x00FF
    ori R1, 0xF000              ; R1 = 0xFFF000

    ; Пропускаем Header (Magic + Count)
    addi R1, 8

    ldi R10, 16                 ; Лимит цикла (макс 16 устройств, чтобы не зависнуть)

scan_loop:
    cmpi R10, 0
    jz scan_ret                 ; Выход, если слоты кончились

    ld R2, R1                   ; R2 = Device Type

    ; Проверяем GPU
    cmpi R2, TYPE_GPU
    jz register_gpu

    ; Проверяем Disk
    cmpi R2, TYPE_STORAGE
    jz register_disk

    ; Проверяем конец списка (Type 0 = обычно конец или пусто)
    cmpi R2, 0
    jz scan_ret

    jmp next_slot

register_gpu:
    mov R3, R1
    addi R3, 4                  ; Смещение до Base Address
    ld R4, R3                   ; R4 = Base Address
    ldi R5, VAR_GPU_ADDR
    st R5, R4                   ; Сохраняем в 0x0400
    jmp next_slot

register_disk:
    mov R3, R1
    addi R3, 4
    ld R4, R3
    ldi R5, VAR_DISK_ADDR
    st R5, R4                   ; Сохраняем в 0x0404
    jmp next_slot

next_slot:
    addi R1, 16                 ; След. запись (+16 байт)
    subi R10, 1
    jmp scan_loop

scan_ret:
    ret

; =================================================================
; Routine: init_video
; Очищает экран синим цветом. Требует VAR_GPU_ADDR.
; =================================================================
init_video:
    ldi R1, VAR_GPU_ADDR
    ld R1, R1                   ; R1 = Base Address GPU

    ; Адрес регистров = Base + VRAM_SIZE (480*270*4 = 518400 = 0x7E900)
    lui R2, 0x0007
    ori R2, 0xE900
    add R1, R2                  ; R1 = MMIO Base

    ; Установка цвета (Синий: 0xFF0000AA)
    lui R3, 0xFF00
    ori R3, 0x00AA

    mov R4, R1
    addi R4, 24                 ; Reg Color (offset 24)
    st R4, R3

    ; Команда очистки
    ldi R3, 1                   ; CMD_CLEAR
    mov R4, R1
    addi R4, 4                  ; Reg Cmd (offset 4)
    st R4, R3
    ret

; =================================================================
; Routine: load_boot_sector
; Читает LBA 0 в 0x1000
; =================================================================
load_boot_sector:
    ldi R1, VAR_DISK_ADDR
    ld R1, R1                   ; R1 = Disk MMIO Base

    ; Настройка DMA
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

    ; Polling wait (ждем пока Status станет 0)
disk_wait:
    ld R2, R1                   ; Читаем Status
    cmpi R2, 0
    jnz disk_wait
    ret

; --- Error Handlers (POST) ---

post_check_gpu:
    ldi R1, VAR_GPU_ADDR
    ld R1, R1
    cmpi R1, 0
    jz panic_no_gpu             ; Если 0, значит GPU не найден
    ret

panic_no_gpu:
    ; Мы не можем вывести ошибку на экран, GPU нет.
    ; Просто висим. В эмуляторе это будет видно по регистрам.
    halt

post_check_disk:
    ldi R1, VAR_DISK_ADDR
    ld R1, R1
    cmpi R1, 0
    jz panic_no_disk
    ret

panic_no_disk:
    ; Красим экран в оранжевый (Warning/Error)
    call set_screen_orange
    halt

set_screen_orange:
    ldi R1, VAR_GPU_ADDR
    ld R1, R1
    lui R2, 0x0007
    ori R2, 0xE900
    add R1, R2                  ; MMIO Base

    lui R3, 0xFFFF
    ori R3, 0xA500              ; Orange
    mov R4, R1
    addi R4, 24
    st R4, R3                   ; Color

    ldi R3, 1
    mov R4, R1
    addi R4, 4
    st R4, R3                   ; Cmd Clear
    ret