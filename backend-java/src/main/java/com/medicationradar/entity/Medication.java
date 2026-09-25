package com.medicationradar.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("medications")
public class Medication {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String aliases;
    private String category;
    private String ingredients;
    private String appearance;
    private String specification;
    private String dosageForm;
    private String indications;
    private String usageDosage;
    private String adverseReactions;
    private String contraindications;
    private String precautions;
    private String specialPopulations;
    private String pharmacology;
    private String therapeuticDuplication;
    private String monitor;
    private String notes;
    private String status;
    private Integer version;
    private String disabledReason;
    private LocalDateTime createdAt;
    @TableField(exist = false)
    private LocalDateTime updatedAt;
}
