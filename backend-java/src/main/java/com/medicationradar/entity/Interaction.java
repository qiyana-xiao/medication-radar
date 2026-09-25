package com.medicationradar.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("interactions")
public class Interaction {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String drugA;
    private String drugB;
    private String severity;
    private String mechanism;
    private String effect;
    private String recommendation;
}
