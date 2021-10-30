package isa

import chisel3._
import chisel3.util._
import fu._
import fu.AluOpType
import config.Config
import InstMacro._
import ISA._

class CtrlInfo extends Bundle with Config with AluOpType {
    // inst
    val inst = UInt(XLEN.W)
    // pc
    val pc = UInt(XLEN.W)
    val next_pc = UInt(NEXT_PC_SIZE.W)
    // branch predict
    val taken_predict = Bool()
    val target_predict = UInt(XLEN.W)
    // illegal inst
    val illegal_inst = Bool()
    // regfile && imm
    val rs1 = UInt(REG_ADDR_WIDTH.W)
    val rs2 = UInt(REG_ADDR_WIDTH.W)
    val rd  = UInt(REG_ADDR_WIDTH.W)
    val imm = UInt(XLEN.W)

    // which fu
    val which_fu = UInt(FU_TYPE_SIZE.W)

    // ALU
    val alu_op    = UInt(ALU_OP_SIZE.W)
    val alu_src_a = UInt(ALU_A_SIZE.W)
    val alu_src_b = UInt(ALU_B_SIZE.W)
    val br_type   = UInt(BR_TYPE_SIZE.W)
    // LSU
    val ls_width  = UInt(LS_WIDTH_SIZE.W)

    // write back
    val wb_src    = UInt(WB_SRC_SIZE.W)
    val wb_dest   = UInt(WB_DEST_SIZE.W)
}

class DecoderIO extends Bundle with Config {
    val pc   = Input(UInt(XLEN.W))
    val inst = Input(UInt(32.W))
    val taken_predict  = Input(Bool())
    val target_predict = Input(UInt(XLEN.W))
    val ctrl = Output(new CtrlInfo)
}

class Decoder extends Module with Config with AluOpType {
    val io = IO(new DecoderIO)

    // rs1, rs2, rd
    val rs1   = io.inst(19, 15)
    val rs2   = io.inst(24, 20)
    val rd    = io.inst(11, 7)

    // ImmGen
    val x_imm     = 0.U(XLEN.W)
    val i_imm     = Cat(Fill(XLEN - 12, io.inst(31)), io.inst(31, 20))
    val s_imm     = Cat(Fill(XLEN - 12, io.inst(31)), io.inst(31, 25), io.inst(11, 7))
    val b_imm     = Cat(Fill(XLEN - 13, io.inst(31)), io.inst(31), io.inst(7), io.inst(30, 25), io.inst(11, 8), 0.U(1.W))
    val u_imm     = Cat(io.inst(31, 12), 0.U(12.W))   // auipc, lui
    val j_imm     = Cat(Fill(XLEN - 21, io.inst(31)), io.inst(31), io.inst(19, 12), io.inst(20), io.inst(30, 21), 0.U(1.W))
    val shamt_imm = Cat(0.U((XLEN - 6).W), io.inst(25, 20))
    val csr_imm   = Cat(0.U((XLEN - 12).W), io.inst(31, 20))

    // alu signals and sources
    // | alu_op | alu_src_a | alu_src_b | imm |
    val alu_signal_arr = Array(
        ADD   -> List(aluAdd.U,  AREG, BREG, x_imm),
        SUB   -> List(aluSub.U,  AREG, BREG, x_imm),
        SLT   -> List(aluSlt.U,  AREG, BREG, x_imm),
        SLTU  -> List(aluSltu.U, AREG, BREG, x_imm),
        XOR   -> List(aluXor.U,  AREG, BREG, x_imm),
        AND   -> List(aluAnd.U,  AREG, BREG, x_imm),
        OR    -> List(aluOr.U,   AREG, BREG, x_imm),
        SLL   -> List(aluSll.U,  AREG, BREG, x_imm),
        SRL   -> List(aluSrl.U,  AREG, BREG, x_imm),
        SRA   -> List(aluSra.U,  AREG, BREG, x_imm),
        LUI   -> List(aluLui.U,  AREG, BIMM, u_imm),
        AUIPC -> List(aluAdd.U,  APC,  BIMM, u_imm),

        ADDI  -> List(aluAdd.U,  AREG, BIMM, i_imm),
        SLTI  -> List(aluSlt.U,  AREG, BIMM, i_imm),
        SLTIU -> List(aluSltu.U, AREG, BIMM, i_imm),
        XORI  -> List(aluXor.U,  AREG, BIMM, i_imm),
        ANDI  -> List(aluAnd.U,  AREG, BIMM, i_imm),
        ORI   -> List(aluOr.U,   AREG, BIMM, i_imm),
        SLLI  -> List(aluSll.U,  AREG, BIMM, shamt_imm),
        SRLI  -> List(aluSrl.U,  AREG, BIMM, shamt_imm),
        SRAI  -> List(aluSra.U,  AREG, BIMM, shamt_imm)
    )
    val alu_signal = ListLookup(
        io.inst, List(aluAdd.U, AREG, BREG, x_imm),
        alu_signal_arr
    )

    // bju signals and sources
    // | alu_op | src_a | src_b | dest | imm | next pc | branch type |
    val bju_signal_arr = Array(
        BEQ  -> List(aluSub.U,  AREG, BREG, DXXX, b_imm, BRANCH, BREQ),
        BNE  -> List(aluSub.U,  AREG, BREG, DXXX, b_imm, BRANCH, BRNE),
        BLT  -> List(aluSub.U,  AREG, BREG, DXXX, b_imm, BRANCH, BRLT),
        BGE  -> List(aluSub.U,  AREG, BREG, DXXX, b_imm, BRANCH, BRGE),
        BLTU -> List(aluSltu.U, AREG, BREG, DXXX, b_imm, BRANCH, BRLTU),
        BGEU -> List(aluSltu.U, AREG, BREG, DXXX, b_imm, BRANCH, BRGEU),

        JAL  -> List(aluAdd.U, AXXX, BREG, DREG, j_imm, JUMP,    BRXX),
        JALR -> List(aluAdd.U, AREG, BIMM, DREG, i_imm, JUMPREG, BRXX),

        MRET -> List(aluAdd.U, AXXX, BXXX, DXXX, x_imm, EPC,     BRXX),
        ECALL-> List(aluAdd.U, AXXX, BXXX, DXXX, x_imm, MTVEC,   BRXX),
        
        CSRRS  -> List(aluOr.U,  AREG, BCSR, DREG, csr_imm, PC4, BRXX),
        CSRRC  -> List(aluAnd.U, AREG, BCSR, DREG, csr_imm, PC4, BRXX),
        CSRRW  -> List(aluAdd.U, AREG, BCSR, DREG, csr_imm, PC4, BRXX),
        CSRRSI -> List(aluOr.U,  AIMM, BCSR, DREG, csr_imm, PC4, BRXX),
        CSRRCI -> List(aluAnd.U, AIMM, BCSR, DREG, csr_imm, PC4, BRXX),
        CSRRWI -> List(aluAdd.U, AIMM, BCSR, DREG, csr_imm, PC4, BRXX)
    )
    val bju_signal = ListLookup(
        io.inst, List(aluAdd.U, AXXX, BXXX, DXXX, x_imm, PC4, BREQ),
        bju_signal_arr
    )

    // lsu signals and sources
    // | ldst_size | ldst_dest(reg->load, mem->store) |
    val lsu_signal_arr = Array(
        LB  -> List(MEMBYTE , DREG),
        LBU -> List(MEMBYTEU, DREG),
        LH  -> List(MEMHALF , DREG),
        LHU -> List(MEMHALFU, DREG),
        LW  -> List(MEMWORD , DREG),

        SB  -> List(MEMBYTE, DMEM),
        SH  -> List(MEMHALF, DMEM),
        SW  -> List(MEMWORD, DMEM)
    )
    val lsu_signal = ListLookup(
        io.inst, List(MEMBYTE, DREG),
        lsu_signal_arr
    )

    // alu or lsu
    val control_signal_arr = Array(

        LUI   -> List(false.B ,  TOALU),
        AUIPC -> List(false.B ,  TOALU),

        JAL   -> List(false.B ,  TOBJU),
        JALR  -> List(false.B ,  TOBJU),

        BEQ   -> List(false.B ,  TOBJU),
        BNE   -> List(false.B ,  TOBJU),
        BLT   -> List(false.B ,  TOBJU),
        BGE   -> List(false.B ,  TOBJU),
        BLTU  -> List(false.B ,  TOBJU),
        BGEU  -> List(false.B ,  TOBJU),

        MRET  -> List(false.B ,  TOBJU),
        ECALL -> List(false.B ,  TOBJU),

        CSRRS -> List(false.B ,  TOBJU),
        CSRRC -> List(false.B ,  TOBJU),
        CSRRW -> List(false.B ,  TOBJU),
        CSRRSI-> List(false.B ,  TOBJU),
        CSRRCI-> List(false.B ,  TOBJU),
        CSRRWI-> List(false.B ,  TOBJU),

        LB    -> List(false.B ,  TOLSU),
        LH    -> List(false.B ,  TOLSU),
        LW    -> List(false.B ,  TOLSU),
        LBU   -> List(false.B ,  TOLSU),
        LHU   -> List(false.B ,  TOLSU),

        SB    -> List(false.B ,  TOLSU),
        SH    -> List(false.B ,  TOLSU),
        SW    -> List(false.B ,  TOLSU),

        ADD   -> List(false.B ,  TOALU),
        SUB   -> List(false.B ,  TOALU),
        SLT   -> List(false.B ,  TOALU),
        SLTU  -> List(false.B ,  TOALU),
        XOR   -> List(false.B ,  TOALU),
        OR    -> List(false.B ,  TOALU),
        AND   -> List(false.B ,  TOALU),
        SLL   -> List(false.B ,  TOALU),
        SRL   -> List(false.B ,  TOALU),
        SRA   -> List(false.B ,  TOALU),
        
        ADDI  -> List(false.B ,  TOALU),
        SLTI  -> List(false.B ,  TOALU),
        SLTIU -> List(false.B ,  TOALU),
        XORI  -> List(false.B ,  TOALU),
        ORI   -> List(false.B ,  TOALU),
        ANDI  -> List(false.B ,  TOALU),
        SLLI  -> List(false.B ,  TOALU),
        SRLI  -> List(false.B ,  TOALU),
        SRAI  -> List(false.B ,  TOALU),
    )
    val control_signal = ListLookup(
                            io.inst, List(true.B, TOALU),
                            control_signal_arr
                        )

    io.ctrl.taken_predict  := io.taken_predict
    io.ctrl.target_predict := io.target_predict
    io.ctrl.inst           := io.inst
    io.ctrl.pc             := io.pc
    io.ctrl.next_pc        := Mux(control_signal(1) === TOBJU, bju_signal(5), PC4)
    io.ctrl.illegal_inst   := control_signal(0)
    io.ctrl.rs1            := rs1
    io.ctrl.rs2            := rs2
    io.ctrl.rd             := rd
    io.ctrl.imm            := MuxLookup(
                                  control_signal(1), x_imm,
                                  Seq(
                                      TOALU -> alu_signal(3),
                                      TOBJU -> bju_signal(4),
                                      TOLSU -> MuxLookup(
                                          lsu_signal(1), i_imm,
                                          Seq( DREG -> i_imm, DMEM -> s_imm )
                                      )
                                  )
                              )
    io.ctrl.which_fu       := control_signal(1)
    io.ctrl.alu_op         := MuxLookup(
                                 control_signal(1), aluAdd.U,
                                 Seq( TOALU -> alu_signal(0), TOBJU -> bju_signal(0))
                             )
    io.ctrl.alu_src_a      := MuxLookup(
                                 control_signal(1), AREG,
                                 Seq( TOALU -> alu_signal(1), TOBJU -> bju_signal(1))
                             )
    io.ctrl.alu_src_b      := MuxLookup(
                                 control_signal(1), BREG,
                                 Seq( TOALU -> alu_signal(2), TOBJU -> bju_signal(2))
                             )
    io.ctrl.ls_width       := Mux(control_signal(1) === TOLSU, lsu_signal(0), MEMXXX)
    io.ctrl.br_type        := Mux(control_signal(1) === TOBJU && bju_signal(5) === BRANCH, bju_signal(6), BRXX)
    io.ctrl.wb_src         := MuxLookup(
                                 control_signal(1), SALU,
                                 Seq(
                                     TOLSU -> SMEM,
                                     TOBJU -> Mux(bju_signal(5) === JUMP || bju_signal(5) === JUMPREG, SPC, SXXX)
                                 )
                             )
    io.ctrl.wb_dest        := MuxLookup(
                                 control_signal(1), DREG,
                                 Seq(
                                     TOBJU -> bju_signal(3),
                                     TOLSU -> lsu_signal(1)
                                 )
                             )
}     