package mycore

import chisel3._
import chisel3.util._
import fu._
import fu.AluOpType
import isa._
import InstMacro._
import mem._
import config.Config

class PCGenIO(addr_width: Int = 32) extends Bundle with Config {
    val is_redirect = Input(Bool())
    val is_stall    = Input(Bool())
    val pc_redirect = Input(UInt(addr_width.W))
    val pc_o        = Output(UInt(addr_width.W))
}

class PCGen(addr_width: Int = 32, start_addr: String = "h00000000") extends Module with Config {
    val io = IO(new PCGenIO(addr_width))

    val pc = RegInit(UInt(addr_width.W), start_addr.U)
    val next_pc = Mux(io.is_redirect, io.pc_redirect, Mux(io.is_stall, pc, pc + 4.U))
    pc := next_pc
    io.pc_o := pc
}

class Frontend(addr_width: Int = 32, start_addr: String = "h00000000") extends Module with Config with AluOpType {
    val io = IO(new FrontendIO)
    val nop = {
        val tmp = Wire(new CtrlInfo)
        tmp.pc := 0.U
        tmp.next_pc := PC4
        tmp.illegal_inst := false.B
        tmp.rs1 := 0.U
        tmp.rs2 := 0.U
        tmp.rd  := 0.U
        tmp.imm := 0.U
        tmp.which_fu := TOALU
        tmp.alu_op   := aluAdd.U
        tmp.alu_src_a := AXXX
        tmp.alu_src_b := BXXX
        tmp.br_type := BRXX
        tmp.ls_width := MEMXXX
        tmp.wb_src := SXXX
        tmp.wb_dest := DXXX
        tmp
    }
    //---------- IF ----------
    val pcgen         = Module(new PCGen(addr_width, start_addr))
    val illegal_pc    = pcgen.io.pc_o(1, 0).orR()
    val stall_if      = WireDefault(false.B)
    val stall_pc      = RegInit(UInt(addr_width.W), start_addr.U)
    val redirect_if   = Wire(Bool())
    val imem_req_addr = Mux(stall_if, stall_pc, pcgen.io.pc_o)

    //---------- ID ----------
    val decoder     = Module(new Decoder)
    val stall_id    = WireDefault(false.B)
    val redirect_id = Wire(Bool())
    val decode_pc   = RegInit(UInt(addr_width.W), start_addr.U)
    val decode_inst = RegInit(0.U(XLEN.W))
    

    // IF stage
    stall_if := stall_id
    // 1) get pc
    redirect_if := redirect_id
    pcgen.io.is_stall    := stall_if
    pcgen.io.is_redirect := io.fb.btof.is_redirect
    pcgen.io.pc_redirect := io.fb.btof.redirect_pc
    stall_pc := Mux(stall_if, stall_pc, pcgen.io.pc_o)
    // 2) fetch inst
    io.imem.a := imem_req_addr(8, 2)

    // ID stage
    stall_id    := io.fb.btof.stall
    redirect_id := io.fb.btof.is_redirect
    decode_pc   := Mux(redirect_id, 0.U, imem_req_addr)  // actually this is not necessary, but this makes it easier for me to discover the bubble
    decode_inst := Mux(redirect_id, 0.U, io.imem.spo)
    decoder.io.pc   := decode_pc
    decoder.io.inst := decode_inst
    io.fb.ftob.ctrl := Mux(redirect_id, nop, decoder.io.ctrl)

    io.fd.pc_if := imem_req_addr
    io.fd.pc_id := decode_pc
}