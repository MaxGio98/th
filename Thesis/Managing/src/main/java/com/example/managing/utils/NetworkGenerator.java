package com.example.managing.utils;
import java.util.*;

public class NetworkGenerator {
    private int numNodes;
    private int avgNeighborsPerNode;
    private HashMap<Integer, ArrayList<Integer>> neighbors;

    public NetworkGenerator(int numNodes, int avgNeighborsPerNode) {
        this.numNodes = numNodes;
        this.avgNeighborsPerNode = avgNeighborsPerNode;
        this.neighbors = new HashMap<>();
    }

    private void setNodeNeighbors() {
        double probNeighbor = (double) avgNeighborsPerNode / numNodes;
        for (int i = 0; i < numNodes; i++) {
            neighbors.put(i, new ArrayList<>());
        }
        for (int i = 0; i < numNodes; i++) {
            for (int j = 0; j < numNodes; j++) {
                if (i != j && Math.random() < probNeighbor) {
                    if (!neighbors.get(i).contains(j)) {
                        neighbors.get(i).add(j);
                        neighbors.get(j).add(i);
                    }
                }
            }
        }
        fixIsolatedComponents();
    }

    // Function to perform Breadth First Search on a graph
    // represented using adjacency list
    private void fixIsolatedComponents() {
        // All nodes are not visited initially
        boolean[] visited = new boolean[numNodes];
        // Choose a random node as starting point
        int startNode = (int) (Math.random() * numNodes);
        // Mark this node as visited
        visited[startNode] = true;
        // Create a queue for BFS
        Queue<Integer> queue = new LinkedList<>();
        // Enqueue the starting node
        queue.add(startNode);
        // Perform BFS
        while (!queue.isEmpty()) {
            // Dequeue a node from queue
            int currentNode = queue.poll();
            /* Get all adjacent vertices of the dequeued
             vertex currentNode If an adjacent has not
             been visited, then mark it visited and
             enqueue it*/
            for (int neighbor : neighbors.get(currentNode)) {
                if (!visited[neighbor]) {
                    visited[neighbor] = true;
                    queue.add(neighbor);
                }
            }
            if (queue.isEmpty()) {
                // check that all nodes are visited
                for (int i = 0; i < numNodes; i++) {
                    // if a node is not visited
                    if (!visited[i]) {
                        // add link random visited node
                        int randomVisitedNode = (int) (Math.random() * numNodes);
                        while (randomVisitedNode == i || !visited[randomVisitedNode]) {
                            randomVisitedNode = (int) (Math.random() * numNodes);
                        }
                        neighbors.get(i).add(randomVisitedNode);
                        neighbors.get(randomVisitedNode).add(i);
                        queue.add(i);
                        visited[i] = true;
                        /*no need to check other nodes,
                        the while loop will continue until all nodes are visited
                         */
                        break;
                    }
                    // at this point all nodes have at least one neighbor
                }
            }
        }
        for (int i = 0; i < numNodes; i++) {
            //if a node has less than 2 neighbors, add a random node
            if (neighbors.get(i).size() < 2) {
                int randomNode = (int) (Math.random() * numNodes);
                while (randomNode == i || neighbors.get(i).contains(randomNode)) {
                    randomNode = (int) (Math.random() * numNodes);
                }
                neighbors.get(i).add(randomNode);
                neighbors.get(randomNode).add(i);
            }
        }
    }

    public HashMap<Integer, ArrayList<Integer>> getNeighbors() {
        setNodeNeighbors();
        return neighbors;
    }
}