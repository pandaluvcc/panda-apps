package com.panda.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一 API 返回格式
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private int code;
    private T data;
    private String message;

    /**
     * 成功返回
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, data, "success");
    }

    /**
     * 成功返回（带消息）
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(200, data, message);
    }

    /**
     * 失败返回
     */
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, null, message);
    }

    /**
     * 失败返回（默认 500）
     */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(500, null, message);
    }
}
