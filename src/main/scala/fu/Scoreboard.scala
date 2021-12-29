package fu

import chisel3._
import chisel3.util._
import config.Config

class SCBDIO(nFu: Int = 6) extends Bundle with Config {
    // IS: structure hazard(busy) or WAW(RRS(rd) =/= 0)? if no, update FUS & RRS using micro-ops
    val fu_is = Input(UInt(log2Ceil(nFu).W))
    val op    = Input(UInt(5.W))
    val rd_is = Input(UInt(5.W))
    val rs1   = Input(UInt(5.W))
    val rs2   = Input(UInt(5.W))
    val issue_wait = Output(Bool())
    // RO: RAW(rdy1 & rdy2)?
    val fu_ro  = Input(UInt(log2Ceil(nFu).W))
    val ro_rdy = Output(Bool())
    // FU:
    // WB: WAR()?
    // val done_vec = Input(Vec(nFu, Bool()))
    // val rd_vec   = Input(Vec(nFu, UInt(5.W)))
    val fu_sel = Input(UInt(log2Ceil(nFu).W))
    val rd_sel = Input(UInt(5.W))
}

trait FuNum {
    val nopNo = 0
    val aluNo = 1
    val bjuNo = 2
    val lsuNo = 3
    val mulNo = 4
    val divNo = 5
}

class FUS(nFu: Int = 6) extends Bundle with Config {
    val busy = Vec(nFu, Bool())
    val op   = Vec(nFu, UInt(5.W)) // add, sub, load, store ...
    val rd   = Vec(nFu, UInt(5.W))
    val rs1  = Vec(nFu, UInt(5.W))
    val rs2  = Vec(nFu, UInt(5.W))
    val fu1  = Vec(nFu, UInt(log2Ceil(nFu).W)) // alu, mul, div, mem, bju
    val fu2  = Vec(nFu, UInt(log2Ceil(nFu).W))
    val rdy1 = Vec(nFu, Bool())
    val rdy2 = Vec(nFu, Bool())
}

class RRS(nFu: Int = 6) extends Bundle with Config {
    val regs = Vec(32, UInt(log2Ceil(nFu).W))
}

class Scoreboard(nFu: Int = 6) extends Module with Config with FuNum {
    val io = IO(new SCBDIO)
    val fus = RegInit(0xFFF.U.asTypeOf(new FUS))
    val rrs = RegInit(0.U.asTypeOf(new RRS))

    // issue stage
    val struc_hazard = fus.busy(io.fu_is)
    val waw = rrs.regs(io.rd_is) =/= 0.U
    // val wait = struc_hazard || waw
    when (!(struc_hazard || waw || fus.busy(bjuNo) === true.B) && io.ro_rdy) {
        fus.busy(io.fu_is) := io.fu_is =/= nopNo.U
        fus.op(io.fu_is)   := io.op
        fus.rd(io.fu_is)   := io.rd_is
        fus.rs1(io.fu_is)  := io.rs1
        fus.rs2(io.fu_is)  := io.rs2
        fus.fu1(io.fu_is)  := rrs.regs(io.rs1)
        fus.fu2(io.fu_is)  := rrs.regs(io.rs2)
        fus.rdy1(io.fu_is) := rrs.regs(io.rs1) === 0.U
        fus.rdy2(io.fu_is) := rrs.regs(io.rs2) === 0.U
        rrs.regs(io.rd_is) := io.fu_is
    }
    io.issue_wait := struc_hazard || waw || fus.busy(bjuNo) === true.B
    // RO stage
    io.ro_rdy := (fus.rdy1(io.fu_ro) === 1.U || fus.rs1(io.fu_ro) === 0.U) && (fus.rdy2(io.fu_ro) === 1.U || fus.rs2(io.fu_ro) === 0.U) //&&
                //  (rrs.regs(io.rs1_ro) === 0.U && rrs.regs(io.rs2_ro) === 0.U)
    // FU stage
    // WB stage
    // val rd_sel = Mux(io.done_vec(bju), io.rd_vec(bju), Mux(io.done_vec(alu), io.rd_vec(alu), Mux(io.done_vec(mul), io.rd_vec(mul), Mux(io.done_vec(div), io.rd_vec(div), Mux(io.done_vec(mem), io.rd_vec(mem), 0.U)))))
    // val fu_sel = Mux(io.done_vec(bju), bju.U, Mux(io.done_vec(alu), alu.U, Mux(io.done_vec(mul), mul.U, Mux(io.done_vec(div), div.U, Mux(io.done_vec(mem), mem.U, nop.U)))))
    when (io.fu_sel =/= 0.U) {
        rrs.regs(io.rd_sel) := 0.U
        fus.busy(io.fu_sel) := false.B
        fus.op(io.fu_sel)   := 0.U
        fus.rd(io.fu_sel)   := 0.U
        fus.rs1(io.fu_sel)  := 0.U
        fus.rs2(io.fu_sel)  := 0.U
        fus.fu1(io.fu_sel)  := 0.U
        fus.fu2(io.fu_sel)  := 0.U
        fus.rdy1(io.fu_sel) := true.B
        fus.rdy2(io.fu_sel) := true.B
        for (i <- 0 until nFu) {
            when (fus.fu1(i) === io.fu_sel) {
                fus.fu1(i)  := 0.U
                fus.rdy1(i) := true.B
            }
            when (fus.fu2(i) === io.fu_sel) {
                fus.fu2(i)  := 0.U
                fus.rdy2(i) := true.B
            }
        }
    }
    
}