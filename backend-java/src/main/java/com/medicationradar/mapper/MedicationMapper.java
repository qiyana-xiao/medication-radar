package com.medicationradar.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medicationradar.entity.Medication;
import org.apache.ibatis.annotations.Param;

public interface MedicationMapper extends BaseMapper<Medication> {
    long isReferencedByReport(@Param("drugName") String drugName);
}
