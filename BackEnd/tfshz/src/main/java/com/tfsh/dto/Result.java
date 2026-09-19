package com.tfsh.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 统一返回结果封装类
 * 用于后端接口向前端返回标准化的 JSON 数据格式。
 * 使用 Lombok 注解自动生成 getter/setter、无参构造、全参构造等方法。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result {

    /** 操作是否成功：true-成功，false-失败 */
    private Boolean success;

    /** 错误提示信息，成功时为 null */
    private String errorMsg;

    /** 返回的数据，可以是任意对象（如单个对象、列表、分页对象等） */
    private Object data;

    /** 总记录数，通常用于分页查询时返回给前端 */
    private Long total;

    /**
     * 成功响应，不携带数据
     * @return Result 对象，success=true，data=null，total=null
     */
    public static Result ok() {
        return new Result(true, null, null, null);
    }

    /**
     * 成功响应，携带单个数据对象
     * @param data 需要返回的数据
     * @return Result 对象，success=true，data=传入的数据
     */
    public static Result ok(Object data) {
        return new Result(true, null, data, null);
    }

    /**
     * 成功响应，携带列表数据和总记录数（常用于分页）
     * @param data  列表数据
     * @param total 总记录数
     * @return Result 对象，success=true，data=列表，total=总数
     */
    public static Result ok(List<?> data, Long total) {
        return new Result(true, null, data, total);
    }

    /**
     * 失败响应，携带错误提示信息
     * @param errorMsg 错误描述
     * @return Result 对象，success=false，errorMsg=错误信息
     */
    public static Result fail(String errorMsg) {
        return new Result(false, errorMsg, null, null);
    }
}
