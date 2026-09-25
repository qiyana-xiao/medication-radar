package com.medicationradar.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("medication_contraindications")
public class MedicationContraindication {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long medicationId;
    private String conditionKeyword;
    private String severityRequired;
    private Integer score;
    private String triggerType;
    private String note;
    private String source;
    private Integer status;
    private Integer version;
    private LocalDateTime createdAt;
}
