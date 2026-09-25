package com.medicationradar.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("reports")
public class Report {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String patient;
    private String medications;
    private String report;
    private String riskLevel;
    private Integer score;
    private Boolean favorite;
    private String knowledgeVersion;
    private LocalDateTime createdAt;
}
