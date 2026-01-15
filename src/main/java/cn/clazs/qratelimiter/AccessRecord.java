package cn.clazs.qratelimiter;

import lombok.Data;

@Data
public class AccessRecord {
    private String bizId;
    /** 时间戳单位：毫秒 */
    private Long timestamp;
    private String ipv4;
}
