package com.anjing.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 统一API响应结果
 *
 * 前端约定格式：{ code: 200, msg: "...", data: ... }
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class APIResponse<T>
{
    public static final int SUCCESS_CODE = 200;
    public static final int ERROR_CODE = 500;

    private int code;
    private String msg;
    private T data;

    public APIResponse() {}

    public APIResponse(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public APIResponse(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public boolean isSuccess() {
        return SUCCESS_CODE == code;
    }

    // ============ 成功 ============

    public static <T> APIResponse<T> success(T data) {
        return new APIResponse<>(SUCCESS_CODE, "操作成功", data);
    }

    public static <T> APIResponse<T> success(T data, String msg) {
        return new APIResponse<>(SUCCESS_CODE, msg, data);
    }

    public static <T> APIResponse<T> success() {
        return new APIResponse<>(SUCCESS_CODE, "操作成功", null);
    }

    public static <T> APIResponse<T> success(String msg) {
        return new APIResponse<>(SUCCESS_CODE, msg, null);
    }

    // ============ 失败 ============

    public static <T> APIResponse<T> error(String msg) {
        return new APIResponse<>(ERROR_CODE, msg);
    }

    public static <T> APIResponse<T> error(int code, String msg) {
        return new APIResponse<>(code, msg);
    }

    public static <T> APIResponse<T> error(int code, String msg, T data) {
        return new APIResponse<>(code, msg, data);
    }

    /**
     * 兼容 ErrorCode.getCode() 返回 String 的场景
     */
    public static <T> APIResponse<T> error(String code, String msg) {
        int intCode;
        try {
            intCode = Integer.parseInt(code);
        } catch (NumberFormatException e) {
            intCode = ERROR_CODE;
        }
        return new APIResponse<>(intCode, msg);
    }
}
