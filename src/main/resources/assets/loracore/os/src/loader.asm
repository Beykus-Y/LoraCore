; =================================================================
; LoraBoot 2.0 stage-2 dualboot loader
; Loaded by stage 1 from LBA 1..4 to 0x1200.
; =================================================================

const VAR_GPU_ADDR       = 0x0400
const VAR_DISK_ADDR      = 0x0404
const VAR_KEYB_ADDR      = 0x0408
const VAR_GPU_SIZE       = 0x0410
const VAR_LOADER_GPU     = 0x0420

const CONFIG_LBA         = 5
const CONFIG_BUFFER      = 0x3000
const TABLE_LBA          = 6
const TABLE_SECTORS      = 2
const TABLE_BUFFER       = 0x3200
const ENTRY_SIZE         = 64
const KERNEL_LOAD_ADDR   = 0x2000
const MENU_TICKS         = 40

loader_entry:
    lui SP, 0x0001
    ori SP, 0xFFFC

    ; Load boot configuration.
    ldi R7, CONFIG_LBA
    ldi R8, 1
    ldi R9, CONFIG_BUFFER
    call disk_read
    cmpi R0, 0
    jnz loader_error

    ; Load the 16-entry file table.
    ldi R7, TABLE_LBA
    ldi R8, TABLE_SECTORS
    ldi R9, TABLE_BUFFER
    call disk_read
    cmpi R0, 0
    jnz loader_error

    call gpu_init
    call show_menu

    ; Default slot from config offset 8. Any value other than 1 means slot 0.
    ldi R6, 0x3008
    ld R5, R6
    cmpi R5, 1
    jz default_ready
    ldi R5, 0
default_ready:
    ldi R10, MENU_TICKS

selection_loop:
    ldi R6, VAR_KEYB_ADDR
    ld R6, R6
    cmpi R6, 0
    jz selection_wait
    ld R2, R6
    cmpi R2, 0
    jz selection_wait
    addi R6, 4
    ld R2, R6
    cmpi R2, 49
    jz choose_system
    cmpi R2, 50
    jz choose_recovery
    cmpi R2, 13
    jz selection_done

selection_wait:
    wait 5000
    subi R10, 1
    cmpi R10, 0
    jnz selection_loop
    jmp selection_done

choose_system:
    ldi R5, 0
    jmp selection_done

choose_recovery:
    ldi R5, 1

selection_done:
    ldi R11, 0

try_selected_slot:
    ; Config offsets 16 and 20 contain the table entry indices.
    ldi R6, 0x3010
    cmpi R5, 0
    jz slot_index_address_ready
    addi R6, 4
slot_index_address_ready:
    ld R4, R6
    shl R4, 6
    ldi R6, TABLE_BUFFER
    add R4, R6

    ; Entry must be marked active.
    mov R6, R4
    addi R6, 40
    ld R0, R6
    cmpi R0, 1
    jnz selected_failed

    ; Read entry LBA and convert byte size to sector count.
    mov R6, R4
    addi R6, 44
    ld R7, R6
    mov R6, R4
    addi R6, 48
    ld R8, R6
    addi R8, 511
    shr R8, 9
    ldi R9, KERNEL_LOAD_ADDR
    call disk_read
    cmpi R0, 0
    jnz selected_failed

    jmp KERNEL_LOAD_ADDR

selected_failed:
    ; One automatic fallback to the other boot slot.
    cmpi R11, 0
    jnz loader_error
    ldi R11, 1
    ldi R0, 1
    sub R0, R5
    mov R5, R0
    jmp try_selected_slot

; R7=LBA, R8=count, R9=DMA address. Returns disk error in R0.
disk_read:
    ldi R1, VAR_DISK_ADDR
    ld R1, R1
    cmpi R1, 0
    jz disk_missing

    mov R3, R1
    addi R3, 8
    st R3, R7
    mov R3, R1
    addi R3, 12
    st R3, R8
    mov R3, R1
    addi R3, 16
    st R3, R9
    ldi R2, 1
    st R1, R2

disk_wait:
    ld R2, R1
    cmpi R2, 0
    jnz disk_wait
    mov R3, R1
    addi R3, 4
    ld R0, R3
    ret

disk_missing:
    ldi R0, 1
    ret

gpu_init:
    ldi R1, VAR_GPU_ADDR
    ld R1, R1
    ldi R2, VAR_GPU_SIZE
    ld R2, R2
    add R1, R2
    subi R1, 64
    ldi R2, VAR_LOADER_GPU
    st R2, R1

    ; Clear to dark blue.
    lui R3, 0xFF10
    ori R3, 0x1830
    mov R4, R1
    addi R4, 24
    st R4, R3
    ldi R3, 1
    mov R4, R1
    addi R4, 4
    st R4, R3
    ret

show_menu:
    ldi R0, 16
    ldi R1, 16
    ldi R2, menu_title
    lui R3, 0xFFFF
    ori R3, 0xFFFF
    call print_string

    ldi R0, 16
    ldi R1, 40
    ldi R2, menu_options
    lui R3, 0xFF80
    ori R3, 0xFFFF
    call print_string
    ret

; R0=x, R1=y, R2=character code, R3=color.
gpu_char:
    ldi R6, VAR_LOADER_GPU
    ld R6, R6
    mov R7, R6
    addi R7, 8
    st R7, R0
    mov R7, R6
    addi R7, 12
    st R7, R1
    mov R7, R6
    addi R7, 16
    st R7, R2
    mov R7, R6
    addi R7, 24
    st R7, R3
    ldi R5, 3
    addi R6, 4
    st R6, R5
    ret

; R0=x, R1=y, R2=pointer to 32-bit character string, R3=color.
print_string:
    ld R4, R2
    cmpi R4, 0
    jz print_done
    push R2
    mov R2, R4
    call gpu_char
    pop R2
    addi R0, 8
    addi R2, 4
    jmp print_string
print_done:
    ret

loader_error:
    ; Red screen means neither boot slot could be loaded.
    ldi R1, VAR_LOADER_GPU
    ld R1, R1
    cmpi R1, 0
    jz loader_halt
    lui R3, 0xFF80
    ori R3, 0x0000
    mov R4, R1
    addi R4, 24
    st R4, R3
    ldi R3, 1
    mov R4, R1
    addi R4, 4
    st R4, R3
loader_halt:
    halt
    jmp loader_halt

menu_title:
.data 76, 111, 114, 97, 66, 111, 111, 116, 32, 50, 46, 48, 0
menu_options:
.data 91, 49, 93, 32, 76, 111, 114, 97, 79, 83, 32, 32, 91, 50, 93, 32, 82, 101, 99, 111, 118, 101, 114, 121, 0
