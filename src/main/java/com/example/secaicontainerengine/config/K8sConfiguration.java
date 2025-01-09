package com.example.secaicontainerengine.config;


import com.fasterxml.jackson.dataformat.yaml.util.StringQuotingChecker;
import io.fabric8.kubernetes.client.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

@Configuration
@Slf4j
public class K8sConfiguration {

    @Value("${k8s.config}")
    String configPath;

    @Bean
    public KubernetesClient K8sClient() {
        InputStream kubeConfigStream = getClass().getClassLoader().getResourceAsStream(configPath);
        if (kubeConfigStream == null) {
            throw new IllegalArgumentException("找不到config文件");
        }
        Config config = Config.fromKubeconfig(convertStreamToString(kubeConfigStream));
        return new KubernetesClientBuilder()
                .withConfig(config)
                .build();
    }

    // 将 InputStream 转为 String 的工具方法
    private static String convertStreamToString(InputStream inputStream) {
        try (Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8)) {
            return scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
        }
    }
}
