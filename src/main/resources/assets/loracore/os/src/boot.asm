; =================================================================
; LoraCore BOOTLOADER v1.5
; Loaded at 0x1000. Loads Kernel to 0x2000.
; =================================================================

const KERNEL_ADDR   = 0x2000
const TABLE_LBA     = 2
const TABLE_BUFFER  = 0x3000    ; Временный буфер для таблицы файлов
const VAR_DISK_ADDR = 0x0404    ; Переменная BIOS

_boot_entry:
    ; Установка стека ядра (0x01FFFC)
    lui SP, 0x0001
    ori SP, 0xFFFC
    ; 1. Получаем адрес контроллера диска
    ldi R1, VAR_DISK_ADDR
    ld R1, R1                   ; R1 = Disk Base MMIO

    ; Проверка на всякий случай (хотя BIOS должен был проверить)
    cmpi R1, 0
    jz fatal_error

    ; 2. Читаем таблицу файлов (LBA 2) в буфер 0x3000
    ; LBA = 2
    ldi R2, TABLE_LBA
    mov R3, R1
    addi R3, 8
    st R3, R2

    ; Count = 1 (читаем 1 сектор таблицы, 512 байт, хватит на 8 файлов)
    ldi R2, 1
    mov R3, R1
    addi R3, 12
    st R3, R2

    ; DMA Addr = 0x3000
    ldi R2, TABLE_BUFFER
    mov R3, R1
    addi R3, 16
    st R3, R2

    ; Execute Read
    ldi R2, 1
    st R1, R2
    call wait_disk

    ; 3. Парсим первую запись файла (считаем, что это Kernel)
    ; Структура LoraFS Entry: [Name:40][Flags:4][StartLBA:4][Size:4]...

    ldi R5, TABLE_BUFFER        ; R5 = Начало записи

    ; Проверяем, есть ли файл (Flags != 0)
    ; Смещение флагов = 40
    mov R6, R5
    addi R6, 40
    ld R0, R6
    cmpi R0, 0
    jz fatal_error              ; Пустая запись, ядра нет

    ; Читаем Start LBA (Offset 44)
    mov R6, R5
    addi R6, 44
    ld R7, R6                   ; R7 = Kernel LBA

    ; Читаем Size Bytes (Offset 48)
    mov R6, R5
    addi R6, 48
    ld R8, R6                   ; R8 = Size in Bytes

    ; 4. Вычисляем количество секторов
    ; Sectors = (Size + 511) / 512
    ; Эквивалентно: (Size + 511) >> 9
    addi R8, 511
    mov R9, R8
    shr R9, 9                   ; R9 = Kernel Sector Count

    ; 5. Загружаем Ядро
    ; LBA = R7
    mov R3, R1
    addi R3, 8
    st R3, R7

    ; Count = R9
    mov R3, R1
    addi R3, 12
    st R3, R9

    ; DMA Addr = KERNEL_ADDR (0x2000)
    ldi R2, KERNEL_ADDR
    mov R3, R1
    addi R3, 16
    st R3, R2

    ; Execute Read
    ldi R2, 1
    st R1, R2
    call wait_disk

    ; 6. Launch Kernel
    jmp KERNEL_ADDR

wait_disk:
    ld R2, R1                   ; Читаем статус (Offset 0)
    cmpi R2, 0
    jnz wait_disk
    ret

fatal_error:
    halt