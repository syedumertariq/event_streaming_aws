package com.eventstreaming.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

@RestController
@RequestMapping("/api/v1/diagnostic")
public class DiagnosticController {

    @Autowired
    private ApplicationContext applicationContext;

    @GetMapping("/beans")
    public ResponseEntity<String> listBeans() {
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        StringBuilder sb = new StringBuilder();
        sb.append("Total beans: ").append(beanNames.length).append("\n");
        
        // Look for our specific beans
        for (String beanName : beanNames) {
            if (beanName.contains("UserActor") || beanName.contains("Controller") || beanName.contains("actorSystem")) {
                sb.append("Found: ").append(beanName).append("\n");
            }
        }
        
        return ResponseEntity.ok(sb.toString());
    }

    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("Diagnostic controller is working");
    }
}