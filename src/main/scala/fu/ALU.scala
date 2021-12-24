package fu

import chisel3._
import chisel3.util._
import isa._
import InstMacro._
import config.Config

trait AluOpType {
    val aluOpWidth = 4
    val aluAdd  = 1
    val aluSub  = 2
    val aluAnd  = 3
    val aluOr   = 4
    val aluXor  = 5
    val aluSll  = 6
    val aluSrl  = 7
    val aluSlt  = 8
    val aluSltu = 9
    val aluSra  = 10
    val aluLui  = 12
}

class ALUIO extends Bundle with Config with AluOpType {
    val clk = Input(Clock())
    val EN  = Input(Bool())
    val ALUControl = Input(UInt(aluOpWidth.W))
    val ALUA = Input(UInt(XLEN.W))
    val ALUB = Input(UInt(XLEN.W))
    val res  = Output(UInt(XLEN.W))
    val zero = Output(Bool())
    val overflow = Output(Bool())
    val finish   = Output(Bool())
}

class FU_ALU extends BlackBox with HasBlackBoxResource {
    val io = IO(new ALUIO)
    addResource("/FU_ALU.v")
}
// trait AluOpType {
//     val aluOpWidth = 4
//     val aluAdd  = 0
//     val aluSub  = 1
//     val aluSlt  = 2
//     val aluSltu = 3
//     val aluXor  = 4
//     val aluAnd  = 5
//     val aluOr   = 6
//     val aluSll  = 7
//     val aluSrl  = 8
//     val aluSra  = 9
//     val aluLui  = 10
// }

// class ALUIO extends Bundle with Config {
//     val a    = Input(UInt(XLEN.W))
//     val b    = Input(UInt(XLEN.W))
//     val op   = Input(UInt(ALU_OP_SIZE.W))
//     val res  = Output(UInt(XLEN.W))
//     val zero = Output(UInt(XLEN.W))
// }

// class ALU extends Module with Config with AluOpType {
//     val io = IO(new ALUIO)

//     val add_res = io.a + io.b
//     val sub_res = io.a - io.b

//     val alu_seq = Seq(
//         aluAdd.U(ALU_OP_SIZE.W)  -> (io.a + io.b),
//         aluSub.U(ALU_OP_SIZE.W)  -> (io.a - io.b),
//         aluSlt.U(ALU_OP_SIZE.W)  -> Mux(io.a.asSInt() < io.b.asSInt(), 1.U, 0.U),
//         aluSltu.U(ALU_OP_SIZE.W) -> Mux(io.a < io.b, 1.U, 0.U),
//         aluXor.U(ALU_OP_SIZE.W)  -> (io.a ^ io.b),
//         aluAnd.U(ALU_OP_SIZE.W)  -> (io.a & io.b),
//         aluOr.U(ALU_OP_SIZE.W)   -> (io.a | io.b),
//         aluSll.U(ALU_OP_SIZE.W)  -> (io.a(31, 0) << io.b(4, 0)),
//         aluSrl.U(ALU_OP_SIZE.W)  -> (io.a(31, 0) >> io.b(4, 0)),
//         aluSra.U(ALU_OP_SIZE.W)  -> (io.a.asSInt() >> io.b(4, 0)).asUInt(),
//         aluLui.U(ALU_OP_SIZE.W)  -> io.b
//     )

//     val result = MuxLookup(io.op, add_res, alu_seq)
//     io.res  :=  result
//     io.zero := ~io.res.orR()
// }