package com.example.managing.simple.simulation;

import com.example.managing.simple.data.Network;
import com.example.managing.simple.plot.Plotter;

import java.io.IOException;

public class Simulator {

	private boolean plotting = false;
	private Plotter plot;

	public Simulator(boolean plotting) {
		this.plotting = plotting;
		if (plotting) {
			plot = new Plotter("Network simulation", "Proportion of healthy nodes");
		}
	}

	public double[] simulate(Network system, int timeSteps) {

		//set infected to false
		system.resetInfected();
		double[] result = new double[timeSteps];

		for (int t = 0; t < timeSteps; t++) {
			result[t] = simulateStep(system);
			if (plotting) {
				plot.addValueToDataset(result, 0, t);
//				try {
//					Thread.sleep(50);
//				} catch (InterruptedException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
				if (t % 100 == 0) {
					System.out.println("Timestep: " + t);
				}
			}

		}
		return result;
	}

	/**
	 * @param system the network to simulate
	 * @param attack the known attacks
	 * @return the percentage of infected nodes after the step
	 */
	private double simulateStep(Network system) {
		// First propagate infected
		propagateInfection(system);
		// Create new outbreak infections
		createOutbreaks(system);
		// Self-heal
		healNodes(system);
		// Count infected and create proportion
		return calculateProportionHealthy(system);
	}

	private double calculateProportionHealthy(Network system) {
		return system.calculateProportionHealthy();
	}

	private void healNodes(Network system) {
		system.healNodes();
	}

	private void createOutbreaks(Network system) {
		system.createOutbreaks();
	}

	private void propagateInfection(Network system) {
		system.propagateInfections();

	}

	public void saveFile(String name) throws IOException {
		plot.save(name);

	}

}
