package com.nageoffer.ai.ragent.career.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "t_career_decision_index", autoResultMap = true)
public class CareerDecisionIndexDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String traceId;

    private String userId;

    private String businessScene;

    private String businessId;

    private String agentType;

    private String decisionType;

    private String decisionKey;

    private String decisionSummary;

    private String inputRefJson;

    private String outputRefJson;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableLogic
    private Integer deleted;
}
