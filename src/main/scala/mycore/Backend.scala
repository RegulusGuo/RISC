package mycore

import chisel3._
import chisel3.util._
import fu._
import fu.AluOpType
import isa._
import InstMacro._
import mem._
import config.Config

class Backend extends Module with Config with AluOpType {
    val io = IO(new BackendIO)

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
        tmp.alu_op := aluAdd.U
        tmp.alu_src_a := AXXX
        tmp.alu_src_b := BXXX
        tmp.br_type := BRXX
        tmp.ls_width := MEMXXX
        tmp.wb_src := SXXX
        tmp.wb_dest := DXXX
        tmp
    }

    // ISSUE
    // val issue_queue  = Module(new FIFO(FIFO_SIZE, new CtrlInfo, 1.U, 1.U))
    // val issue_inst   = Wire(new CtrlInfo)
    val issue_inst   = RegInit(nop)
    val rs1_fwd_data = Wire(UInt(XLEN.W))
    val rs2_fwd_data = Wire(UInt(XLEN.W))
    val rs1_data     = Wire(UInt(XLEN.W))
    val rs2_data     = Wire(UInt(XLEN.W))
    val stall_is     = WireDefault(false.B)
    val redirect_is  = WireDefault(false.B)

    // EX
    val alu = Module(new ALU)
    val ex_inst = RegInit(nop)
    val ex_inst_valid = RegInit(false.B)
    val ex_inst_data_valid = Wire(Bool())
    val ex_rs1_data = Reg(UInt(XLEN.W))
    val ex_rs2_data = Reg(UInt(XLEN.W))

    val ex_rs1_true_data = Wire(UInt(XLEN.W))
    val ex_rs2_true_data = Wire(UInt(XLEN.W))

    val stall_ex    = WireDefault(false.B)
    val redirect_ex = redirect_is
    val alu_valid = Wire(Bool())
    
    val is_branch = ex_inst.next_pc === BRANCH
    val is_jump   = ex_inst.next_pc === JUMP || ex_inst.next_pc === JUMPREG
    val ex_brj    = Wire(Bool())
    val brj_taken = WireDefault(false.B)
    val br_pc    = ex_inst.pc + ex_inst.imm
    val jump_pc  = Wire(UInt(XLEN.W))
    val ex_brj_pc = Mux(is_branch, Mux(brj_taken, br_pc, ex_inst.pc + 4.U), jump_pc)
    
    val lsu_valid = Wire(Bool())
    // val ls_addr = ex_rs1_data + ex_inst.imm
    val ls_addr = ex_rs1_true_data + ex_inst.imm

    // WB
    val wb_inst = RegInit(nop)
    val wb_inst_valid = RegInit(true.B)
    val stall_wb    = WireDefault(false.B)
    val regFile = Module(new RegFile(len = 32, nread = 3, nwrite = 1))
    val csr     = Module(new CSR)
    val wb_csr_data = Reg(UInt(XLEN.W))
    val wb_alu_data = Wire(UInt(XLEN.W))
    val wb_lsu_data = Wire(UInt(XLEN.W))
    val wb_data = Wire(UInt(XLEN.W))
    val wb_result = Reg(UInt(XLEN.W))
    val wb_brj = RegInit(false.B)
    val wb_brj_pc = Reg(UInt(XLEN.W))

    //----------ISSUE----------
    // issue_queue.io.enqStep := 1.U
    // issue_queue.io.deqStep := 1.U
    // issue_queue.io.flush   := redirect_is
    // issue_queue.io.deqReq  := !stall_is
    // issue_queue.io.enqReq  := true.B

    // issue_queue.io.din(0)  := io.bf.ftob.ctrl
    // issue_inst := issue_queue.io.dout(0)
    redirect_is := ex_brj
    issue_inst := Mux(redirect_is || stall_is, nop, io.bf.ftob.ctrl)

    // detect use after load (stall 1 cycle)
    // stall_is := issue_inst.which_fu === TOLSU && issue_inst.wb_dest === DREG &&
    //            (issue_inst.rd === io.bf.ftob.ctrl.rs1 || issue_inst.rd === io.bf.ftob.ctrl.rs2)
    io.bf.btof.stall := stall_is
    
    
    // from regfile
    rs1_fwd_data := rs1_data
    rs2_fwd_data := rs2_data
    // wb forwarding to is
    when (wb_inst_valid && wb_inst.wb_dest === DREG && wb_inst.rd =/= 0.U) {
        when(wb_inst.rd === issue_inst.rs1) {
            rs1_fwd_data := wb_data
        }
        when(wb_inst.rd === issue_inst.rs2) {
            rs2_fwd_data := wb_data
        }
    }
    // ex forwarding to is
    when (ex_inst_valid && ex_inst.wb_dest === DREG && ex_inst.rd =/= 0.U) {
        when(ex_inst.rd === issue_inst.rs1) {
            rs1_fwd_data := wb_alu_data
        }
        when(ex_inst.rd === issue_inst.rs2) {
            rs2_fwd_data := wb_alu_data
        }
    }
    // read regfile
    regFile.io.rs_addr_vec(0) := issue_inst.rs1
    regFile.io.rs_addr_vec(1) := issue_inst.rs2
    rs1_data := regFile.io.rs_data_vec(0)
    rs2_data := regFile.io.rs_data_vec(1)

    //----------EX----------
    ex_inst := Mux(redirect_ex, nop, issue_inst)
    ex_inst_valid := true.B
    ex_rs1_data := rs1_fwd_data
    ex_rs2_data := rs2_fwd_data
    val reforward_rs1 = wb_inst.which_fu === TOLSU && wb_inst.wb_dest === DREG && (wb_inst.rd === ex_inst.rs1)
    val reforward_rs2 = wb_inst.which_fu === TOLSU && wb_inst.wb_dest === DREG && (wb_inst.rd === ex_inst.rs2)
    ex_rs1_true_data := Mux(reforward_rs1, wb_data, ex_rs1_data)
    ex_rs2_true_data := Mux(reforward_rs2, wb_data, ex_rs2_data)

    alu_valid := ex_inst_valid
    lsu_valid := ex_inst_valid

    // alu
    val is_csrc = ex_inst.alu_op === aluAnd.U && ex_inst.alu_src_b === BCSR
    alu.io.a := MuxLookup(
        ex_inst.alu_src_a,
        Mux(is_csrc, ~ex_rs1_true_data, ex_rs1_true_data),
        Seq(
            APC  -> ex_inst.pc,
            AIMM-> Mux(is_csrc, Cat(Fill(XLEN - 5, 1.U(1.W)), ~ex_inst.rs1),
                                Cat(Fill(XLEN - 5, 0.U(1.W)),  ex_inst.rs1))
        )
    )
    alu.io.b := MuxLookup(
        ex_inst.alu_src_b,
        ex_rs2_true_data,
        Seq(
            BIMM -> ex_inst.imm,
            BCSR -> MuxLookup( ex_inst.alu_op, csr.io.common_io.dout,
                Seq( aluAdd.U -> 0.U(XLEN.W) ))
        )
    )
    alu.io.op := ex_inst.alu_op

    // bju (after ALU)
    jump_pc := ex_inst.imm + Mux(ex_inst.next_pc === JUMP, ex_inst.pc, ex_rs1_true_data)
    when (ex_inst_valid) {
        when (is_branch) {
            brj_taken := MuxLookup (
                ex_inst.br_type, alu.io.zero === 1.U,
                Seq(
                    BRNE  -> (alu.io.zero === 0.U),
                    BRGE  -> (alu.io.res.asSInt() >= 0.S),
                    BRLT  -> (alu.io.res.asSInt() < 0.S),
                    BRGEU -> (alu.io.res(0) === 0.U),
                    BRLTU -> (alu.io.res(0) === 1.U)
                )
            )
        }.elsewhen (is_jump) {
            brj_taken := true.B
        }
    }
    ex_brj := Mux(ex_inst_valid, Mux(is_branch || is_jump, brj_taken, false.B), false.B)
    // lsu
    io.dmem.addra := ls_addr
    // io.dmem.dina  := ex_rs2_data
    io.dmem.dina  := ex_rs2_true_data
    io.dmem.wea   := ex_inst.wb_dest === DMEM
    io.dmem.mem_u_b_h_w := ex_inst.ls_width
    io.dmem.clka := clock

    ex_inst_data_valid := Mux(ex_inst.which_fu === TOLSU, lsu_valid, alu_valid)

    io.bf.btof.is_redirect := ex_brj
    io.bf.btof.redirect_pc := ex_brj_pc

    //----------WB----------
    // wb from alu
    wb_alu_data := Mux(ex_inst.wb_src === SPC || ex_inst.next_pc =/= PC4,
                    ex_inst.pc + 4.U, alu.io.res)
    // wb from lsu
    wb_lsu_data := io.dmem.douta

    wb_result := Mux(ex_inst.which_fu === TOLSU, wb_lsu_data, wb_alu_data)
    wb_data := wb_result
    wb_inst   := ex_inst

    // write regfile
    regFile.io.wen_vec(0)     := Mux(wb_inst_valid, wb_inst.wb_dest === DREG, false.B)
    regFile.io.rd_addr_vec(0) := Mux(wb_inst_valid, wb_inst.rd, 0.U)
    regFile.io.rd_data_vec(0) := Mux(wb_inst_valid, wb_data, 0.U)

    // write CSR
    csr.io.common_io.wen := wb_inst_valid && wb_inst.which_fu === TOBJU && wb_inst.next_pc === PC4
    csr.io.common_io.rd  := Mux(csr.io.common_io.wen, wb_inst.imm(12, 0), ex_inst.imm(12, 0))
    csr.io.common_io.din := wb_csr_data
    csr.io.event_io := DontCare // FIXME: For compile

    // debug signals
    io.bd.pc_is    := issue_inst.pc
    io.bd.pc_ex    := ex_inst.pc
    io.bd.pc_wb    := wb_inst.pc
    regFile.io.rs_addr_vec(2) := io.bd.debug_addr(4, 0)
    io.bd.reg_data := regFile.io.rs_data_vec(2)
}