package fu

import chisel3._
import chisel3.util._
import isa._
import InstMacro._
import config.Config

class BJUIO extends Bundle with Config {
    val clk  = Input(Clock())
    val EN   = Input(Bool())
    val JALR = Input(Bool())
    val cmp_ctrl = Input(UInt(4.W))
    val rs1_data = Input(UInt(XLEN.W))
    val rs2_data = Input(UInt(XLEN.W))
    val imm      = Input(UInt(XLEN.W))
    val PC       = Input(UInt(XLEN.W))
    val PC_jump  = Output(UInt(XLEN.W))
    val PC_wb    = Output(UInt(XLEN.W))
    val is_jump  = Output(Bool())
    val finish   = Output(Bool())
}

class FU_jump extends BlackBox with HasBlackBoxResource {
    val io = IO(new BJUIO)
    addResource("/FU_jump.v")
}