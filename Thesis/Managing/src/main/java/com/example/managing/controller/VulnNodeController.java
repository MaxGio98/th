package com.example.managing.controller;

import com.example.managing.utils.service.VulnNodeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
public class VulnNodeController {
    @Autowired
    VulnNodeService vulnNodeService;

    @GetMapping("/contact")
    public ResponseEntity<Integer> contact(HttpServletRequest request) {
        System.out.println("Contacted by " + request.getRemoteAddr());
        return vulnNodeService.contact(request.getRemoteAddr());
    }

    @GetMapping("/outbreakDetected")
    public void outbreakDetected(HttpServletRequest request) {
        vulnNodeService.outbreakDetected(request);
    }

    @GetMapping("/infectionDetected")
    public ResponseEntity<String> infectionDetected(HttpServletRequest request) {
        return vulnNodeService.infectionDetected(request);
    }

    @GetMapping("/healed")
    public ResponseEntity<String> healedNode(HttpServletRequest request) {
        return vulnNodeService.healedNode(request);
    }
}
