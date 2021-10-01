package fu

import chisel3._
import chisel3.util._
import config.Config
import isa._

class CSREventIO extends Bundle with Config {
    // exception
    val is_ecall         = Input(Bool())
    val illegal_inst     = Input(Bool())
    val mem_access_fault = Input(Bool())
    // interrupt
    val external_int     = Input(Bool())
}

class CSRCommonIO extends Bundle with Config {
    val wen  = Input(Bool())
    val din  = Input(UInt(XLEN.W))
    val rd   = Input(UInt(CSRNumBits.W))
    val dout = Output(UInt(XLEN.W))
}

class CSRIO extends Bundle with Config {
    val event_io  = new CSREventIO
    val common_io = new CSRCommonIO
}

class CSR extends Module with Config {
    val io = IO(new CSRIO)
}