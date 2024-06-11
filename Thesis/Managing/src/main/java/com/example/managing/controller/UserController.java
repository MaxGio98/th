package com.example.managing.controller;

import com.example.managing.utils.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;

import static com.example.managing.utils.ParseManagingMessage.parsePostBody;

@RestController
public class UserController {
    @Autowired
    UserService userService;

    @GetMapping("/getOnlineNodes")
    public ResponseEntity<ArrayList<String>> getOnlineNodes() {
        return userService.returnOnlineNodes();
    }

    @GetMapping("/getOnlineNodesAndTypes")
    public ResponseEntity<HashMap<String, Integer>> getOnlineNodesAndTypes() {
        return userService.returnOnlineNodesAndTypes();
    }

    @GetMapping("/calculateHealth")
    public ResponseEntity<String> calculateHealth() {
        return userService.returnHealthyPercentage();
    }

    @GetMapping("/plotHealth")
    public void plotHealth(HttpServletResponse response) {
        userService.plotHealth(response);
    }

    @GetMapping("/nodesAndNeighbors")
    public ResponseEntity<String> nodesAndNeighbors() {
        return userService.nodesAndNeighbors();
    }

    @PostMapping("/addNeighbor")
    public ResponseEntity<String> addNeighbor(@RequestBody String message) {
        return userService.manageRequest(parsePostBody(message), 0);
    }

    @PostMapping("/removeNeighbor")
    public ResponseEntity<String> removeNeighbor(@RequestBody String message) {
        return userService.manageRequest(parsePostBody(message), 1);
    }

    @PostMapping("/addNode")
    public ResponseEntity<String> addNode(@RequestBody String message) {
        return userService.addNode(parsePostBody(message));
    }

    @PostMapping("/removeNode")
    public ResponseEntity<String> removeNode(@RequestBody String message) {
        return userService.removeNode(parsePostBody(message));
    }

}
