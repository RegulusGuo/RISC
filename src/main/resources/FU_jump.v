
module cmp_32(  input [31:0] a,
                input [31:0] b,
                input [2:0] ctrl,
                output c
    );
    localparam cmp_EQ  = 3'b001;
    localparam cmp_NE  = 3'b010;
    localparam cmp_LT  = 3'b011;
    localparam cmp_GE  = 3'b100;
    localparam cmp_LTU = 3'b101;
    localparam cmp_GEU = 3'b110;

    wire res_EQ  = a == b;
    wire res_NE  = ~res_EQ;
    wire res_LT  = (a[31] & ~b[31]) || (~(a[31] ^ b[31]) && a < b);
    wire res_LTU = a < b;
    wire res_GE  = ~res_LT;
    wire res_GEU = ~res_LTU;

    wire EQ  = ctrl == cmp_EQ ; 
    wire NE  = ctrl == cmp_NE ; 
    wire LT  = ctrl == cmp_LT ; 
    wire LTU = ctrl == cmp_LTU;
    wire GE  = ctrl == cmp_GE ; 
    wire GEU = ctrl == cmp_GEU;

    assign c = EQ  & res_EQ  |
               NE  & res_NE  |
               LT  & res_LT  |
               LTU & res_LTU |
               GE  & res_GE  |
               GEU & res_GEU ;
endmodule

module FU_jump(
	input clk, EN, JALR,
	input[3:0] cmp_ctrl,
	input[31:0] rs1_data, rs2_data, imm, PC,
	output[31:0] PC_jump, PC_wb,
	output is_jump, finish
);

	wire cmp_res;
    reg state;
    assign finish = state == 1'b1;
	initial begin
        state = 0;
    end

	reg JALR_reg;
	reg[3:0] cmp_ctrl_reg = 0;
	reg[31:0] rs1_data_reg = 0, rs2_data_reg = 0, imm_reg = 0, PC_reg = 0;

	always@(posedge clk) begin
        if(EN & ~state) begin // state == 0
			JALR_reg <= JALR;
			cmp_ctrl_reg <= cmp_ctrl;
            rs1_data_reg <= rs1_data;
            rs2_data_reg <= rs2_data;
            imm_reg <= imm;
			PC_reg <= PC;
            state <= 1;
        end
        else state <= 0;
    end

	cmp_32 cmp(.a(rs1_data_reg),.b(rs2_data_reg),.ctrl(cmp_ctrl_reg[3:1]),.c(cmp_res));

	// add_32 a(.a(JALR_reg ? rs1_data_reg : PC_reg),.b(imm_reg),.c(PC_jump));
	assign PC_jump = imm_reg + (JALR_reg ? rs1_data_reg : PC_reg);

	// add_32 b(.a(PC_reg),.b(32'd4),.c(PC_wb));
	assign PC_wb = PC_reg + 32'd4;

	assign is_jump = cmp_ctrl_reg[0] | cmp_res;

endmodule