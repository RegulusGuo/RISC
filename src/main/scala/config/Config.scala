package config

trait Config {
    val XLEN = 32
    val MXLEN      = 32
    val SXLEN      = 32
    val CSRNumBits = 12
    
    val FIFO_SIZE = 8

    val bhtIndexBits = 7
    val bhtEntryNum  = 1 << bhtIndexBits
    val bhtTagBits   = XLEN - bhtIndexBits - 2

    val bpuBRANCH = 3
    val bpuJALR   = 2
    val bpuJAL    = 1
    val bpuOTHER  = 0
}

trait CacheConfig {
    val cachelineBits = 128  // 16 Bytes
    val addrWidth     = 32
    val dataWidth     = 32   // == register size
    val nway          = 2
    val nline         = 64 // 256 before
    val offsetBits    = 4
    val indexBits     = 6  // 8 before
    val tagBits       = addrWidth - offsetBits - indexBits
}

trait MemAccessType {
    val MEMBYTE  = 0
    val MEMHALF  = 1
    val MEMWORD  = 2
    val MEMDWORD = 3
    val MEMTYPE  = 2
}