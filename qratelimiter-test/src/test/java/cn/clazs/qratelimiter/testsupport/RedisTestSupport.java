package cn.clazs.qratelimiter.testsupport;

import java.net.InetSocketAddress;
import java.net.Socket;

public final class RedisTestSupport {

    private RedisTestSupport() {
    }

    public static boolean isRedisRequiredOrAvailable() {
        if ("true".equalsIgnoreCase(firstNonBlank(
                System.getProperty("qratelimiter.redis.tests.enabled"),
                System.getenv("QRL_REDIS_TESTS_ENABLED")))) {
            return true;
        }
        return isTcpReachable(redisHost(), redisPort());
    }

    public static String redisHost() {
        return firstNonBlank(
                System.getProperty("qratelimiter.redis.host"),
                System.getenv("QRL_REDIS_HOST"),
                "localhost");
    }

    public static int redisPort() {
        String value = firstNonBlank(
                System.getProperty("qratelimiter.redis.port"),
                System.getenv("QRL_REDIS_PORT"),
                "6379");
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return 6379;
        }
    }

    public static String unavailableMessage() {
        return "Redis is not reachable at " + redisHost() + ":" + redisPort();
    }

    private static boolean isTcpReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1000);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }
}
