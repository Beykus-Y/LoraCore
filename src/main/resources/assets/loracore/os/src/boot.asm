; =================================================================
; LoraCore stage-1 boot sector
; BIOS loads this sector from LBA 0 to 0x1000.
; Its only job is to load the extensible stage-2 loader.
; =================================================================

const VAR_DISK_ADDR   = 0x0404
const STAGE2_LBA      = 1
const STAGE2_SECTORS  = 4
const STAGE2_LOAD     = 0x1200

_boot_entry:
    lui SP, 0x0001
    ori SP, 0xFFFC

    ldi R1, VAR_DISK_ADDR
    ld R1, R1
    cmpi R1, 0
    jz boot_halt

    ldi R2, STAGE2_LBA
    mov R3, R1
    addi R3, 8
    st R3, R2

    ldi R2, STAGE2_SECTORS
    mov R3, R1
    addi R3, 12
    st R3, R2

    ldi R2, STAGE2_LOAD
    mov R3, R1
    addi R3, 16
    st R3, R2

    ldi R2, 1
    st R1, R2

stage1_wait:
    ld R2, R1
    cmpi R2, 0
    jnz stage1_wait

    mov R3, R1
    addi R3, 4
    ld R2, R3
    cmpi R2, 0
    jnz boot_halt

    jmp STAGE2_LOAD

boot_halt:
    halt
    jmp boot_halt
