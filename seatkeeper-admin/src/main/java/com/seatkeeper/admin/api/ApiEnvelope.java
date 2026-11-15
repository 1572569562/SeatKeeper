package com.seatkeeper.admin.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 服务端统一响应信封（契约里的 Result）：
 * <pre>
 * {"code":"OK","message":"操作成功","data":{...}}
 * {"code":"SEAT_DISABLED","message":"座位已停用","data":null}
 * </pre>
 *
 * 所有接口都要先过这一层，成功才去读 data。
 */
public record ApiEnvelope(String code, String message, JsonNode data) {

    public static final String OK = "OK";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ApiEnvelope parse(String body) {
        try {
            JsonNode root = MAPPER.readTree(body == null || body.isBlank() ? "{}" : body);
            if (root == null || !root.isObject()) {
                throw new ApiException("BAD_RESPONSE", "服务端返回的不是预期格式");
            }
            String code = root.path("code").asText("");
            String message = root.path("message").asText("");
            JsonNode data = root.path("data");
            return new ApiEnvelope(code, message, data.isMissingNode() ? null : data);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("BAD_RESPONSE", "服务端返回内容无法解析", e);
        }
    }

    public boolean isOk() {
        return OK.equals(code);
    }

    /** 成功时取 data，失败时抛异常 */
    public JsonNode requireData() {
        if (!isOk()) {
            throw toException();
        }
        if (data == null || data.isNull()) {
            throw new ApiException(code, "服务端没有返回数据");
        }
        return data;
    }

    public ApiException toException() {
        return new ApiException(code, friendlyMessage());
    }

    /** 错误码翻译成能直接显示给用户的话；没有映射就用服务端给的 message */
    public String friendlyMessage() {
        switch (code) {
            case "UNAUTHORIZED":
                return "学号或密码错误";
            case "TOKEN_INVALID":
                return "登录已过期，请重新登录";
            case "FORBIDDEN":
                return "当前账号没有权限执行这个操作";
            case "NOT_FOUND":
                return "记录不存在，可能已被删除";
            case "SEAT_TAKEN":
                return "该座位已被预约";
            case "SEAT_DISABLED":
                return "该座位已停用";
            case "INVALID_STATE":
                return "当前状态不允许这个操作";
            case "LIMIT_EXCEEDED":
                return "最多同时预约 2 个座位";
            case "CREDIT_RESTRICTED":
                return "信用分受限，暂时不能预约";
            case "CREDIT_OUT_OF_RANGE":
                return "信用分超出允许范围";
            case "INVALID_ARGUMENT":
                return "参数不合法";
            case "DATABASE_UNAVAILABLE":
                return "数据库不可用，请稍后再试";
            case "INTERNAL_ERROR":
                return "服务端出错了，请稍后再试";
            default:
                return message == null || message.isBlank() ? "操作失败（" + code + "）" : message;
        }
    }
}
