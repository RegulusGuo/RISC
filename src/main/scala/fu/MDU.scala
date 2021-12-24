package fu

import chisel3._
import chisel3.util._
import isa._
import InstMacro._
import config.Config

class MULIO extends Bundle with Config {
    val clk = Input(Clock())
    val EN  = Input(Bool())
    val A   = Input(UInt(XLEN.W))
    val B   = Input(UInt(XLEN.W))
    val res = Output(UInt(XLEN.W))
    val finish = Output(Bool())
}

class FU_mul extends BlackBox with HasBlackBoxResource {
    val io = IO(new MULIO)
    addResource("/FU_mul.v")
}

class DIVIO extends Bundle with Config {
    val clk = Input(Clock())
    val EN  = Input(Bool())
    val A   = Input(UInt(XLEN.W))
    val B   = Input(UInt(XLEN.W))
    val res = Output(UInt(XLEN.W))
    val finish = Output(Bool())
}

class FU_div extends BlackBox with HasBlackBoxResource {
    val io = IO(new DIVIO)
    addResource("/FU_div.v")
}