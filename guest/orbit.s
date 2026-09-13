# Cerberus Orbit: original IA-32 demo. All movement/collision logic runs in guest.
# GNU assembler, Intel syntax. No libc, Linux, DOS or Windows dependency.
.intel_syntax noprefix
.code32
.section .text
.global _start
_start:
frame:
    mov eax, 3
    int 0x80                    # Read touch/gamepad cursor (EAX = 0..319).
    sub eax, 28
    cmp eax, 8
    jge clamp_right
    mov eax, 8
clamp_right:
    cmp eax, 256
    jle save_paddle
    mov eax, 256
save_paddle:
    mov DWORD PTR [paddle], eax

    mov eax, DWORD PTR [ball_x]
    add eax, DWORD PTR [velocity_x]
    mov DWORD PTR [ball_x], eax
    cmp eax, 8
    jle bounce_x
    cmp eax, 306
    jl advance_y
bounce_x:
    xor eax, eax
    sub eax, DWORD PTR [velocity_x]
    mov DWORD PTR [velocity_x], eax
advance_y:
    mov eax, DWORD PTR [ball_y]
    add eax, DWORD PTR [velocity_y]
    mov DWORD PTR [ball_y], eax
    cmp eax, 20
    jg check_paddle
    mov DWORD PTR [velocity_y], 2
check_paddle:
    cmp DWORD PTR [velocity_y], 0
    jle draw
    cmp eax, 174
    jl draw
    cmp eax, 180
    jg miss_check
    mov ebx, DWORD PTR [ball_x]
    add ebx, 6
    cmp ebx, DWORD PTR [paddle]
    jl miss_check
    mov ebx, DWORD PTR [paddle]
    add ebx, 56
    cmp DWORD PTR [ball_x], ebx
    jg miss_check
    mov DWORD PTR [velocity_y], -2
    add DWORD PTR [score], 8
    cmp DWORD PTR [score], 304
    jle draw
    mov DWORD PTR [score], 8
    jmp draw
miss_check:
    cmp eax, 198
    jl draw
    mov DWORD PTR [ball_x], 160
    mov DWORD PTR [ball_y], 60
    mov DWORD PTR [velocity_y], -2
    mov DWORD PTR [score], 0

draw:
    mov eax, 1
    mov ebx, 0x0c1020
    int 0x80                    # Clear framebuffer.

    mov ebx, 0
    mov ecx, 0
    mov edx, 320
    mov esi, 3
    mov edi, 0x8157ff
    call rectangle
    mov ebx, 8
    mov ecx, 9
    mov edx, DWORD PTR [score]
    mov esi, 3
    mov edi, 0x45e5bb
    call rectangle
    mov ebx, 4
    mov ecx, 18
    mov edx, 2
    mov esi, 174
    mov edi, 0x293451
    call rectangle
    mov ebx, 314
    call rectangle

    mov ebx, DWORD PTR [paddle]
    mov ecx, 182
    mov edx, 56
    mov esi, 5
    mov edi, 0x45e5bb
    call rectangle
    mov ebx, DWORD PTR [ball_x]
    mov ecx, DWORD PTR [ball_y]
    mov edx, 6
    mov esi, 6
    mov edi, 0xf4f0ff
    call rectangle

    mov eax, 4
    int 0x80                    # Present once, yield to Android scheduler.
    jmp frame
rectangle:
    mov eax, 2
    int 0x80
    ret

.section .data
.align 4
ball_x: .long 160
ball_y: .long 60
velocity_x: .long 2
velocity_y: .long -2
paddle: .long 132
score: .long 0
