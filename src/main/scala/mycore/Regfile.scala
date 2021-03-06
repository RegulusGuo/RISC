package mycore

import chisel3._
import chisel3.util._
import chisel3.util.experimental._

// issue order -> vec(0), vec(1), vec(2) ... ->> deal with WAW
class RegFileIO(nregs: Int = 32, len: Int = 32, nread: Int = 6, nwrite: Int = 3) extends Bundle {
    val rs_addr_vec = Input(Vec(nread, UInt(log2Ceil(nregs).W)))
    val rs_data_vec = Output(Vec(nread, UInt(len.W)))
    val wen_vec     = Input(Vec(nwrite, Bool()))
    val rd_addr_vec = Input(Vec(nwrite, UInt(log2Ceil(nregs).W)))
    val rd_data_vec = Input(Vec(nwrite, UInt(len.W)))
    override def cloneType = (new RegFileIO(nregs, len, nread, nwrite)).asInstanceOf[this.type]
}

class RegFile(nregs: Int = 32, len: Int = 64, nread: Int = 6, nwrite: Int = 3, verilator: Boolean = false) extends Module {
    val io = IO(new RegFileIO(nregs, len, nread, nwrite))

    val regs = RegInit(VecInit(Seq.fill(nregs)(0.U(len.W))))
    for (i <- 0 until nread) {
        io.rs_data_vec(i) := regs(io.rs_addr_vec(i))
    }
    for (i <- 0 until nwrite) {
        when(io.wen_vec(i) && io.rd_addr_vec(i).orR) {
            regs(io.rd_addr_vec(i)) := io.rd_data_vec(i)
        }
    }
}