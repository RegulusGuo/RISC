package cache

import chisel3._
import chisel3.util._
import mycore._
import config._

class XBar extends Module with Config with CacheConfig with MemAccessType {
    val io = IO(new Bundle{
        val dcache = Flipped(new CacheMemIO)
        val dmem   = Flipped(new DmemIO)
    })

    io.dmem.clk := clock
    io.dmem.rst := reset
    // idle --cache req--> s1 --ack--> s2 --ack--> s3 --ack--> s4 --ack--> s5 --> idle //
    val ridle :: rready :: rack1 :: rack2 :: rack3 :: rack4 :: Nil = Enum(6)
    val widle :: wready :: wack1 :: wack2 :: wack3 :: wack4 :: Nil = Enum(6)
    val rstate = RegInit(ridle)
    val wstate = RegInit(widle)
    val rbuffer = Reg(Vec(cachelineBits / dataWidth, UInt(dataWidth.W)))
    val wbuffer = Reg(Vec(cachelineBits / dataWidth, UInt(dataWidth.W)))

    io.dcache.req.ready  := true.B
    io.dcache.resp.valid := rstate === rack4 || wstate === wack4
    io.dcache.resp.bits.rdata := 0.U
    io.dmem.cs := io.dcache.req.valid
    io.dmem.we := io.dcache.req.bits.wen
    io.dmem.din  := 0.U
    io.dmem.addr := 0.U

    switch (rstate) {
        is(ridle) {
            rstate := Mux(io.dcache.req.valid && !io.dcache.req.bits.wen, rready, ridle)
        }
        is (rready) {
            io.dmem.addr := Cat(io.dcache.req.bits.addr(XLEN - 1, 4), 0.U(4.W))
            rstate := Mux(io.dmem.ack, rack1, rready)
        }
        is (rack1) {
            io.dmem.addr := Cat(io.dcache.req.bits.addr(XLEN - 1, 4), 4.U(4.W))
            rstate := Mux(io.dmem.ack, rack2, rack1)
        }
        is (rack2) {
            io.dmem.addr := Cat(io.dcache.req.bits.addr(XLEN - 1, 4), 8.U(4.W))
            rstate := Mux(io.dmem.ack, rack3, rack2)
        }
        is (rack3) {
            io.dmem.addr := Cat(io.dcache.req.bits.addr(XLEN - 1, 4), 12.U(4.W))
            rstate := Mux(io.dmem.ack, rack4, rack3)
        }
        is (rack4) {
            rstate := ridle
            io.dcache.resp.bits.rdata := rbuffer.asTypeOf(chiselTypeOf(io.dcache.resp.bits.rdata))
        }
    }
    when (io.dmem.ack) {
        switch (rstate) {
            is(ridle) { }
            is(rready) {
                rbuffer(0) := io.dmem.dout
            }
            is(rack1) {
                rbuffer(1) := io.dmem.dout
            }
            is(rack2) {
                rbuffer(2) := io.dmem.dout
            }
            is(rack3) {
                rbuffer(3) := io.dmem.dout
            }
            is(rack4) {}
        }
    }

    switch (wstate) {
        is(widle) {
            wbuffer := io.dcache.req.bits.wdata.asTypeOf(chiselTypeOf(wbuffer))
            wstate := Mux(io.dcache.req.valid && io.dcache.req.bits.wen, wready, widle)
        }
        is (wready) {
            io.dmem.din := wbuffer(0)
            io.dmem.addr := Cat(io.dcache.req.bits.addr(XLEN - 1, 4), 0.U(4.W))
            wstate := Mux(io.dmem.ack, wack1, wready)
        }
        is (wack1) {
            io.dmem.din := wbuffer(1)
            io.dmem.addr := Cat(io.dcache.req.bits.addr(XLEN - 1, 4), 4.U(4.W))
            wstate := Mux(io.dmem.ack, wack2, wack1)
        }
        is (wack2) {
            io.dmem.din := wbuffer(2)
            io.dmem.addr := Cat(io.dcache.req.bits.addr(XLEN - 1, 4), 8.U(4.W))
            wstate := Mux(io.dmem.ack, wack3, wack2)
        }
        is (wack3) {
            io.dmem.din := wbuffer(3)
            io.dmem.addr := Cat(io.dcache.req.bits.addr(XLEN - 1, 4), 12.U(4.W))
            wstate := Mux(io.dmem.ack, wack4, wack3)
        }
        is (wack4) {
            wstate := widle
        }
    }
}