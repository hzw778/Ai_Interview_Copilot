package interview.guide;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI Interview Platform - Main Application
 * 智能AI面试官平台 - 主启动类
 */
@SpringBootApplication
@MapperScan(basePackages = "interview.guide", annotationClass = Mapper.class)
public class App {

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}
