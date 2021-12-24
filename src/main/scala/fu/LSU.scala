package fu

import chisel3._
import chisel3.util._
import isa._
import InstMacro._
import config.Config

class MEMIO extends Bundle with Config {
    val clk = Input(Clock())
    val EN  = Input(Bool())
    val mem_w = Input(Bool())
    val bhw   = Input(UInt(3.W))
    val rs1_data = Input(UInt(XLEN.W))
    val rs2_data = Input(UInt(XLEN.W))
    val imm      = Input(UInt(XLEN.W))
    
    val addra = Output(UInt(XLEN.W))
    val dina  = Output(UInt(XLEN.W))
    val wea   = Output(Bool())
    val mem_u_b_h_w = Output(UInt(LS_WIDTH_SIZE.W))
    val finish   = Output(Bool())
}

class FU_mem extends BlackBox with HasBlackBoxResource {
    val io = IO(new MEMIO)
    addResource("/FU_mem.v")
}