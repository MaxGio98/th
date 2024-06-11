package com.example.managing.utils.service;

import com.example.managing.model.ImproveTime;
import com.example.managing.model.NodeMessage;
import com.example.managing.model.NodeStatus;
import com.example.managing.simple.data.Network;
import com.example.managing.simple.exceptions.NoSatisfyingImprovementFoundException;
import com.example.managing.simple.learning.Learner;
import com.example.managing.utils.NetworkGenerator;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.example.managing.ManagingApplication.*;


@Service
public class VulnNodeService {

    @Value("${managing.numNodes}")
    private int numNodes;
    @Value("${managing.avgNeighNodes}")
    private int avgNeighborsPerNode;
    @Value("${managing.nTypes}")
    private int nTypes;
    @Value("${managing.minutesForImprove}")
    private int minutesForImproveHealth;
    @Value("${managing.healT}")
    private double meanHealT;
    private HashMap<Integer, ArrayList<Integer>> neighbors;
    private boolean init;
    private Learner l;
    private Network n;
    private double[] probAttack;
    private int[] outbreakCounter;
    private boolean firstBlood;
    private ImproveTime improveTime;

    @EventListener(ApplicationReadyEvent.class)
    private void init() {
        init = true;
        probAttack = new double[nTypes];
        outbreakCounter = new int[nTypes];
        initProbAttack();
        neighbors = new HashMap<>();
        firstBlood = false;
        l = new Learner(5000, 5, 0.05);
        n = new Network();
        improveTime = new ImproveTime(0, 0, -1);
        NetworkGenerator n = new NetworkGenerator(numNodes, avgNeighborsPerNode);
        neighbors = n.getNeighbors();
    }

    public static void triggerDiscovery(String node) {
        //get first key of the network
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + node + "/triggerDiscovery"))
                .header("Content-Type", "application/json")
                .GET()
                .build();
        // Invia la richiesta in modo asincrono e ottieni un CompletableFuture
        CompletableFuture<HttpResponse<Void>> response = client.sendAsync(request, HttpResponse.BodyHandlers.discarding());
        response.thenAccept(res -> {
            if (res.statusCode() == 200) {
                System.out.println("Discovery triggered successfully from node " + node);
            } else {
                System.out.println("Error in triggering discovery from node " + node + " " + res.statusCode());
            }
        });
    }

    public static JSONObject buildMessage(NodeMessage message) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("senderId", message.getSenderId());
        jsonObject.put("destinationId", message.getDestinationId());
        jsonObject.put("messageType", message.getMessageType());
        jsonObject.put("messageContent", message.getMessageContent());
        return jsonObject;
    }

    public static CompletableFuture sendAsyncMessagePOST(JSONObject jsonObject, String neighborId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + neighborId + "/sendMessage"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonObject.toString()))
                .build();
        // Invia la richiesta in modo asincrono e ottieni un CompletableFuture
        return client.sendAsync(request, HttpResponse.BodyHandlers.discarding());
    }



    private void initProbAttack() {
        for (int i = 0; i < nTypes; i++) {
            probAttack[i] = 0.0;
        }
    }

    public ResponseEntity<Integer> contact(String url) {
        Random r = new Random();
        int type = r.nextInt((nTypes));
        network.put(url, new NodeStatus(false, new ArrayList<>(), false, type));
        if (init) {
            if (network.size() == numNodes) {
                init = false;
                for (int i = 0; i < numNodes; i++) {
                    String target = getOnlineNodes().get(i);
                    ArrayList<Integer> neighborsIndex = neighbors.get(i);
                    ArrayList<String> neighborsUrl = new ArrayList<>();
                    for (Integer index : neighborsIndex) {
                        neighborsUrl.add(getOnlineNodes().get(index));
                    }
                    network.get(target).setNeighbors(neighborsUrl);
                }
                sendNeighborsToNodes();
            }
        } else {
            //new node added after the init
            network.put(url, new NodeStatus(false, newNodeNeighbors.get(0), false, type));
            for (String neighbor : newNodeNeighbors.getFirst()) {
                network.get(neighbor).getNeighbors().add(url);
            }
            newNodeNeighbors.removeFirst();
            JSONObject nodeTable = createNodeTable(url);
            CompletableFuture<HttpResponse> response = sendAsyncMessagePOST(buildMessage(new NodeMessage("", url, 0, nodeTable)), url);
            response.thenAccept(res -> {
                if (res.statusCode() == 200) {
                    network.get(url).setIsReady(true);
                    triggerDiscovery(url);
                }
            });
        }
        return new ResponseEntity<>(type, HttpStatus.OK);
    }

    private void improveHealth() {
        n.createSystem(network.size(), nTypes, networkToIntArray(), probAttack, nodesTypeToArray(), infectionStatusToArray(), meanHealT, null);
        int initialNumberOfTotalNeighbors = n.countAllNeighbors();
        l.learnProportionHealthy(n);
        if (l.isNeededReconfiguration()) {
            try {
                l.improve(n);
                System.out.println("Total number of edges was: " + initialNumberOfTotalNeighbors + " and now is: "
                        + n.countAllNeighbors() + " with a proportion of healthy of " + l.learnProportionHealthy(n));
                ArrayList<Integer>[] neighbors = n.getNeighbors();
                for (String node : getOnlineNodes()) {
                    network.get(node).setIsReady(false);
                }
                updateNetwork(neighbors);
                improveTime = new ImproveTime(System.currentTimeMillis(), 0, getHealthyPercentageValue());
            } catch (NoSatisfyingImprovementFoundException e) {
                System.out.println("Impossible to improve");
                e.printStackTrace();
            }
        } else {
            System.out.println("It was not needed a reconfiguration!");
        }
    }

    private ArrayList<Integer>[] networkToIntArray() {
        ArrayList<Integer>[] neighbors = new ArrayList[network.size()];
        for (int i = 0; i < network.size(); i++) {
            neighbors[i] = new ArrayList<>();
        }
        for (int i = 0; i < network.size(); i++) {
            neighbors[i] = new ArrayList<>();
        }
        ArrayList<String> keys = getOnlineNodes();
        for (String node : network.keySet()) {
            ArrayList<String> neighborsList = network.get(node).getNeighbors();
            for (String neighbor : neighborsList) {
                int neighIndex = keys.indexOf(neighbor);
                neighbors[keys.indexOf(node)].add(neighIndex);
            }
        }
        return neighbors;
    }

    private int[] nodesTypeToArray() {
        int[] types = new int[network.size()];
        ArrayList<String> keys = getOnlineNodes();
        for (int i = 0; i < network.size(); i++) {
            types[i] = network.get(keys.get(i)).getType();
        }
        return types;
    }

    private boolean[] infectionStatusToArray() {
        boolean[] status = new boolean[network.size()];
        ArrayList<String> keys = getOnlineNodes();
        for (int i = 0; i < network.size(); i++) {
            status[i] = network.get(keys.get(i)).isInfected();
        }
        return status;
    }

    private void updateNetwork(ArrayList<Integer>[] n) {
        //set all nodes as not ready
        for (String node : network.keySet()) {
            network.get(node).setIsReady(false);
        }
        HashMap<String, ArrayList<String>> newNetwork = networkIntToString(n);
        for (String node : network.keySet()) {
            JSONObject messageContent = new JSONObject();
            ArrayList<String> nodesToRemove = new ArrayList<>();
            for (String neighbor : network.get(node).getNeighbors()) {
                if (!newNetwork.get(node).contains(neighbor)) {
                    nodesToRemove.add(neighbor);
                }
            }
            network.get(node).getNeighbors().clear();
            network.get(node).getNeighbors().addAll(newNetwork.get(node));
            messageContent.put("nodesToRemove", nodesToRemove);
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

    private HashMap<String, ArrayList<String>> networkIntToString(ArrayList<Integer>[] newNetwork) {
        HashMap<String, ArrayList<String>> n = new HashMap<>();
        for (int i = 0; i < newNetwork.length; i++) {
            ArrayList<String> neighbors = new ArrayList<>();
            for (int neighbor : newNetwork[i]) {
                neighbors.add(getOnlineNodes().get(neighbor));
            }
            n.put(getOnlineNodes().get(i), neighbors);
        }
        return n;
    }

    private JSONObject createNodeTable(String node) {
        JSONObject nodeTable = new JSONObject();
        JSONObject entries = new JSONObject();
        for (String neighbor : network.get(node).getNeighbors()) {
            JSONObject info = new JSONObject();
            info.put("nextHop", neighbor);
            info.put("distance", 1);
            entries.put(neighbor, info);
        }
        nodeTable.put("table", entries);
        return nodeTable;
    }

    private void sendNeighborsToNodes() {
        for (String node : getOnlineNodes()) {
            //print nodeTable, with every value of the node
            JSONObject nodeTable = createNodeTable(node);
            //send table to node
            CompletableFuture<HttpResponse> response = sendAsyncMessagePOST(buildMessage(new NodeMessage("", node, 0, nodeTable)), node);
            response.thenAccept(res -> {
                if (res.statusCode() == 200) {
                    network.get(node).setIsReady(true);
                    //if all nodes are ready, start discovery
                    if (network.values().stream().allMatch(NodeStatus::isReady)) {
                        triggerDiscovery(getOnlineNodes().getFirst());
                    }
                }
            });
        }
    }

    private double getHealthyPercentageValue() {
        int healthyNodes = 0;
        //from getNetwork, get the number of nodes that are not infected
        for (String node : network.keySet()) {
            if (!network.get(node).isInfected()) {
                healthyNodes++;
            }
        }
        return (double) healthyNodes / network.size() * 100;
    }

    public ResponseEntity<String> infectionDetected(HttpServletRequest url) {
        String node = url.getRemoteAddr();
        network.get(node).setInfected(true);
        if (!firstBlood) {
            firstBlood = true;
            collectHealthyPercentage();
        }
        return new ResponseEntity<>("200 - OK: Infection detected", HttpStatus.OK);
    }

    private void collectHealthyPercentage() {
        AtomicInteger minutes = new AtomicInteger();
        double[][] probAttackPerTypePerSecond = new double[nTypes][60];
        final AtomicInteger seconds = new AtomicInteger(0);
        double[] lastIntervalHealthValues = new double[minutesForImproveHealth];
        Mean calculatorMean = new Mean();
        AtomicBoolean skip = new AtomicBoolean(false);
        Thread t = new Thread(() -> {
            try {
                while (true) {
                    if (seconds.get() == 60) {
                        calculateAvgProbAttackPerTypeLastMinute(probAttackPerTypePerSecond);
                        seconds.set(0);
                        //find average health of last 10 seconds
                        double[] lastMinuteHealth = new double[60];
                        for (int i = 0; i < 60; i++) {
                            lastMinuteHealth[i] = healthyResults.get(healthyResults.size() - 60 + i);
                        }
                        double lastMinuteAverageHealth = calculatorMean.evaluate(lastMinuteHealth, 0, lastMinuteHealth.length);
                        lastIntervalHealthValues[minutes.get() % minutesForImproveHealth] = lastMinuteAverageHealth;
                        if (minutes.get() == (minutesForImproveHealth - 1)) {
                            double averageHealth = calculatorMean.evaluate(lastIntervalHealthValues, 0, lastIntervalHealthValues.length);
                            for (int i = 0; i < nTypes; i++) {
                                System.out.println("Type " + i + " prob: " + String.format("%.4f", probAttack[i]));
                            }
                            System.out.println("Average health is: " + averageHealth);
                            if (averageHealth < 95) {
                                System.out.println("Average health is less than 95%, start improvement");
                                //print timestamp
                                System.out.println("Start improving at: " + LocalDateTime.now());
                                improveHealth();
                                //set outbreakCounter to 0
                                for (int i = 0; i < nTypes; i++) {
                                    outbreakCounter[i] = 0;
                                }
                                skip.set(true);
                            }
                            minutes.set(0);
                        } else {
                            minutes.getAndIncrement();
                        }
                    }
                    if (!skip.get()) {
                        for (int i = 0; i < nTypes; i++) {
                            int attackedNodes = outbreakCounter[i];
                            int totalNodesPerType = getNumNodePerType(i);
                            double prob = (double) attackedNodes / totalNodesPerType;
                            probAttackPerTypePerSecond[i][seconds.get()] = prob;
                            outbreakCounter[i] = 0;
                        }
                        double actualHealthPercentage = getHealthyPercentageValue();
                        healthyResults.add(actualHealthPercentage);
                        if (actualHealthPercentage >= 95 && improveTime.initialNetworkHealth != -1) {
                            improveTime.endTime = System.currentTimeMillis();
                            long diff = improveTime.endTime - improveTime.startTime;
                            long hh = diff / 3600000;
                            long mmm = (diff % 3600000) / 60000;
                            long ss = (diff % 60000) / 1000;
                            System.out.println("Healthy in: " + hh + " hours, " + mmm + " minutes, " + ss + " seconds");
                            System.out.println("The network was at " + improveTime.initialNetworkHealth + "% healthy");
                            System.out.println("Ended at: " + LocalDateTime.now());
                            improveTime = new ImproveTime(0, 0, -1);
                        }
                        seconds.getAndIncrement();
                    } else {
                        skip.set(false);
                    }
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        t.start();
    }

    private void calculateAvgProbAttackPerTypeLastMinute(double[][] probAttackPerTypePerSecond) {
        for (int i = 0; i < nTypes; i++) {
            double[] probAttackPerType = probAttackPerTypePerSecond[i];
            double probAttackLastMinute = new Mean().evaluate(probAttackPerType, 0, probAttackPerType.length);
            if (probAttackLastMinute > probAttack[i]) {
                probAttack[i] = probAttackLastMinute;
            }
        }
    }

    private int getNumNodePerType(int type) {
        int numNode = 0;
        for (String node : network.keySet()) {
            if (network.get(node).getType() == type) {
                numNode++;
            }
        }
        return numNode;
    }

    public ResponseEntity<String> healedNode(HttpServletRequest url) {
        String node = url.getRemoteAddr();
        network.get(node).setInfected(false);
        return new ResponseEntity<>("200 - OK", HttpStatus.OK);
    }


    public ArrayList<String> getOnlineNodes() {
        return new ArrayList<>(network.keySet());
    }

    public void outbreakDetected(HttpServletRequest request) {
        String node = request.getRemoteAddr();
        int type = network.get(node).getType();
        outbreakCounter[type]++;
    }
}
