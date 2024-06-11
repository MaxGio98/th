package com.example.managing;

import com.example.managing.model.NodeStatus;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.*;

@SpringBootApplication
public class ManagingApplication {
    public static HashMap<String, NodeStatus> network;
    public static DockerClient dockerClient;
    public static HostConfig hostConfig;
    public static ArrayList<String> containerIds;
    public static HttpClient client;
    public static ArrayList<Double> healthyResults;
    public static ArrayList<ArrayList<String>> newNodeNeighbors;
    private final String imageName = "node";
    @Value("${managing.numNodes}")
    private int numNodes;

    public static void main(String[] args) {
        network = new HashMap<>();
        healthyResults = new ArrayList<>();
        newNodeNeighbors = new ArrayList<>();
        //SpringApplication.run(ManagingApplication.class, args);
        SpringApplicationBuilder builder = new SpringApplicationBuilder(ManagingApplication.class);
        builder.headless(false);
        ConfigurableApplicationContext context = builder.run(args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        setDockerConfigAndHost(numNodes);
        startInitialNodes();
    }

    private static void setDockerConfigAndHost(int numNodes) {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();
        dockerClient = DockerClientImpl.getInstance(config, httpClient);
        hostConfig = HostConfig.newHostConfig().withAutoRemove(true).withExtraHosts("host.docker.internal:host-gateway");
        client = HttpClient.newHttpClient();
        Runtime runtime = Runtime.getRuntime();
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(numNodes, Runtime.getRuntime().availableProcessors()));
        List<Future<?>> futures = new ArrayList<>();
        runtime.addShutdownHook(new Thread(() -> {
            System.out.println("Closing nodes...");
            List<Exception> exceptions = new CopyOnWriteArrayList<>(); // Thread-safe exception handling
            for (String containerName : containerIds) {
                futures.add(executor.submit(() -> {
                    try {
                        System.out.println("Closing " + containerName);
                        dockerClient.stopContainerCmd(containerName).exec();
                    } catch (Exception e) {
                        exceptions.add(e);
                    }
                }));
            }
            // Wait for all closures to finish
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
            if (!exceptions.isEmpty()) {
                System.err.println("Encountered exceptions during shutdown:");
                for (Exception e : exceptions) {
                    e.printStackTrace(System.err);
                }
            }
            try {
                dockerClient.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            client.close();
        }));
    }

    private void startInitialNodes() {
        containerIds = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(numNodes, Runtime.getRuntime().availableProcessors()));
        List<Future<CreateContainerResponse>> futures = new ArrayList<>();
        System.out.println("Starting " + numNodes + " nodes...");

        long startTime = System.currentTimeMillis();
        for (int i = 0; i < numNodes; i++) {
            int finalI = i; // Capture loop variable for lambda
            futures.add(executor.submit(() -> {
                System.out.println("Creating node" + finalI);
                CreateContainerResponse container = dockerClient.createContainerCmd(imageName)
                        .withHostConfig(hostConfig)
                        .withName("node" + finalI)
                        .withMemory(400000000L)
                        .exec();
                return container;
            }));
        }
        for (Future<CreateContainerResponse> future : futures) {
            CreateContainerResponse container = null;
            try {
                container = future.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
            dockerClient.startContainerCmd(container.getId()).exec();
            containerIds.add(container.getId());
        }
        long endTime = System.currentTimeMillis();
        System.out.println("All nodes launched in " + (endTime - startTime) + " ms");
        executor.shutdown();
    }
}
