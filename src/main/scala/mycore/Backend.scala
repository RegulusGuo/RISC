package mycore

import chisel3._
import chisel3.util._
import fu._
import fu.AluOpType
import fu.FuNum
import isa._
import InstMacro._
import mem._
import config.Config
import scala.annotation.switch

class Backend(nFu: Int = 6) extends Module with Config with AluOpType with FuNum {
    val io = IO(new BackendIO)

    val nop = {
        val tmp = Wire(new CtrlInfo)
        tmp.taken_predict := false.B
        tmp.target_predict := 0.U
        tmp.inst := 0x13.U
        tmp.pc   := 0.U
        tmp.next_pc := PC4
        tmp.illegal_inst := false.B
        tmp.rs1 := 0.U
        tmp.rs2 := 0.U
        tmp.rd  := 0.U
        tmp.imm := 0.U
        tmp.which_fu := TOXXX
        tmp.alu_op := aluAdd.U
        tmp.alu_src_a := AXXX
        tmp.alu_src_b := BXXX
        tmp.br_type := BRXX
        tmp.ls_width := MEMXXX
        tmp.wb_src := SXXX
        tmp.wb_dest := DXXX
        tmp
    }

    val scbd = Module(new Scoreboard)
    scbd.io.fu_is := io.bf.ftob.ctrl.which_fu
    scbd.io.rd_is := Mux(io.bf.ftob.ctrl.wb_dest === DREG, io.bf.ftob.ctrl.rd, 0.U)
    scbd.io.rs1   := MuxLookup(io.bf.ftob.ctrl.which_fu, io.bf.ftob.ctrl.rs1,
                        Seq(
                            TOALU -> Mux(io.bf.ftob.ctrl.alu_src_a === AREG, io.bf.ftob.ctrl.rs1, 0.U),
                            TOBJU -> Mux(io.bf.ftob.ctrl.alu_src_a === AREG, io.bf.ftob.ctrl.rs1, 0.U)
                        ))
    scbd.io.rs2   := MuxLookup(io.bf.ftob.ctrl.which_fu, io.bf.ftob.ctrl.rs2,
                        Seq(
                            TOALU -> Mux(io.bf.ftob.ctrl.alu_src_b === BREG, io.bf.ftob.ctrl.rs2, 0.U),
                            TOBJU -> Mux(io.bf.ftob.ctrl.alu_src_b === BREG, io.bf.ftob.ctrl.rs2, 0.U),
                            TOLSU -> Mux(io.bf.ftob.ctrl.wb_dest === DREG, 0.U, io.bf.ftob.ctrl.rs2)
                        ))
    scbd.io.op    := 0.U
    
    // ISSUE (READ OPERAND IN SCOREBOARD)
    // val issue_queue  = Module(new FIFO(FIFO_SIZE, new CtrlInfo, 1.U, 1.U))
    // val issue_inst   = Wire(new CtrlInfo)
    val issue_inst   = RegInit(nop)
    // val issue_inst_valid = RegInit(false.B)
    val issue_inst_valid = RegInit(true.B)
    val rs1_fwd_data = Wire(UInt(XLEN.W))
    val rs2_fwd_data = Wire(UInt(XLEN.W))
    val rs1_data     = Wire(UInt(XLEN.W))
    val rs2_data     = Wire(UInt(XLEN.W))
    val stall_is     = WireDefault(false.B)
    val redirect_is  = WireDefault(false.B)

    // EX
    val alu  = Module(new FU_ALU)
    val mulu = Module(new FU_mul)
    val divu = Module(new FU_div)
    val lsu  = Module(new FU_mem)
    val bju  = Module(new FU_jump)
    val ex_inst  = RegInit(nop)
    val alu_inst = RegInit(nop)
    val bju_inst = RegInit(nop)
    val lsu_inst = RegInit(nop)
    val mul_inst = RegInit(nop)
    val div_inst = RegInit(nop)
    val nop_inst = RegInit(nop)
    // val ex_inst_vec = RegInit(VecInit(Seq.fill(nFu)(nop)))
    val inst_is_ex  = Mux(stall_is, nop, issue_inst)
    // val ex_inst_valid      = RegInit(false.B)
    val ex_inst_valid      = RegInit(true.B)
    val ex_inst_data_valid = Wire(Bool())
    val ex_rs1_data = RegInit(0.U(XLEN.W))
    val ex_rs2_data = RegInit(0.U(XLEN.W))

    val ex_rs1_true_data = WireDefault(0.U(XLEN.W))
    val ex_rs2_true_data = WireDefault(0.U(XLEN.W))

    val stall_ex    = WireDefault(false.B)
    val redirect_ex = WireDefault(false.B)
    val alu_valid   = Wire(Bool())
    
    val is_branch = ex_inst.next_pc === BRANCH
    val is_jump   = ex_inst.next_pc === JUMP || ex_inst.next_pc === JUMPREG
    // val ex_brj    = WireDefault(false.B)
    val ex_brj    = RegInit(false.B)
    // val brj_taken = WireDefault(false.B)
    // val br_pc     = ex_inst.pc + ex_inst.imm
    // val jump_pc   = Wire(UInt(XLEN.W))
    // val ex_brj_pc = Mux(is_branch, Mux(brj_taken, br_pc, ex_inst.pc + 4.U), jump_pc)
    val ex_brj_pc = Mux(bju.io.is_jump, bju.io.PC_jump, bju.io.PC_wb)
    
    val lsu_valid = Wire(Bool())
    val ls_addr = ex_rs1_true_data + ex_inst.imm

    val ex_interrupt = RegInit(false.B)

    // WB
    val wb_inst       = RegInit(nop)
    // val wb_inst_valid = RegInit(false.B)
    val wb_inst_valid = RegInit(true.B)
    val stall_wb      = WireDefault(false.B)
    val regFile       = Module(new RegFile(len = 32, nread = 3, nwrite = 1))
    val csr           = Module(new CSR)
    val wb_csr_data   = RegInit(0.U(XLEN.W))
    val wb_alu_data   = Wire(UInt(XLEN.W))
    val wb_lsu_data   = Wire(UInt(XLEN.W))
    val wb_data       = Wire(UInt(XLEN.W))
    val wb_result     = RegInit(0.U(XLEN.W))
    val wb_brj        = RegInit(false.B)
    val wb_brj_pc     = Reg(UInt(XLEN.W))
    val wb_interrupt  = RegInit(false.B)
    val done_vec      = RegInit(VecInit(Seq.fill(nFu)(false.B)))
    val rd_vec        = RegInit(VecInit(Seq.fill(nFu)(0.U(5.W))))
    val res_vec       = RegInit(VecInit(Seq.fill(nFu)(0.U(XLEN.W))))

    //----------ISSUE (ACTUALLY RO IN SCOREBOARD)----------
    // issue_queue.io.enqStep := 1.U
    // issue_queue.io.deqStep := 1.U
    // issue_queue.io.flush   := redirect_is
    // issue_queue.io.deqReq  := !stall_is
    // issue_queue.io.enqReq  := true.B

    // issue_queue.io.din(0)  := io.bf.ftob.ctrl
    // issue_inst := issue_queue.io.dout(0)
    // redirect_is := ex_brj || csr.io.event_io.trap_redirect

    issue_inst := Mux(redirect_is, nop, Mux(stall_is, issue_inst, Mux(scbd.io.issue_wait, nop, io.bf.ftob.ctrl)))
    scbd.io.fu_ro  := issue_inst.which_fu

    redirect_is := redirect_ex
    issue_inst_valid := ~redirect_is && ~stall_is

    // detect use after load (stall 1 cycle)
    // stall_is := issue_inst.which_fu === TOLSU && issue_inst.wb_dest === DREG &&
    //            (issue_inst.rd === io.bf.ftob.ctrl.rs1 || issue_inst.rd === io.bf.ftob.ctrl.rs2)
    // stall_is := stall_ex
    stall_is := !scbd.io.ro_rdy
    io.bf.btof.stall := (stall_is || scbd.io.issue_wait) && !redirect_is
    
    // from regfile
    rs1_fwd_data := rs1_data
    rs2_fwd_data := rs2_data

    // NOTE: no forwarding in SCOREBOARD
    // // wb forwarding to is
    // when (wb_inst_valid && wb_inst.wb_dest === DREG && wb_inst.rd =/= 0.U) {
    //     when(wb_inst.rd === issue_inst.rs1) {
    //         rs1_fwd_data := wb_data
    //     }
    //     when(wb_inst.rd === issue_inst.rs2) {
    //         rs2_fwd_data := wb_data
    //     }
    // }
    // // ex forwarding to is
    // when (ex_inst_valid && ex_inst.wb_dest === DREG && ex_inst.rd =/= 0.U) {
    //     when(ex_inst.rd === issue_inst.rs1) {
    //         rs1_fwd_data := wb_alu_data
    //     }
    //     when(ex_inst.rd === issue_inst.rs2) {
    //         rs2_fwd_data := wb_alu_data
    //     }
    // }

    // read regfile
    // regFile.io.rs_addr_vec(0) := issue_inst.rs1
    // regFile.io.rs_addr_vec(1) := issue_inst.rs2
    regFile.io.rs_addr_vec(0) := inst_is_ex.rs1
    regFile.io.rs_addr_vec(1) := inst_is_ex.rs2
    rs1_data := regFile.io.rs_data_vec(0)
    rs2_data := regFile.io.rs_data_vec(1)

    //----------EX----------
    val nop_with_pc = WireDefault(nop)
    nop_with_pc.pc := issue_inst.pc
    // ex_inst := Mux(csr.io.event_io.validated_int, nop_with_pc, Mux(redirect_ex || stall_ex, nop, issue_inst))
    when (csr.io.event_io.validated_int) {
        ex_inst := nop_with_pc
    }.elsewhen (redirect_ex) {
        ex_inst := nop
    }.elsewhen (!stall_ex) {
        ex_inst := inst_is_ex
        alu.io.EN  := inst_is_ex.which_fu === TOALU
        bju.io.EN  := inst_is_ex.which_fu === TOBJU
        lsu.io.EN  := inst_is_ex.which_fu === TOLSU
        mulu.io.EN := inst_is_ex.which_fu === TOMUL
        divu.io.EN := inst_is_ex.which_fu === TODIV
        switch (inst_is_ex.which_fu) {
            is(TOALU) {
                alu_inst := inst_is_ex
                alu.io.ALUControl := inst_is_ex.alu_op
                alu.io.ALUA := MuxLookup(
                        inst_is_ex.alu_src_a,
                        rs1_data,
                        Seq(
                            APC  -> inst_is_ex.pc,
                            AIMM -> Cat(Fill(XLEN - 5, 0.U(1.W)),  inst_is_ex.rs1)
                        ))
                alu.io.ALUB := MuxLookup(
                        inst_is_ex.alu_src_b,
                        rs2_data,
                        Seq(
                            BIMM -> inst_is_ex.imm
                        ))
            }
            is(TOBJU) {
                bju_inst := inst_is_ex
                bju.io.JALR := inst_is_ex.next_pc === JUMPREG
                bju.io.cmp_ctrl := Cat(inst_is_ex.br_type, inst_is_ex.next_pc === JUMP || inst_is_ex.next_pc === JUMPREG)
                bju.io.rs1_data := rs1_data
                bju.io.rs2_data := rs2_data
                bju.io.imm      := inst_is_ex.imm
                bju.io.PC := inst_is_ex.pc
            }
            is(TOLSU) {
                lsu_inst := inst_is_ex
                lsu.io.mem_w := inst_is_ex.wb_dest === DMEM
                lsu.io.bhw := inst_is_ex.ls_width
                lsu.io.rs1_data := rs1_data
                lsu.io.rs2_data := rs2_data
                lsu.io.imm      := inst_is_ex.imm
            }
            is(TOMUL) {
                mul_inst  := inst_is_ex
                mulu.io.A := rs1_data
                mulu.io.B := rs2_data
            }
            is(TODIV) {
                div_inst  := inst_is_ex
                divu.io.A := rs1_data
                divu.io.B := rs2_data
            }
            is(TOXXX) {
                nop_inst := inst_is_ex
            }
        }
    }
    alu.io.clk  := clock
    bju.io.clk  := clock
    lsu.io.clk  := clock
    mulu.io.clk := clock
    divu.io.clk := clock

    ex_inst_valid := issue_inst_valid
    when (!stall_is) {
        ex_rs1_data := rs1_fwd_data
        ex_rs2_data := rs2_fwd_data
    }
    // val reforward_rs1 = wb_inst.which_fu === TOLSU && wb_inst.wb_dest === DREG && (wb_inst.rd === ex_inst.rs1)
    // val reforward_rs2 = wb_inst.which_fu === TOLSU && wb_inst.wb_dest === DREG && (wb_inst.rd === ex_inst.rs2)
    // ex_rs1_true_data := Mux(reforward_rs1, wb_data, ex_rs1_data)
    // ex_rs2_true_data := Mux(reforward_rs2, wb_data, ex_rs2_data)
    ex_rs1_true_data := ex_rs1_data
    ex_rs2_true_data := ex_rs2_data

    alu_valid := ex_inst_valid
    lsu_valid := ex_inst_valid

    // alu
    // val is_csrc = ex_inst.alu_op === aluAnd.U && ex_inst.alu_src_b === BCSR
    // alu.io.a := MuxLookup(
    //     ex_inst.alu_src_a,
    //     Mux(is_csrc, ~ex_rs1_true_data, ex_rs1_true_data),
    //     Seq(
    //         APC  -> ex_inst.pc,
    //         AIMM-> Mux(is_csrc, Cat(Fill(XLEN - 5, 1.U(1.W)), ~ex_inst.rs1),
    //                             Cat(Fill(XLEN - 5, 0.U(1.W)),  ex_inst.rs1))
    //     )
    // )
    // alu.io.b := MuxLookup(
    //     ex_inst.alu_src_b,
    //     ex_rs2_true_data,
    //     Seq(
    //         BIMM -> ex_inst.imm,
    //         BCSR -> MuxLookup( ex_inst.alu_op, 
    //                     Mux(csrNeedForward(wb_inst, ex_inst), csr.io.common_io.din, csr.io.common_io.dout),
    //                     Seq( aluAdd.U -> 0.U(XLEN.W) ))
    //     )
    // )
    // alu.io.op := ex_inst.alu_op

    // bju (after ALU)
    // jump_pc := ex_inst.imm + Mux(ex_inst.next_pc === JUMP, ex_inst.pc, ex_rs1_true_data)
    // when (ex_inst_valid) {
    //     when (is_branch) {
    //         brj_taken := MuxLookup (
    //             ex_inst.br_type, alu.io.zero === 1.U,
    //             Seq(
    //                 BRNE  -> (alu.io.zero === 0.U),
    //                 BRGE  -> (alu.io.res.asSInt() >= 0.S),
    //                 BRLT  -> (alu.io.res.asSInt() < 0.S),
    //                 BRGEU -> (alu.io.res(0) === 0.U),
    //                 BRLTU -> (alu.io.res(0) === 1.U)
    //             )
    //         )
    //     }.elsewhen (is_jump) {
    //         brj_taken := true.B
    //     }
    // }
    // ex_brj := Mux(ex_inst_valid, Mux(is_branch || is_jump, brj_taken, false.B), false.B)
    ex_brj := bju.io.is_jump && bju.io.finish
    redirect_ex := (ex_brj  && (ex_inst.taken_predict === false.B  || ex_inst.target_predict =/= ex_brj_pc)) ||
                   (~ex_brj && (ex_inst.taken_predict === true.B)) 
    io.bf.btof.is_redirect := redirect_ex || csr.io.event_io.trap_redirect
    io.bf.btof.redirect_pc := Mux(redirect_ex, ex_brj_pc, csr.io.event_io.redirect_pc)

    def isCall(inst: UInt): Bool = {
        inst(14, 0) === BitPat("b000000011100111") || inst(11, 0) === BitPat("b000011101111")
    }
    def isRet(inst: UInt): Bool = {
        inst(31, 0) === BitPat("b00000000000000001000000001100111")
    }
    io.bf.btof.bpu_update.pc := ex_inst.pc
    // io.bf.btof.bpu_update.inst := ex_inst.inst
    io.bf.btof.bpu_update.is_call     := isCall(ex_inst.inst)
    io.bf.btof.bpu_update.is_ret      := isRet(ex_inst.inst)
    io.bf.btof.bpu_update.real_taken  := ex_brj
    io.bf.btof.bpu_update.real_target := ex_brj_pc
    io.bf.btof.bpu_update.inst_type   := MuxLookup(ex_inst.next_pc, bpuOTHER.U,
                                                Seq(
                                                    BRANCH  -> bpuBRANCH.U,
                                                    JUMP    -> bpuJAL.U,
                                                    JUMPREG -> bpuJALR.U
                                                )
                                            )

    // lsu
    val ex_mem_req_valid = true.B
    io.dmem.addra := lsu.io.addra
    io.dmem.dina  := lsu.io.dina
    io.dmem.wea   := lsu.io.wea
    io.dmem.mem_u_b_h_w := lsu.io.mem_u_b_h_w
    io.dmem.clka := clock

    ex_inst_data_valid := Mux(ex_inst.which_fu === TOLSU, lsu_valid, alu_valid)

    // CSR
    def isMret(inst: CtrlInfo): Bool = {
        inst.next_pc === EPC
    }
    def isEcall(inst: CtrlInfo): Bool = {
        inst.next_pc === MTVEC
    }
    def isCsrRW(inst: CtrlInfo): Bool = {
        inst.next_pc === PC4 && inst.which_fu === TOBJU
    }
    // forwarding
    def csrNeedForward(inst1: CtrlInfo, inst2: CtrlInfo): Bool = {
        isCsrRW(inst1) && isCsrRW(inst2) && (inst1.imm(12, 0) === inst2.imm(12, 0))
    }
    // stall & interrupt
    // stall_ex := isMret(ex_inst) || isEcall(ex_inst) || 
    //            (!ex_mem_req_valid && ex_inst.which_fu === TOLSU) || ex_inst.illegal_inst
    ex_interrupt := csr.io.event_io.validated_int

    //----------WB----------
    // wb from alu
    // wb_alu_data := Mux(ex_inst.wb_src === SPC || ex_inst.next_pc =/= PC4,
    //                 ex_inst.pc + 4.U, 
    //                 MuxLookup(
    //                     ex_inst.alu_src_b, alu.io.res,
    //                     Seq(
    //                         BCSR -> Mux(csrNeedForward(wb_inst, ex_inst), 
    //                                     csr.io.common_io.din,
    //                                     csr.io.common_io.dout)
    //                     )
    //                 )
    //             )
    // // wb from lsu
    // val wb_mem_req_valid = RegInit(false.B)
    // wb_mem_req_valid := ex_mem_req_valid
    // val wb_ls_addr = RegInit(0.U(XLEN.W))
    // wb_ls_addr := ls_addr
    // wb_lsu_data := io.dmem.douta

    // wb_result := Mux(ex_inst.which_fu === TOLSU, Mux(ex_mem_req_valid, wb_lsu_data, 0.U), wb_alu_data)
    // wb_data   := wb_result
    // wb_inst   := ex_inst
    // wb_inst_valid := ex_inst_valid
    // wb_interrupt  := wb_inst_valid && ex_interrupt
    wb_data := DontCare
    wb_lsu_data := DontCare
    wb_alu_data := DontCare

    when (alu.io.finish) {
        done_vec(aluNo) := true.B
        rd_vec(aluNo)   := alu_inst.rd
        res_vec(aluNo)  := alu.io.res
    }
    when (bju.io.finish) {
        done_vec(bjuNo) := true.B
        rd_vec(bjuNo)   := Mux(bju_inst.wb_dest === DREG, bju_inst.rd, 0.U)
        res_vec(bjuNo)  := bju.io.PC_wb
    }
    when (lsu.io.finish) {
        done_vec(lsuNo) := true.B
        rd_vec(lsuNo)   := Mux(lsu_inst.wb_dest === DREG, lsu_inst.rd, 0.U)
        res_vec(lsuNo)  := io.dmem.douta
    }
    when (mulu.io.finish) {
        done_vec(mulNo) := true.B
        rd_vec(mulNo)   := mul_inst.rd
        res_vec(mulNo)  := mulu.io.res
    }
    when (divu.io.finish) {
        done_vec(divNo) := true.B
        rd_vec(divNo)   := div_inst.rd
        res_vec(divNo)  := divu.io.res
    }

    val rd_sel = Wire(UInt(5.W))
    val fu_sel = Wire(UInt(log2Ceil(nFu).W))
    when (done_vec(bjuNo)) {
        rd_sel := rd_vec(bjuNo)
        fu_sel := bjuNo.U
        done_vec(bjuNo) := false.B
        rd_vec(bjuNo)   := 0.U
    }.elsewhen (done_vec(aluNo)) {
        rd_sel := rd_vec(aluNo)
        fu_sel := aluNo.U
        done_vec(aluNo) := false.B
        rd_vec(aluNo)   := 0.U
    }.elsewhen (done_vec(mulNo)) {
        rd_sel := rd_vec(mulNo)
        fu_sel := mulNo.U
        done_vec(mulNo) := false.B
        rd_vec(mulNo)   := 0.U
    }.elsewhen (done_vec(divNo)) {
        rd_sel := rd_vec(divNo)
        fu_sel := divNo.U
        done_vec(divNo) := false.B
        rd_vec(divNo)   := 0.U
    }.elsewhen (done_vec(lsuNo)) {
        rd_sel := rd_vec(lsuNo)
        fu_sel := lsuNo.U
        done_vec(lsuNo) := false.B
        rd_vec(lsuNo)   := 0.U
    }.otherwise {
        rd_sel := 0.U
        fu_sel := 0.U
    }
    scbd.io.fu_sel := fu_sel
    scbd.io.rd_sel := rd_sel

    // write regfile
    // regFile.io.wen_vec(0)     := Mux(wb_inst_valid, wb_inst.wb_dest === DREG, false.B)
    // regFile.io.rd_addr_vec(0) := Mux(wb_inst_valid, wb_inst.rd, 0.U)
    // regFile.io.rd_data_vec(0) := Mux(wb_inst_valid && regFile.io.rd_addr_vec(0).orR, wb_data, 0.U)
    regFile.io.wen_vec(0)     := rd_sel =/= 0.U
    regFile.io.rd_addr_vec(0) := rd_sel
    regFile.io.rd_data_vec(0) := res_vec(fu_sel)

    // read CSR
    csr.io.common_io.raddr := ex_inst.imm(12, 0)
    // write CSR
    csr.io.common_io.wen   := wb_inst_valid && wb_inst.which_fu === TOBJU && wb_inst.next_pc === PC4
    csr.io.common_io.waddr := wb_inst.imm(12, 0)
    wb_csr_data := alu.io.res
    csr.io.common_io.din := wb_csr_data

    // ecall & mret
    csr.io.event_io.is_mret  := wb_inst_valid && isMret(wb_inst)
    csr.io.event_io.is_ecall := wb_inst_valid && isEcall(wb_inst)
    
    // TODO
    csr.io.event_io.illegal_inst     := wb_inst_valid && wb_inst.illegal_inst
    // csr.io.event_io.mem_access_fault := !wb_mem_req_valid && wb_inst.which_fu === TOLSU
    csr.io.event_io.mem_access_fault := DontCare
    csr.io.event_io.epc              := wb_inst.pc
    csr.io.event_io.inst             := wb_inst.inst
    // csr.io.event_io.bad_address      := wb_ls_addr
    csr.io.event_io.bad_address      := DontCare
    csr.io.event_io.external_int     := io.external_int
    csr.io.event_io.deal_with_int    := wb_interrupt

    // debug signals
    io.bd.pc_is    := issue_inst.pc
    io.bd.pc_ex    := ex_inst.pc
    io.bd.pc_wb    := wb_inst.pc
    io.bd.stall    := io.bf.btof.stall
    io.bd.redirect := io.bf.btof.is_redirect
    io.bd.trap_redirect := csr.io.event_io.trap_redirect
    io.bd.is_ecall := csr.io.event_io.is_ecall

    regFile.io.rs_addr_vec(2) := io.bd.debug_addr(4, 0)
    io.bd.reg_data := regFile.io.rs_data_vec(2)
}