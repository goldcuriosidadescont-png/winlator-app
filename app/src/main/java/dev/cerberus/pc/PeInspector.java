package dev.cerberus.pc;

/** Header inspection only; never maps or executes PE sections. Pure Java, host-testable. */
final class PeInspector {
    private static int u16(byte[] b, int offset) {
        if (offset < 0 || offset > b.length - 2) throw new IllegalArgumentException("Cabecalho truncado");
        return (b[offset] & 255) | ((b[offset + 1] & 255) << 8);
    }
    private static long u32(byte[] b, int offset) {
        return u16(b, offset) | ((long)u16(b, offset + 2) << 16);
    }
    static String inspect(byte[] bytes) {
        try {
            if (u16(bytes, 0) != 0x5a4d) return "Arquivo sem assinatura MZ.";
            long pointer = u32(bytes, 0x3c);
            if (pointer < 0x40 || pointer > bytes.length - 26L) return "Cabecalho PE fora do trecho lido (limite: 1 MiB).";
            int pe = (int)pointer;
            if (u32(bytes, pe) != 0x4550) return "MZ encontrado, mas sem assinatura PE valida.";
            int machine = u16(bytes, pe + 4), sections = u16(bytes, pe + 6);
            int optionalSize = u16(bytes, pe + 20), optional = pe + 24;
            if (optionalSize < 20 || optionalSize > bytes.length - optional)
                return "Optional header ausente ou truncado.";
            int magic = u16(bytes, optional);
            if (magic != 0x10b && magic != 0x20b) return "Optional header nao e PE32/PE32+.";
            String architecture = machine == 0x14c ? "x86 (32 bits)" : machine == 0x8664 ? "x86-64" :
                machine == 0xaa64 ? "ARM64" : "0x" + Integer.toHexString(machine);
            return "Arquitetura: " + architecture + "\nFormato: " + (magic == 0x10b ? "PE32" : "PE32+") +
                "\nSecoes declaradas: " + sections + "\nEntry RVA: 0x" + Long.toHexString(u32(bytes, optional + 16)) +
                "\n\nSomente inspecao do cabecalho. Este prototipo ainda nao carrega nem executa Windows EXE.";
        } catch (IllegalArgumentException e) { return "Cabecalho truncado ou invalido."; }
    }
}
