package com.example.managing.utils.service;

import com.example.managing.model.ManagingMessage;
import com.example.managing.model.NodeMessage;
import com.example.managing.model.NodeStatus;
import com.example.managing.simple.plot.Plotter;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.model.Container;
import jakarta.servlet.http.HttpServletResponse;
import org.jfree.chart.ChartUtils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static com.example.managing.ManagingApplication.*;
import static com.example.managing.utils.service.VulnNodeService.*;

@Service
public class UserService {
    @Value("${managing.minutesForImprove}")
    private int minutesForImprove;

    public ResponseEntity<ArrayList<String>> returnOnlineNodes() {
        return new ResponseEntity<>(getOnlineNodes(), HttpStatus.OK);
    }

    public ArrayList<String> getOnlineNodes() {
        return new ArrayList<>(network.keySet());
    }

    public ResponseEntity<HashMap<String, Integer>> returnOnlineNodesAndTypes() {
        HashMap<String, Integer> response = new HashMap<>();
        for (String node : network.keySet()) {
            response.put(node, network.get(node).getType());
        }
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    public ResponseEntity<String> returnHealthyPercentage() {

        double healthyPercentage = getHealthyPercentageValue();
        return new ResponseEntity<>("Healthy percentage: " + String.format("%.2f", healthyPercentage) + "%", HttpStatus.OK);
    }

    private double getHealthyPercentageValue() {
        int healthyNodes = 0;
        for (String node : network.keySet()) {
            if (!network.get(node).isInfected()) {
                healthyNodes++;
            }
        }
        return (double) healthyNodes / network.size() * 100;
    }

    public void plotHealth(HttpServletResponse response) {
        double[] result = healthyResults.stream().mapToDouble(i -> i).toArray();
        Plotter p = new Plotter(
                "Network simulation",
                "Proportion of healthy nodes", 60*minutesForImprove);
        p.addDataset(result, 0, result.length);
        BufferedImage i = p.getBufferedImage();
        response.setContentType("image/jpeg");
        try {
            ChartUtils.writeBufferedImageAsJPEG(response.getOutputStream(), i);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public ResponseEntity<String> nodesAndNeighbors() {
        //in the response print the network keys and their neighbors
        StringBuilder response = new StringBuilder();
        for (String node : network.keySet()) {
            response.append(node).append(": ").append(network.get(node).getNeighbors()).append("\n");
        }
        return new ResponseEntity<>(response.toString(), HttpStatus.OK);
    }

    public ResponseEntity<String> manageRequest(ManagingMessage message, int requestType) {
        if (getOnlineNodes().contains(message.getDestinationId())) {
            if (message.getNodes().isEmpty()) {
                //return error, the field nodes has to be specified and not empty, write in the response the correct format
                return new ResponseEntity<>("400 - BAD REQUEST: The field nodes has to be specified and not empty, correct format: {\"destinationId\": int, \"nodes\": [int, int, ...]}", HttpStatus.BAD_REQUEST);
            } else {
                if (message.getNodes().contains(message.getDestinationId())) {
                    return new ResponseEntity<>("400 - BAD REQUEST: The node can't be its own neighbor", HttpStatus.BAD_REQUEST);
                }
                //check that message.getnodes exist in the onlineNodes list
                for (int i = 0; i < message.getNodes().size(); i++) {
                    if (!getOnlineNodes().contains(message.getNodes().get(i))) {
                        return new ResponseEntity<>("400 - BAD REQUEST: The node " + message.getNodes().get(i) + " doesn't exist", HttpStatus.BAD_REQUEST);
                    }
                }
                return switch (requestType) {
                    case 0 -> addNeighbor(message);
                    case 1 -> removeNeighbor(message);
                    default ->
                            new ResponseEntity<>("400 - BAD REQUEST: The request type is not valid", HttpStatus.BAD_REQUEST);
                };
            }
        } else {
            return new ResponseEntity<>("400 - BAD REQUEST: The node " + message.getDestinationId() + " doesn't exists", HttpStatus.BAD_REQUEST);
        }
    }

    public ResponseEntity<String> addNeighbor(ManagingMessage message) {
        //check that message.getdestinationid exists
        if (!network.containsKey(message.getDestinationId())) {
            return new ResponseEntity<>("400 - BAD REQUEST: The node " + message.getDestinationId() + " doesn't exist", HttpStatus.BAD_REQUEST);
        }
        //check that message.getnodes exist
        for (int i = 0; i < message.getNodes().size(); i++) {
            if (!network.containsKey(message.getNodes().get(i))) {
                return new ResponseEntity<>("400 - BAD REQUEST: The node " + message.getNodes().get(i) + " doesn't exist", HttpStatus.BAD_REQUEST);
            }
            if (network.get(message.getDestinationId()).getNeighbors().contains(message.getNodes().get(i))) {
                return new ResponseEntity<>("400 - BAD REQUEST: The node " + message.getNodes().get(i) + " is already a neighbor of " + message.getDestinationId(), HttpStatus.BAD_REQUEST);
            }
        }
        //send the message to the destination node
        JSONObject messageContent = new JSONObject();
        messageContent.put("nodes", message.getNodes());
        NodeMessage nodeMessage = new NodeMessage("", message.getDestinationId(), 3, messageContent);
        CompletableFuture<HttpResponse> response = sendAsyncMessagePOST(buildMessage(nodeMessage), message.getDestinationId());
        response.thenAccept(res -> {
            if (res.statusCode() == 200) {
                //add the neighbors to the destination node
                for (String node : message.getNodes()) {
                    network.get(node).getNeighbors().add(message.getDestinationId());
                    network.get(message.getDestinationId()).getNeighbors().add(node);
                    //the discovery is triggered by the message.getdestinationid node
                }
                triggerDiscovery(message.getDestinationId());


            }
        });
        return new ResponseEntity<>("200 - OK: Neighbors added", HttpStatus.OK);
    }

    public ResponseEntity<String> removeNeighbor(ManagingMessage message) {

        ArrayList<String> destinationNeighbors = network.get(message.getDestinationId()).getNeighbors();
        if (destinationNeighbors.size() - message.getNodes().size() < 2) {
            return new ResponseEntity<>("400 - BAD REQUEST: The node " + message.getDestinationId() + " has to have at least two neighbors", HttpStatus.BAD_REQUEST);
        }
        //check if the nodes are neighbors
        for (int i = 0; i < message.getNodes().size(); i++) {
            if (!destinationNeighbors.contains(message.getNodes().get(i))) {
                return new ResponseEntity<>("400 - BAD REQUEST: The node " + message.getNodes().get(i) + " is not a neighbor of " + message.getDestinationId(), HttpStatus.BAD_REQUEST);
            }
            if (network.get(message.getNodes().get(i)).getNeighbors().size() - 1 < 2) {
                return new ResponseEntity<>("400 - BAD REQUEST: The node " + message.getNodes().get(i) + " has to have at least two neighbors", HttpStatus.BAD_REQUEST);
            }
        }
        HashMap<String, ArrayList<String>> modifiedNetwork = new HashMap<>();
        for (String node : network.keySet()) {
            modifiedNetwork.put(node, new ArrayList<>(network.get(node).getNeighbors()));
        }
        for (String node : message.getNodes()) {
            modifiedNetwork.get(node).remove(message.getDestinationId());
            modifiedNetwork.get(message.getDestinationId()).remove(node);
        }
        if (isNetworkSplit(modifiedNetwork)) {
            return new ResponseEntity<>("400 - BAD REQUEST: The network would be split in more than one component", HttpStatus.BAD_REQUEST);
        }
        removeNeighbors(message.getDestinationId(), message.getNodes());
        for (String node : network.keySet()) {
            network.get(node).setIsReady(false);
        }
        removeNeighborsUpdateTable(message.getDestinationId(), message.getNodes());
        return new ResponseEntity<>("200 - OK: Neighbors removed", HttpStatus.OK);
    }

    private boolean isNetworkSplit(HashMap<String, ArrayList<String>> modifiedNetwork) {
        // Segno tutti i nodi come non visitati
        boolean[] visited = new boolean[modifiedNetwork.size()];
        // Inizio la ricerca BFS da un nodo casuale
        String startNode = new ArrayList<>(modifiedNetwork.keySet()).getFirst();
        visited[0] = true;
        // Coda per la BFS
        Queue<String> queue = new LinkedList<>();
        queue.add(startNode);
        while (!queue.isEmpty()) {
            String currentNode = queue.poll();
            // Esplora i vicini non visitati del nodo corrente
            for (String neighbor : modifiedNetwork.get(currentNode)) {
                if (!visited[new ArrayList<>(modifiedNetwork.keySet()).indexOf(neighbor)]) {
                    visited[new ArrayList<>(modifiedNetwork.keySet()).indexOf(neighbor)] = true;
                    queue.add(neighbor);
                }
            }
        }
        //check that all nodes are visited
        for (int i = 0; i < visited.length; i++) {
            if (!visited[i]) {
                return true;
            }
        }
        return false;
    }

    private void removeNeighbors(String node, ArrayList<String> neighbors) {
        for (String neighbor : neighbors) {
            network.get(node).getNeighbors().remove(neighbor);
            network.get(neighbor).getNeighbors().remove(node);
        }
    }

    private void removeNeighborsUpdateTable(String n, ArrayList<String> neighbors) {
        for (String node : network.keySet()) {
            JSONObject messageContent = new JSONObject();
            if (node.equals(n)) {
                messageContent.put("nodesToRemove", neighbors);
            } else if (neighbors.contains(node)) {
                messageContent.put("nodesToRemove", new ArrayList<>(Collections.singletonList(n)));
            } else {
                messageContent.put("nodesToRemove", new ArrayList<>());
            }
            network.get(node).setIsReady(false);
            sendMessageNeighborsToRemove(node, messageContent);
        }
    }

    private void sendMessageNeighborsToRemove(String node, JSONObject messageContent) {
        NodeMessage nodeMessage = new NodeMessage("", node, 4, messageContent);
        CompletableFuture<HttpResponse> response = sendAsyncMessagePOST(buildMessage(nodeMessage), node);
        response.thenAccept(res -> {
            if (res.statusCode() == 200) {
                network.get(node).setIsReady(true);
                //if all nodes are ready, start discovery
                if (network.values().stream().allMatch(NodeStatus::isReady)) {
                    triggerDiscovery(getOnlineNodes().getFirst());
                }
            } else {
                System.out.println("Error in sending message to " + node + " " + res.statusCode());
            }
        });
    }

    public ResponseEntity<String> addNode(ManagingMessage message) {
        if (message.getNodes().isEmpty()) {
            return new ResponseEntity<>("400 - BAD REQUEST: The field nodes has to be specified and not empty, correct format: {\"nodes\": [int, int, ...]}", HttpStatus.BAD_REQUEST);
        }
        if (message.getNodes().size() < 2) {
            return new ResponseEntity<>("400 - BAD REQUEST: The node has to have at least two neighbors", HttpStatus.BAD_REQUEST);
        }
        //retrieve neighbors from the message.nodes
        ArrayList<String> neighbors = message.getNodes();
        for (String neighbor : neighbors) {
            if (!getOnlineNodes().contains(neighbor)) {
                return new ResponseEntity<>("400 - BAD REQUEST: The node " + neighbor + " doesn't exist", HttpStatus.BAD_REQUEST);
            }
        }
        System.out.println("node n." + containerIds.size());
        String imageName = "node";
        CreateContainerResponse container = dockerClient.createContainerCmd(imageName).withHostConfig(hostConfig).exec();
        containerIds.add(container.getId());
        dockerClient.startContainerCmd(container.getId()).exec();
        newNodeNeighbors.add(neighbors);
        return new ResponseEntity<>("200 - OK: Node added", HttpStatus.OK);
    }

    public ResponseEntity<String> removeNode(ManagingMessage message) {
        if (!network.containsKey(message.getDestinationId())) {
            return new ResponseEntity<>("400 - BAD REQUEST: The node " + message.getDestinationId() + " doesn't exist", HttpStatus.BAD_REQUEST);
        }
        for (String neighbor : network.get(message.getDestinationId()).getNeighbors()) {
            if (network.get(neighbor).getNeighbors().size() - 1 < 2) {
                return new ResponseEntity<>("400 - BAD REQUEST: The node " + neighbor + " has to have at least two neighbors", HttpStatus.BAD_REQUEST);
            }
        }
        HashMap<String, ArrayList<String>> modifiedNetwork = new HashMap<>();
        for (String node : network.keySet()) {
            if (!node.equals(message.getDestinationId())) {
                modifiedNetwork.put(node, new ArrayList<>());
                for (String neighbor : network.get(node).getNeighbors()) {
                    if (!neighbor.equals(message.getDestinationId())) {
                        modifiedNetwork.get(node).add(neighbor);
                    }
                }
            }

        }

        if (isNetworkSplit(modifiedNetwork)) {
            return new ResponseEntity<>("400 - BAD REQUEST: The network would be split in more than one component", HttpStatus.BAD_REQUEST);
        }

        //remove the desired node from the neighbors
        AtomicInteger everythingOk = new AtomicInteger();
        JSONObject messageContent = new JSONObject();
        messageContent.put("nodesToRemove", new ArrayList<>(Collections.singletonList(message.getDestinationId())));
        int networkSize = network.size();
        for (int i = 0; i < network.size(); i++) {
            //get key of i element
            String node = new ArrayList<>(network.keySet()).get(i);
            if (!node.equals(message.getDestinationId())) {
                //CompletableFuture<HttpResponse> response = sendAsyncMessagePOST(buildMessage(new NodeMessage("", node, 4, message.getDestinationId())), node);
                CompletableFuture<HttpResponse> response = sendAsyncMessagePOST(buildMessage(new NodeMessage("", node, 4, messageContent)), node);
                response.thenAccept(res -> {
                    if (res.statusCode() == 200) {
                        everythingOk.getAndIncrement();
                        if (everythingOk.get() == networkSize - 2) {
                            ListContainersCmd listContainersCmd = dockerClient.listContainersCmd();
                            listContainersCmd.withShowAll(true);
                            for (Container container : listContainersCmd.exec()) {
                                String containerId = container.getId();
                                String containerName = container.getNames()[0];
                                String containerIp = dockerClient.inspectContainerCmd(containerId).exec().getNetworkSettings().getNetworks().get("bridge").getIpAddress();
                                System.out.println(containerIp + " " + containerName);
                                if (containerIp.equals(message.getDestinationId())) {
                                    //print containernames content
                                    containerIds.remove(containerId);
                                    for (String neighbor : network.get(message.getDestinationId()).getNeighbors()) {
                                        network.get(neighbor).getNeighbors().remove(message.getDestinationId());
                                    }
                                    //remove the neighbors from the desired node
                                    network.remove(message.getDestinationId());
                                    dockerClient.stopContainerCmd(container.getId()).exec();
                                    triggerDiscovery(getOnlineNodes().getFirst());
                                    break;
                                }
                            }
                        }
                    }
                });
            }
        }
        return new ResponseEntity<>("200 - OK: Node removed", HttpStatus.OK);

    }

}
