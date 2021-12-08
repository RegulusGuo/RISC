package config

trait Config {
    val XLEN = 32
    val MXLEN      = 32
    val SXLEN      = 32
    val CSRNumBits = 12
    
    val FIFO_SIZE = 8

    val bhtIndexBits = 8
    val bhtEntryNum  = 1 << bhtIndexBits
    val bhtTagBits   = XLEN - bhtIndexBits - 2

    val bpuBRANCH = 3
    val bpuJALR   = 2
    val bpuJAL    = 1
    val bpuOTHER  = 0
}