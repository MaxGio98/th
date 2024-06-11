package com.example.vulnerablenode.controller;

import com.example.vulnerablenode.model.ForwardRow;
import com.example.vulnerablenode.model.Message.Message;
import com.example.vulnerablenode.utils.service.VulnerableNodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import static com.example.vulnerablenode.utils.parsingJsonMessage.ParseNodeMessage.parsePostBody;

@RestController
public class VulnerableNodeController {

    @Autowired
    private VulnerableNodeService n;

    //used to check all the table of the node
    @GetMapping("/showTable")
    public ConcurrentHashMap<String, ForwardRow> showTable() {
        return n.getNode().getNodeTable();
    }

    @PostMapping("/sendMessage")
    public void sendMessage(@RequestBody String message) {
        Message m = parsePostBody(message);
        n.manageMessage(m);
    }

    @GetMapping("/triggerDiscovery")
    public void triggerDiscovery() {
        n.triggerDiscovery();
    }

    //trigger ping, with get method, with variable destinationId
    @GetMapping("/triggerPing/{destinationId}")
    public void triggerPing(@PathVariable String destinationId) {
        n.triggerPing(destinationId);
    }

    //api used in order to generate the graph
    @GetMapping("/isInfected")
    //return the boolean isinfected and the type of the node
    public ArrayList<Object> isInfectedAndType() {
        return n.isInfectedAndType();
    }

    //used by the infector to create an outbreak
    @GetMapping("/infect")
    public void infect()
    {
        n.outbreak();
    }

}
