package com.example.secaicontainerengine;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.secaicontainerengine.mapper")
public class SecAiContainerEngineApplication {

	public static void main(String[] args) {
		SpringApplication.run(SecAiContainerEngineApplication.class, args);
	}

}
