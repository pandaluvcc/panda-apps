package com.panda.common.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * API 接口日志切面
 * <p>
 * 记录所有 Controller 方法的调用信息：URL、方法、参数、耗时
 */
@Aspect
@Component
@Slf4j
public class ApiLogAspect {

    private final ObjectMapper objectMapper;
    private final ThreadLocal<String> traceIdHolder = new ThreadLocal<>();

    public ApiLogAspect() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * 拦截所有 RestController 方法
     */
    @Around("@within(org.springframework.web.bind.annotation.RestController)")
    public Object logApiCall(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        // 生成 traceId
        String traceId = UUID.randomUUID().toString().replace("-", "");
        traceIdHolder.set(traceId);

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();
        String classMethod = signature.getDeclaringTypeName() + "." + methodName;

        // 获取接口描述（优先用注解名称）
        String apiName = getApiName(joinPoint, methodName);

        // 获取请求信息
        HttpServletRequest request = getHttpServletRequest(joinPoint.getArgs());
        String url = request != null ? request.getRequestURL().toString() : "N/A";
        String httpMethod = request != null ? request.getMethod() : "N/A";
        String ip = request != null ? getClientIp(request) : "N/A";

        // 序列化参数
        String argsStr = serializeArgs(joinPoint.getArgs(), signature.getParameterNames());

        // 打印请求开始日志
        log.info("[{}] ================请求接口【{}】开始================", traceId, apiName);
        log.info("[{}] URL : {}", traceId, url);
        log.info("[{}] HTTP_METHOD : {}", traceId, httpMethod);
        log.info("[{}] IP : {}", traceId, ip);
        log.info("[{}] CLASS_METHOD : {}", traceId, classMethod);
        log.info("[{}] Request Args : {}", traceId, argsStr);

        Object result = null;
        Throwable error = null;

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable e) {
            error = e;
            throw e;
        } finally {
            long elapsed = System.currentTimeMillis() - startTime;

            if (error != null) {
                log.info("[{}] URL: {} RESPONSE: {}", traceId, url, error.getMessage());
                log.info("[{}] SPEND TIME : {}ms", traceId, elapsed);
                log.info("[{}] ================请求接口【{}】异常结束================", traceId, apiName);
            } else {
                String responseStr = serializeResult(result);
                log.info("[{}] URL: {} RESPONSE:{}", traceId, url, responseStr);
                log.info("[{}] SPEND TIME : {}ms", traceId, elapsed);
                log.info("[{}] ================请求接口【{}】结束================", traceId, apiName);
            }

            traceIdHolder.remove();
        }
    }

    /**
     * 序列化返回值
     */
    private String serializeResult(Object result) {
        if (result == null) {
            return "null";
        }
        try {
            String json = objectMapper.writeValueAsString(result);
            // 限制返回值长度
            if (json.length() > 1000) {
                json = json.substring(0, 1000) + "...";
            }
            return json;
        } catch (Exception e) {
            return result.toString();
        }
    }

    /**
     * 获取接口名称（优先从 @Operation 注解获取中文描述）
     */
    private String getApiName(ProceedingJoinPoint joinPoint, String defaultName) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 优先从 @Operation 注解获取 summary
        Operation operation = method.getAnnotation(Operation.class);
        if (operation != null && !operation.summary().isEmpty()) {
            return operation.summary();
        }

        return defaultName;
    }

    /**
     * 获取 HttpServletRequest（优先从 RequestContextHolder 获取）
     */
    private HttpServletRequest getHttpServletRequest(Object[] args) {
        // 优先从 RequestContextHolder 获取
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            return attributes.getRequest();
        }
        // 备用：从方法参数中获取
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof HttpServletRequest) {
                    return (HttpServletRequest) arg;
                }
            }
        }
        return null;
    }

    /**
     * 获取客户端 IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多个代理时取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 序列化请求参数
     */
    private String serializeArgs(Object[] args, String[] paramNames) {
        if (args == null || args.length == 0) {
            return "";
        }

        List<String> parts = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            String name = (paramNames != null && i < paramNames.length) ? paramNames[i] : "arg" + i;

            // 跳过不能序列化的类型
            if (arg instanceof HttpServletRequest
                    || arg instanceof HttpServletResponse
                    || arg instanceof MultipartFile) {
                if (arg instanceof MultipartFile mf) {
                    parts.add(name + "=" + mf.getOriginalFilename() + "(" + mf.getSize() + "bytes)");
                }
                continue;
            }

            try {
                String json = objectMapper.writeValueAsString(arg);
                // 限制参数长度
                if (json.length() > 500) {
                    json = json.substring(0, 500) + "...";
                }
                parts.add(name + "=" + json);
            } catch (Exception e) {
                parts.add(name + "=" + arg);
            }
        }
        return String.join("&", parts);
    }
}
