package com.tfsh.dto;

import lombok.Data;

import java.util.List;

@Data
public class ScrollResult {
    private List<?> list;      // 当前页查到的数据列表
    private Long minTime;      // 本次查询中最后一条记录的时间戳（游标）
    private Integer offset;    // 与 minTime 相同的记录已经取了多少条
}
