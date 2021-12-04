package fu

import chisel3._
import chisel3.util._
import config.Config
import isa._
import mycore.RegFile
import utils._
import cache._
import scala.annotation.switch

class BPUReq extends Bundle with Config {
    val pc = Input(UInt(XLEN.W))
}

class BPUResp extends Bundle with Config {
    val taken  = Output(Bool())
    val target = Output(UInt(XLEN.W))
}

class BPUUpdate extends Bundle with Config {
    val pc          = Input(UInt(XLEN.W))
    val inst        = Input(UInt(XLEN.W))
    val inst_type   = Input(UInt(2.W))
    val real_taken  = Input(Bool())
    val real_target = Input(UInt(XLEN.W))
}

class BPUIO extends Bundle with Config {
    val req    = new BPUReq
    val resp   = new BPUResp
    val update = new BPUUpdate
}

class RAS(nRASEntries: Int = 4) extends Module with Config {
    val io = IO(new Bundle{
        val push      = Input(Bool())
        val push_addr = Input(UInt(XLEN.W))
        val ret       = Input(Bool())
        val ret_addr  = Output(UInt(XLEN.W))
    })
    val ras = Reg(Vec(nRASEntries, UInt((XLEN - 2).W)))
    val top = RegInit(0.U(log2Ceil(nRASEntries + 1).W))
    when (io.push) {
        ras(top) := io.push_addr(XLEN - 1, 2)
        top := Mux(top === nRASEntries.U, nRASEntries.U, top + 1.U)
        io.ret_addr := 0.U
    }.elsewhen (io.ret) {
        val new_top = Mux(top === 0.U, 0.U, top - 1.U)
        top := new_top
        io.ret_addr := ras(new_top)
    }.otherwise {
        io.ret_addr := 0.U
    }
}
class BPU extends Module with Config {
    val io = IO(new BPUIO)

    val tag = Module(new RegFile(nregs = bhtEntryNum, len = bhtTagBits + 1, nread = 1, nwrite = 1)) // valid + tag
    val bht = Module(new RegFile(nregs = bhtEntryNum, len = 4,              nread = 2, nwrite = 1)) // 1-bit call + 1-bit ret + 2-bit state predict
    val btb = Module(new RegFile(nregs = bhtEntryNum, len = XLEN - 2,       nread = 1, nwrite = 1)) //
    val ras = Module(new RAS(nRASEntries = 4))

    def isCall(inst: UInt): Bool = {
        inst(14, 0) === BitPat("b000000011100111") || inst(11, 0) === BitPat("b000011101111")
    }
    def isRet(inst: UInt): Bool = {
        inst(31, 0) === BitPat("b00000000000000001000000001100111")
    }

    val pc_tag_req    = io.req.pc(XLEN - 1, bhtIndexBits + 2)
    val pc_index_req  = io.req.pc(bhtIndexBits + 1, 2)
    tag.io.rs_addr_vec(0) := pc_index_req
    bht.io.rs_addr_vec(0) := pc_index_req
    btb.io.rs_addr_vec(0) := pc_index_req
    val valid        = tag.io.rs_data_vec(0)(bhtTagBits)
    val fetch_tag    = tag.io.rs_data_vec(0)(bhtTagBits - 1, 0)
    val fetch_taken  = bht.io.rs_data_vec(0)(1, 0)
    val fetch_ret    = bht.io.rs_data_vec(0)(2)
    val fetch_call   = bht.io.rs_data_vec(0)(3)
    val fetch_target = btb.io.rs_data_vec(0)

    val pc_tag_update   = io.update.pc(XLEN - 1, bhtIndexBits + 2)
    val pc_index_update = io.update.pc(bhtIndexBits + 1, 2)

    // query
    when (valid && fetch_tag === pc_tag_req) { // hit
        io.resp.taken  := Mux(fetch_ret, true.B, fetch_taken(1))
        io.resp.target := Cat(Mux(fetch_ret, ras.io.ret_addr, fetch_target), 0.U(2.W))
    }.otherwise { // not in table
        io.resp.taken  := false.B
        io.resp.target := 0.U
    }
    ras.io.ret  := fetch_ret
    ras.io.push := fetch_call
    ras.io.push_addr := io.req.pc + 4.U

    // update
    val need_update = io.update.inst_type =/= bpuOTHER.U
    tag.io.wen_vec(0)     := need_update
    tag.io.rd_addr_vec(0) := pc_index_update
    tag.io.rd_data_vec(0) := Cat(true.B, pc_tag_update)

    btb.io.wen_vec(0)     := need_update && io.update.real_taken
    btb.io.rd_addr_vec(0) := pc_index_update
    btb.io.rd_data_vec(0) := io.update.real_target(XLEN - 1, 2)

    bht.io.wen_vec(0)     := need_update
    bht.io.rd_addr_vec(0) := pc_index_update
    bht.io.rs_addr_vec(1) := pc_index_update
    val current_bht = bht.io.rs_data_vec(1)(1, 0)

    when (io.update.real_taken) {
        bht.io.rd_data_vec(0) := Cat(isCall(io.update.inst), isRet(io.update.inst), Mux(io.update.inst_type === bpuJAL.U, 3.U,
            MuxLookup(current_bht, current_bht + 1.U,
            Seq( 3.U -> current_bht )
        )))
    }.otherwise {
        bht.io.rd_data_vec(0) := Cat(0.U(2.W), MuxLookup(current_bht, current_bht - 1.U, Seq( 0.U -> current_bht )))
    }
    
    when (isCall(io.update.inst) && !bht.io.rs_data_vec(1)(3)) {
        ras.io.push := true.B
        ras.io.push_addr := io.update.pc + 4.U
    }
}

// class GShareBPU(entries: Int = 256) extends Module with Config {
//     val io = IO(new BPUIO)

//     val hlen = log2Ceil(entries)
//     val ghr = RegInit(0.U(hlen.W))
//     val bct = Module(new DualPortBRAM(DATA_WIDTH = XLEN, entries)) // bct(i)(1, 0) is 2-bit counter

//     val bct_addr = ghr ^ io.req.pc(hlen + 1, 2)
//     bct.io.clk := clock
//     bct.io.rst := reset
//     bct.io.addra := bct_addr
    
//     val need_update = io.update.inst_type =/= bpuOTHER.U && (io.update.real_taken ^ io.update.pc(1))
//     when (need_update) {
//         ghr := Cat(ghr(hlen - 2, 0), io.update.real_taken)
//         val last_pred   = io.update.pc(1, 0)
//         when (io.update.real_taken) {
//             bct.io.dina := Cat(io.update.real_target, Mux(io.update.inst_type === bpuJAL.U, 3.U,
//                 MuxLookup(last_pred, last_pred + 1.U, Seq( 3.U -> last_pred )) ))
//         }.otherwise {
//             bht.io.dina := Cat(io.update.pred_target, MuxLookup(last_pred, last_pred - 1.U, Seq( 0.U -> last_pred )))
//         }
//     }
// }