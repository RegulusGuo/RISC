package mycore

import chisel3._
import chisel3.util._
import fu._
import isa._
import config.Config
import java.util.ResourceBundle

class FtoB extends Bundle with Config {
    val ctrl = Output(new CtrlInfo)
}

class BtoF extends Bundle with Config {
    val stall       = Output(Bool())
    val is_redirect = Output(Bool())
    val redirect_pc = Output(UInt(32.W))
}

class FrontBackIO extends Bundle with Config {
    val ftob = new FtoB
    val btof = Flipped(new BtoF)
}

class FrontendDebugIO extends Bundle with Config {
    val pc_if = Output(UInt(XLEN.W))
    val pc_id = Output(UInt(XLEN.W))
}

class BackendDebugIO extends Bundle with Config {
    val pc_is    = Output(UInt(XLEN.W))
    val pc_ex    = Output(UInt(XLEN.W))
    val pc_wb    = Output(UInt(XLEN.W))

    val stall    = Output(Bool())
    val redirect = Output(Bool())
    val trap_redirect = Output(Bool())
    val is_ecall = Output(Bool())
    
    val debug_addr = Input(UInt(7.W))
    val reg_data = Output(UInt(XLEN.W))
}

class FrontendIO extends Bundle with Config {
    val fb   = new FrontBackIO
    val fd   = new FrontendDebugIO
    val imem = Flipped(new ImemIO)
}

class BackendIO extends Bundle with Config {
    val bf   = Flipped(new FrontBackIO)
    val bd   = new BackendDebugIO
    val dmem = Flipped(new DmemIO)
    val external_int = Input(Bool())
}

class ImemIO extends Bundle with Config {
    val a   = Input(UInt(7.W))
    val spo = Output(UInt(32.W))
}

// mem_u_b_h_w(1,0) = 10 -> word
// mem_u_b_h_w(1,0) = 01 -> half word
// mem_u_b_h_w(1,0) = 00 -> byte
// mem_u_b_h_w[2] -> unsigned(ZEXT)
class DmemIO extends Bundle with Config {
    val addra = Input(UInt(32.W))
    val clka  = Input(Clock())
    val dina  = Input(UInt(32.W))
    val wea   = Input(Bool())
    val douta = Output(UInt(32.W))
    val mem_u_b_h_w = Input(UInt(3.W))
}

class CoreDebugIO extends Bundle with Config {
    val fd = new FrontendDebugIO
    val bd = new BackendDebugIO
}

class CoreIO extends Bundle with Config {
    // val clk   = Input(Clock())
    val imem  = Flipped(new ImemIO)
    val dmem  = Flipped(new DmemIO)
    val debug = new CoreDebugIO
    val interrupt = Input(Bool())
}