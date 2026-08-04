package com.wong.collector.application.workflow;

import redis.clients.jedis.Jedis;

public class RedisTest {
    public static void main(String[] args) {
        try (Jedis jedis = new Jedis("localhost", 6379)) {
            System.out.println("Connected to Redis");
            System.out.println("Redis PING: " + jedis.ping());
            System.out.println("Redis INFO: " + jedis.info("server"));
        } catch (Exception e) {
            System.err.println("Connection failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
