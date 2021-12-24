sbt "runMain tile.GenT"
cat ./build/verilog/tile/RAM_B.v >> ./build/verilog/tile/Tile.v
cat ./build/verilog/tile/ROM_D.v >> ./build/verilog/tile/Tile.v
cat ./build/verilog/tile/FU_ALU.v >> ./build/verilog/tile/Tile.v
cat ./build/verilog/tile/FU_mul.v >> ./build/verilog/tile/Tile.v
cat ./build/verilog/tile/FU_div.v >> ./build/verilog/tile/Tile.v
cat ./build/verilog/tile/FU_mem.v >> ./build/verilog/tile/Tile.v
cat ./build/verilog/tile/FU_jump.v >> ./build/verilog/tile/Tile.v