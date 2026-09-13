ENTRY(_start)
PHDRS { code PT_LOAD FLAGS(5); data PT_LOAD FLAGS(6); }
SECTIONS {
    . = 0x10000;
    .text : { *(.text*) } :code
    __code_end = .;
    .data : { *(.data*) *(.rodata*) } :data
    /DISCARD/ : { *(.note*) *(.comment*) *(.eh_frame*) }
}
