package com.sky.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@Slf4j
public class RedisConfiguration {
    //字符串类型模板对象
    @Bean
    @Qualifier("StringRedisTemplate")
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        log.info("开始创建stringredis模板对象...");
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(factory);
        return template;
    }
    //json类型模板对象
    @Bean
    @Qualifier("JsonRedisTemplate")
    public RedisTemplate JsonRedisTemplate(RedisConnectionFactory factory) {
        log.info("开始创建jsonredis模板对象...");
        RedisTemplate template = new RedisTemplate();
        //设置redis连接工厂
        template.setConnectionFactory(factory);
        //设置key的序列化器
        template.setKeySerializer(new StringRedisSerializer());
        //设置value的序列化器,使用json序列化器
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        //设置hash的key的序列化器
        template.setHashKeySerializer(new StringRedisSerializer());
        //设置hash的value的序列化器,使用json序列化器
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }

}
