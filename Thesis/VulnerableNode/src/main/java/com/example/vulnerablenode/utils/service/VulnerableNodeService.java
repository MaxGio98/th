package com.example.vulnerablenode.utils.service;

import com.example.vulnerablenode.model.ForwardRow;
import com.example.vulnerablenode.model.Message.Message;
import com.example.vulnerablenode.model.Node;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.*;

@Service
public class VulnerableNodeService {
    private final String managingURL = "http://host.docker.internal";
    @Autowired
    Environment environment;
    private Node node;
    private ArrayList<String> pingUUIDs;
    private HttpClient client;
    private HashMap<String, ArrayList<String>> discoverySentUUIDsAndNeigh;
    private ArrayList<String> bannedDiscoveryUUIDs;
    private ExecutorService executor;
    @Value("${node.healT}")
    private double healT;

    public Node getNode() {
        return node;
    }

    @EventListener(ApplicationReadyEvent.class)
    private void initializeNode() {
        String id = "";
        try {
            InetAddress inetAddress = InetAddress.getLocalHost();
            id = inetAddress.getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        node = new Node(id, -1, false, new ConcurrentHashMap<>());
        System.out.println(node.getId());
        client = HttpClient.newHttpClient();
        discoverySentUUIDsAndNeigh = new HashMap<>();
        pingUUIDs = new ArrayList<>();
        bannedDiscoveryUUIDs = new ArrayList<>();
        executor = Executors.newSingleThreadExecutor();
        contactManagingPort();
    }

    private void contactManagingPort() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("node", node.getId());
            CompletableFuture<HttpResponse<String>> future = sendAsyncGET("/contact");
            future.thenAccept(response -> {
                if (response.statusCode() == 200) {
                    //the response body is an integer, which is the type
                    int type = Integer.parseInt(response.body());
                    node.setType(type);
                }
            });
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public void manageMessage(Message message) {
        switch (message.getMessageType()) {
            case 0:
                retrieveTableFromManaging(message);
                break;
            case 1:
                executor.submit(() -> discovery(message));
                break;
            case 2:
                managePing(message);
                break;
            case 3:
                addNewNeighbor(message);
                break;
            case 4:
                updateNodeTable(message);
                break;
            case 5:
                internalInfection(message);
                break;
            default:
                break;
        }
    }

    private void retrieveTableFromManaging(Message message) {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode messageContentNode = objectMapper.valueToTree(message.getMessageContent());
        JsonNode tableNode = messageContentNode.get("table");
        if (tableNode != null && tableNode.isObject()) {
            //clear the node table
            node.getNodeTable().clear();
            Iterator<Map.Entry<String, JsonNode>> fields = tableNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = entry.getKey();
                if (!key.equals(node.getId())) {
                    JsonNode value = entry.getValue();
                    int distance = value.get("distance").asInt();
                    String nextHop = value.get("nextHop").asText();
                    ForwardRow forwardRow = new ForwardRow(nextHop, distance);
                    node.getNodeTable().put(key, forwardRow);
                }
            }
        }
    }

    public void triggerDiscovery() {
        UUID uuid = UUID.randomUUID();
        ConcurrentHashMap<String, ForwardRow> neighborTable = node.getNodeTable();
        for (Map.Entry<String, ForwardRow> entry : neighborTable.entrySet()) {
            if (entry.getValue().distance == 1) {
                JSONObject jsonObject = new JSONObject();
                try {
                    jsonObject.put("senderId", node.getId());
                    jsonObject.put("destinationId", entry.getKey());
                    jsonObject.put("messageType", 1);
                    JSONObject messageContent = new JSONObject();
                    JSONObject tableObject = getJsonTable(neighborTable);
                    messageContent.put("table", tableObject);
                    messageContent.put("uuid", uuid.toString());
                    jsonObject.put("messageContent", messageContent);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                CompletableFuture<HttpResponse> f = sendAsyncMessagePOST(jsonObject, entry.getKey());
                f.thenAccept(response -> {
                    if (response.statusCode() == 200) {
                        System.out.println("Triggered to " + entry.getKey());
                    }
                });
            }
        }
    }

    private void discovery(Message message) throws ConcurrentModificationException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode messageContentNode = objectMapper.valueToTree(message.getMessageContent());
        String uuid = messageContentNode.get("uuid").asText();
        if (!bannedDiscoveryUUIDs.contains(uuid)) {
            HashMap<String, ForwardRow> neighborTable = new HashMap<>();
            JsonNode tableNode = messageContentNode.get("table");
            if (tableNode != null && tableNode.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = tableNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    String key = entry.getKey();
                    if (!key.equals(node.getId()) && !key.equals(message.getSenderId())) {
                        JsonNode value = entry.getValue();
                        int distance = value.get("distance").asInt()+1;
                        //if (distance != Integer.MAX_VALUE) {
                            //distance++;
                        String nextHop = message.getSenderId();
                        ForwardRow forwardRow = new ForwardRow(nextHop, distance);
                        neighborTable.put(key, forwardRow);
                        //}
                    }
                }
            }
            try {
                boolean isChanged = false;
                ConcurrentHashMap<String, ForwardRow> updatedRows = new ConcurrentHashMap<>();
                //only neighbors can send this type of message. if an existing node has now a new neighbor,
                //the recevier has to update the distance to 1 and the nextHop to the new neighbor
                if (!node.getNodeTable().containsKey(message.getSenderId()) || node.getNodeTable().get(message.getSenderId()).distance != 1) {
                    //new node added
                    ForwardRow f = new ForwardRow(message.getSenderId(), 1);
                    node.getNodeTable().put(message.getSenderId(), f);
                    updatedRows.put(message.getSenderId(), f);
                    isChanged = true;
                }
                for (Map.Entry<String, ForwardRow> entry : neighborTable.entrySet()) {
                    if (!node.getNodeTable().containsKey(entry.getKey())) {
                        node.getNodeTable().put(entry.getKey(), entry.getValue());
                        updatedRows.put(entry.getKey(), entry.getValue());
                        isChanged = true;
                    } else if (entry.getValue().distance < node.getNodeTable().get(entry.getKey()).distance) {
                        node.getNodeTable().put(entry.getKey(), entry.getValue());
                        updatedRows.put(entry.getKey(), entry.getValue());
                        isChanged = true;
                    }
                }
                if (!discoverySentUUIDsAndNeigh.containsKey(uuid)) {
                    discoverySentUUIDsAndNeigh.put(uuid, new ArrayList<>());
                }
                if (!discoverySentUUIDsAndNeigh.get(uuid).contains(message.getSenderId())) {
                    for (Map.Entry<String, ForwardRow> entry : node.getNodeTable().entrySet()) {
                        if (entry.getValue().distance == 1) {
                            sendAsyncMessagePOST(buildDiscoveryMessage(entry, uuid, node.getNodeTable()), entry.getKey());
                        }
                    }
                    discoverySentUUIDsAndNeigh.get(uuid).add(message.getSenderId());
                } else {
                    for (Map.Entry<String, ForwardRow> entry : node.getNodeTable().entrySet()) {
                        if (entry.getValue().distance == 1) {
                            if (isChanged) {
                                sendAsyncMessagePOST(buildDiscoveryMessage(entry, uuid, updatedRows), entry.getKey());
                            }
                        }
                    }
                }
            } catch (ConcurrentModificationException e) {
                System.out.println("Concurrent modification, aborting modification");
                e.printStackTrace();
            }
        }
    }

    private JSONObject buildDiscoveryMessage(Map.Entry<String, ForwardRow> entry, String uuid, ConcurrentHashMap<String, ForwardRow> updatedRows) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("senderId", node.getId());
            jsonObject.put("destinationId", entry.getKey());
            jsonObject.put("messageType", 1);
            JSONObject messageContent = new JSONObject();
            // Costruisci un array di JSONObjects per rappresentare ogni ForwardRow
            JSONObject tableObject = getJsonTable(updatedRows);
            messageContent.put("table", tableObject);
            messageContent.put("uuid", uuid);
            jsonObject.put("messageContent", messageContent);
            return jsonObject;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    private JSONObject getJsonTable(ConcurrentHashMap<String, ForwardRow> rows) {
        JSONObject tableObject = new JSONObject();
        for (Map.Entry<String, ForwardRow> e : rows.entrySet()) {
            String key = e.getKey();
            ForwardRow forwardRow = e.getValue();
            JSONObject forwardRowJson = new JSONObject();
            forwardRowJson.put("nextHop", forwardRow.nextHop);
            forwardRowJson.put("distance", forwardRow.distance);
            tableObject.put(String.valueOf(key), forwardRowJson);
        }
        return tableObject;
    }

    private void managePing(Message message) {
        if (message.getDestinationId().equals(node.getId())) {
            ObjectNode messageContentNode = (ObjectNode) message.getMessageContent();
            String pingId = messageContentNode.get("pingId").asText();
            if (!pingUUIDs.contains(pingId)) {
                System.out.println("Ping arrived at destination \n_____________________");
                String nextHop = node.getNodeTable().get(message.getSenderId()).nextHop;
                //ping back
                CompletableFuture<HttpResponse> response = sendAsyncMessagePOST(buildPingMessage(message, node.getId(), message.getSenderId()), nextHop);
                if (!response.isDone()) {
                    System.out.println("Error: " + nextHop + " is not reachable from " + node.getId());
                } else {
                    response.thenAccept(r -> {
                        if (r.statusCode() == 200) {
                            System.out.println("Ping back sent " + node.getId() + " -> " + nextHop);
                        }
                    });
                }
            } else {
                //ping back received
                pingUUIDs.remove(pingId);
                System.out.println("Ping back received " + node.getId() + "\n___________________\n");
            }
        } else {
            //get nextHop to reach the destination
            String nextHop = node.getNodeTable().get(message.getDestinationId()).nextHop;
            //forward the message async
            CompletableFuture<HttpResponse> response = sendAsyncMessagePOST(buildPingMessage(message, message.getSenderId(), message.getDestinationId()), nextHop);
            if (!response.isDone()) {
                System.out.println("Error: " + nextHop + " is not reachable from " + node.getId());
            } else {
                response.thenAccept(r -> {
                    if (r.statusCode() == 200) {
                        System.out.println("Ping forwarded " + node.getId() + " -> " + nextHop);
                    }
                });
            }
        }
    }

    //build a ping message
    private JSONObject buildPingMessage(Message message, String fromId, String toId) {
        ObjectNode messageContentNode = (ObjectNode) message.getMessageContent();
        String pingId = messageContentNode.get("pingId").asText();
        JSONObject jsonObject = new JSONObject();
        JSONObject messageContent = new JSONObject();
        try {
            jsonObject.put("senderId", fromId);
            jsonObject.put("destinationId", toId);
            jsonObject.put("messageType", 2);
            messageContent.put("pingId", pingId);
            jsonObject.put("messageContent", messageContent);
            return jsonObject;

        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public void triggerPing(String destinationId) {
        UUID uuid = UUID.randomUUID();
        String pingId = uuid.toString();
        pingUUIDs.add(pingId);
        String nextHop = node.getNodeTable().get(destinationId).nextHop;
        JSONObject messageContent = new JSONObject();
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("senderId", node.getId());
            jsonObject.put("destinationId", destinationId);
            jsonObject.put("messageType", 2);
            messageContent.put("pingId", pingId);
            jsonObject.put("messageContent", messageContent);
            CompletableFuture<HttpResponse<Void>> future = sendAsyncMessagePOST(jsonObject, nextHop);
            if (!future.isDone()) {
                System.out.println("Error: " + nextHop + " is not reachable from " + node.getId());
            } else {
                future.thenAccept(response -> {
                    System.out.println(response.statusCode());

                    if (response.statusCode() == 200) {
                        //check every 5 seconds if the ping back is received. if not received after 20 seconds, print an error message and stop the check
                        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
                        executor.scheduleAtFixedRate(() -> {
                            if (pingUUIDs.contains(pingId)) {
                                System.out.println("Ping back not received from " + destinationId);
                            } else {
                                System.out.println("Ping back received from " + destinationId);
                                executor.shutdown();
                            }
                        }, 100, 1000, TimeUnit.MILLISECONDS);
                    } else {
                        System.out.println("Error: " + nextHop + " is not reachable from " + node.getId());
                    }
                });
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    private void updateNodeTable(Message message) {
        //put all the keys of discoverySent in bannedDiscoveryUUIDs
        bannedDiscoveryUUIDs = new ArrayList<>(discoverySentUUIDsAndNeigh.keySet());
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode messageContentNode = objectMapper.valueToTree(message.getMessageContent());
        JsonNode nodesToRemove = messageContentNode.get("nodesToRemove");
        if (nodesToRemove != null) {
            for (JsonNode n : nodesToRemove) {
                String nodeNotNeighborAnymore = n.asText();
                System.out.println("Node to remove: " + nodeNotNeighborAnymore);
                if (node.getNodeTable().containsKey(nodeNotNeighborAnymore)) {
                    System.out.println("found");
                }
                node.getNodeTable().remove(nodeNotNeighborAnymore);
                if (!node.getNodeTable().containsKey(nodeNotNeighborAnymore)) {
                    System.out.println("removed");
                }
            }
        }
        ArrayList<String> toRemove = new ArrayList<>();
        for (String key : node.getNodeTable().keySet()) {
            if (node.getNodeTable().get(key).distance != 1) {
                toRemove.add(key);
            }
        }
        for (String key : toRemove) {
            node.getNodeTable().remove(key);
            System.out.println("removed " + key);
        }
    }


    private void addNewNeighbor(Message message) {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode messageContentNode = objectMapper.valueToTree(message.getMessageContent());
        JsonNode nodes = messageContentNode.get("nodes");
        for (JsonNode n : nodes) {
            String newNeighborId = n.asText();
            node.getNodeTable().get(newNeighborId).nextHop = newNeighborId;
            node.getNodeTable().get(newNeighborId).distance = 1;
        }
    }


    public ArrayList<Object> isInfectedAndType() {
        ArrayList<Object> result = new ArrayList<>();
        result.add(node.isInfected());
        result.add(node.getType());
        return result;
    }

    public void outbreak() {
        sendAsyncGET("/outbreakDetected");
        infect();
    }

    private void internalInfection(Message message) {
        if (!node.isInfected()) {
            //retrieve the value in messageContent, it is a integer
            IntNode intNode = (IntNode) message.getMessageContent();
            int type = intNode.intValue();
            if (type == node.getType()) {
                infect();
            }
        }
    }

    private void infect() {
        if (!node.isInfected()) {
            ScheduledExecutorService infectionExecutor = new ScheduledThreadPoolExecutor(1);
            node.setInfected(true);
            //start the infection process
            sendAsyncGET("/infectionDetected");
            double rand = new Random().nextDouble();
            //2.5 in app.properties
            double value = 2 * (rand * healT);
            long time = Math.round(value);
            //print time in seconds
            long startTime = System.currentTimeMillis();
            infectionExecutor.scheduleAtFixedRate(() -> {
                if (TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - startTime) >= time) {
                    //self Heal
                    node.setInfected(false);
                    sendAsyncGET("/healed");
                    infectionExecutor.shutdown();
                } else {
                    for (Map.Entry<String, ForwardRow> entry : node.getNodeTable().entrySet()) {
                        if (entry.getValue().distance == 1) {
                            JSONObject jsonObject = getInternalInfectionJson(entry);
                            sendAsyncMessagePOST(jsonObject, entry.getKey());
                        }
                    }
                }
            }, 1, 1, TimeUnit.SECONDS);
            //selfHeal(time);
        }
    }

    private JSONObject getInternalInfectionJson(Map.Entry<String, ForwardRow> entry) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("senderId", node.getId());
            jsonObject.put("destinationId", entry.getKey());
            jsonObject.put("messageType", 5);
            jsonObject.put("messageContent", node.getType());
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return jsonObject;
    }

    private CompletableFuture sendAsyncMessagePOST(JSONObject jsonObject, String neighborId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + neighborId + "/sendMessage"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonObject.toString()))
                .build();
        // Invia la richiesta in modo asincrono e ottieni un CompletableFuture
        return client.sendAsync(request, HttpResponse.BodyHandlers.discarding());
    }

    private CompletableFuture sendAsyncGET(String route) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(managingURL + route))
                .header("Content-Type", "application/json")
                .GET()
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }
        /*private void resetDistanceNotNeighbors() {
        ArrayList<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, ForwardRow> entry : node.getNodeTable().entrySet()) {
            if (entry.getValue().distance != 1) {
                toRemove.add(entry.getKey());
            }
        }
        for (String key : toRemove) {
            System.out.println("Removing " + key);
            node.getNodeTable().remove(key);
        }
        //print node table
        System.out.println("Node table after reset");
        for (Map.Entry<String, ForwardRow> entry : node.getNodeTable().entrySet()) {
            System.out.println(entry.getKey() + " -> " + entry.getValue().nextHop + " " + entry.getValue().distance);
        }
    }*/
}
