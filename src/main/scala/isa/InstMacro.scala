package isa

import chisel3._
import chisel3.util._
import fu._
import config.Config

object InstMacro {
    val REG_ADDR_WIDTH = 5
    val ALU_OP_SIZE    = 4

    // branch types
    val BRXX  = 0.U(4.W)
    val BREQ  = 1.U(4.W)
    val BRNE  = 2.U(4.W)
    val BRLT  = 3.U(4.W)
    val BRGE  = 4.U(4.W)
    val BRLTU = 5.U(4.W)
    val BRGEU = 6.U(4.W)
    val BR_TYPE_SIZE = BRXX.getWidth
    
    // next pc
    val PC4     = 0.U(3.W)
    val BRANCH  = 1.U(3.W)
    val JUMP    = 2.U(3.W)
    val JUMPREG = 3.U(3.W)
    val TRAP    = 4.U(3.W)
    val NEXT_PC_SIZE  = PC4.getWidth

    // to which fu
    val TOXXX = 0.U(2.W)
    val TOALU = 0.U(2.W)
    val TOBJU = 1.U(2.W)
    val TOLSU = 2.U(2.W)
    val FU_TYPE_SIZE = TOXXX.getWidth

    // ALU src (port A)
    val AXXX = 0.U(2.W)  // default is reg
    val AREG = 0.U(2.W)
    val AIMM = 1.U(2.W)
    val APC  = 2.U(2.W)
    val ALU_A_SIZE = AXXX.getWidth
    // ALU src (port B)
    val BXXX = 0.U(2.W)
    val BREG = 0.U(2.W)
    val BIMM = 1.U(2.W)
    
    val ALU_B_SIZE = BXXX.getWidth

    // Mem signals && infos
    // size
    val MEMXXX = 7.U(3.W)
    val MEMBYTE  = 0.U(3.W)  // 000
    val MEMBYTEU = 4.U(3.W)  // 100
    val MEMHALF  = 1.U(3.W)  // 001
    val MEMHALFU = 5.U(3.W)  // 101
    val MEMWORD  = 2.U(3.W)  // 010
    val LS_WIDTH_SIZE = MEMXXX.getWidth
    // wb src
    val SXXX = 0.U(2.W)
    val SALU = 1.U(2.W)
    val SMEM = 2.U(2.W)
    val SPC  = 3.U(2.W)
    val WB_SRC_SIZE = SXXX.getWidth
    // read or write (wb dest is reg or mem)
    val DXXX = 0.U(2.W)
    val DREG = 1.U(2.W)
    val DMEM = 2.U(2.W)
    val WB_DEST_SIZE = DXXX.getWidth
}