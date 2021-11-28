package mycore

import chisel3._
import chisel3.util._
import fu._
import isa._
import config._
import java.util.ResourceBundle

class FtoB extends Bundle with Config {
    val ctrl = Output(new CtrlInfo)
}

class BtoF extends Bundle with Config {
    val stall       = Output(Bool())
    val is_redirect = Output(Bool())
    val redirect_pc = Output(UInt(32.W))
    val bpu_update  = Flipped(new BPUUpdate)
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
    // val dmem = Flipped(new DmemIO)
    val dcache = Flipped(new CacheCoreIO)
    val external_int = Input(Bool())
}

class ImemIO extends Bundle with Config {
    val a   = Input(UInt(8.W))
    val spo = Output(UInt(32.W))
}

// mem_u_b_h_w(1,0) = 10 -> word
// mem_u_b_h_w(1,0) = 01 -> half word
// mem_u_b_h_w(1,0) = 00 -> byte
// mem_u_b_h_w[2] -> unsigned(ZEXT)
// class DmemIO extends Bundle with Config {
//     val addra = Input(UInt(32.W))
//     val clka  = Input(Clock())
//     val dina  = Input(UInt(32.W))
//     val wea   = Input(Bool())
//     val douta = Output(UInt(32.W))
//     val mem_u_b_h_w = Input(UInt(3.W))
//     val sim_uart_char_out = Output(UInt(8.W))
//     val sim_uart_char_valid = Output(Bool())
// }
class DmemIO extends Bundle with Config {
    val clk = Input(Clock())
	val rst = Input(Reset())
	val cs  = Input(Bool())
	val we  = Input(Bool())
	val addr = Input(UInt(XLEN.W))
	val din  = Input(UInt(XLEN.W))
	val dout = Output(UInt(XLEN.W))
	val stall = Output(Bool())
	val ack   = Output(Bool())
	val ram_state = Output(UInt(3.W))
}

// from the view of cache
class CacheCoreReq extends Bundle with CacheConfig with MemAccessType {
    val addr  = Output(UInt(addrWidth.W))
    val wen   = Output(Bool())
    val wdata = Output(UInt(dataWidth.W))
    val mtype = Output(UInt(MEMTYPE.W))
}

class CacheCoreResp extends Bundle with CacheConfig with MemAccessType {
    val rdata = Output(UInt(dataWidth.W))
}

class CacheCoreIO extends Bundle with CacheConfig with MemAccessType {
    val req  = Flipped(Decoupled(new CacheCoreReq)) // input valid, output ready
    val resp = Decoupled(new CacheCoreResp)         // input ready, output valid
}

// from the view of cache
class CacheMemReq extends Bundle with CacheConfig with MemAccessType {
    val addr  = Output(UInt(addrWidth.W))
    val wen   = Output(Bool())
    val wdata = Output(UInt(cachelineBits.W))
    val mtype = Output(UInt(MEMTYPE.W))
}

class CacheMemResp extends Bundle with CacheConfig with MemAccessType {
    val rdata = Output(UInt(cachelineBits.W))
}

class CacheMemIO extends Bundle with CacheConfig with MemAccessType {
    val req  = Decoupled(new CacheMemReq)           // input ready, output valid
    val resp = Flipped(Decoupled(new CacheMemResp)) // input valid, output ready
}

class DebugIO extends Bundle with CacheConfig with MemAccessType {
    val hit        = Output(Bool())
    val hitWay     = Output(UInt(1.W))
    val replaceWay = Output(UInt(1.W))
}

class CacheIO extends Bundle with CacheConfig with MemAccessType {
    val cpu = new CacheCoreIO
    val bar = new CacheMemIO
    val dbg = new DebugIO
}

class CoreDebugIO extends Bundle with Config {
    val fd = new FrontendDebugIO
    val bd = new BackendDebugIO
}

class CoreIO extends Bundle with Config {
    val imem      = Flipped(new ImemIO)
    val dcache    = Flipped(new CacheCoreIO)
    val debug     = new CoreDebugIO
    val interrupt = Input(Bool())
}