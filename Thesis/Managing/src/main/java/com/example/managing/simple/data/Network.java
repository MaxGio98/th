package com.example.managing.simple.data;

import com.example.managing.model.NodeStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

/**
 * 
 */
public class Network {

	//An array for the types and an array for the nodes
	private ArrayList<Integer>[] neighbors;
	private int[] type;
	private boolean[] isInfected;
	private double meanHealingT; // all nodes have the same healtime TODO: make it node type specific, or node
	// specific?

	private VulnerabilityExploit[] exploits;

	private Random r = new Random();

	protected static final Logger log = LogManager.getLogger(Network.class);

//Creates a single vulnerability for each type of sofware
	/**
	 * @param nnodes        number of nodes
	 * @param nsoftware     number of types of software
	 * @param avgProbAttack average probability of attack at a given time
	 * @param meanHealT     average healing time
	 * @param filename
	 */
	@SuppressWarnings("unchecked")
	public void createSystem(int nnodes, int nsoftware, ArrayList<Integer>[] network, double[] avgProbAttack, int[] nodesType, boolean[] infectionStatus ,double meanHealT,
							 String filename) {

		exploits = new VulnerabilityExploit[nsoftware];
		type = new int[nnodes];
		neighbors = new ArrayList[nnodes];
		isInfected = new boolean[nnodes];
		meanHealingT = meanHealT;
		// fill exploits
		fillExploits(nsoftware, avgProbAttack);
		//fillType();
		type=nodesType;
		//fillNeighbor(network);
		neighbors=network;
		//fillInfected(nnodes);
		isInfected=infectionStatus;
		infectedAtCurrentTime = new boolean[isInfected.length];
	}

	/*private void fillInfected(int nnodes) {
		for (int i = 0; i < nnodes; i++) {
			isInfected[i] = false;
		}
	}*/

	/*private void fillNeighbor(HashMap<String, NodeStatus> network)
	{
		for(int i=0; i<network.size(); i++)
		{
			neighbors[i] = new ArrayList<>();
		}
		ArrayList<String> keys = new ArrayList<>(network.keySet());
		for(String node : network.keySet())
		{
			ArrayList<String> neighborsList = network.get(node).getNeighbors();
			for(String neighbor : neighborsList)
			{
				int neighIndex = keys.indexOf(neighbor);
				neighbors[keys.indexOf(node)].add(neighIndex);
			}
		}
	}*/


	/*private void fillNeighbor(int nnodes, double avgNeighbors) {
		for (int i = 0; i < nnodes; i++) {
			neighbors[i] = new ArrayList<Integer>();
		}

		// Since each neighbor counts double, the avgNeignobrs is divided by 2.
		double neigForNode = avgNeighbors / 2.0;
		double probabilityAnotherNeigh = (neigForNode - 1.0) / neigForNode;
		for (int i = 0; i < nnodes; i++) {

			// Create the first (to a previous node) and then create the rest to any that is
			// not our neighbor.
			// If node is 0, we do not create the first to a previous

			int neigh;
			if (!allPreviousAreNeighbors(i)) {
				neigh = r.nextInt(i); // Creates a random between 0 (incl) and i (excl)
				int count = -1; // TODO: Check if this is 0 or -1
				int indexSearchNodes = -1;

				// find the neighbor. Not the "neigh" because it may be already a neighbor
				while (count < neigh) {
					indexSearchNodes = (indexSearchNodes + 1) % i;
					if (!isAlreadyNeighbor(indexSearchNodes, i)) {
						count++;
					}

				}
				log.debug("Creating neighbor point 1 - Between nodes {}-{}", i , indexSearchNodes);
				//System.out.println("Creating neighbor point 1 - Between nodes" + i + "-" + indexSearchNodes);
				addNeighbor(indexSearchNodes, i);
			} else {// create the first neighbor between i and nnodes
				if (i < nnodes - 1) {
					neigh = r.nextInt(nnodes - 1 - i); // TODO: this may be called with a 0 if we are in the last node
					log.debug("Creating neighbor point 2  - Between nodes {}-{}", i , (i + 1 + neigh));
					addNeighbor(i + 1 + neigh, i);
				}
			}

			// First created
			while (r.nextDouble() < probabilityAnotherNeigh) {
				neigh = r.nextInt(nnodes - 1);
				addNeighbor(searchIthNodeNotNeighborYet(neigh, i, nnodes), i);

			}
			log.trace("Neigbors created for node {}", i);
		}

	}*/

	/*private int searchIthNodeNotNeighborYet(int ithNotNeighbor, int nodeIndex, int nnodes) {
		int count = -1; // TODO: Check if this is 0 or -1
		int loopsWithoutFindingCandidate=0;
		boolean allAreNeighbors=false;
		int indexSearched = -1;
		while (count < ithNotNeighbor && (!allAreNeighbors)) {
			indexSearched = (indexSearched + 1) % nnodes;
			if (!isAlreadyNeighbor(indexSearched, nodeIndex)) {
				count++;
				loopsWithoutFindingCandidate=0;
			}
			else {
				loopsWithoutFindingCandidate++;
				if(loopsWithoutFindingCandidate==nnodes) {
					allAreNeighbors=true;
					log.warn("All are neigbors of node {}", nodeIndex);
				}
			}

		}
		log.debug("Creating neighbor point  3 - Between nodes {}-{}", nodeIndex, indexSearched);
		return indexSearched;
	}

	private boolean allPreviousAreNeighbors(int i) {
		for (int j = 0; j < i; j++) {
			if (!isAlreadyNeighbor(j, i)) {
				return false;
			}
		}
		return true;
	}*/

	/*private boolean isAlreadyNeighbor(int node, int neigh) {
		if (node == neigh) {
			return true;
		}
		return neighbors[node].contains(neigh);
	}*/

	public void addNeighbor(int j, int i) {
		neighbors[j].add(i);
		neighbors[i].add(j);

	}

	public void removeNeighbor(int j, int i) {
		neighbors[j].remove(neighbors[j].indexOf(i));
		neighbors[i].remove(neighbors[i].indexOf(j));

	}

	/*private void fillType() {
		/*for (int i = 0; i < nnodes; i++) {
			type[i] = (int) Math.floor(Math.random() * differents);

		}
		//from network get all types and save them in the variable type
		int i=0;
		for(String key : network.keySet()) {
			type[i]=network.get(key).getType();
			i++;
		}

	}*/

	private void fillExploits(int nsoftware, double[] probAttack) {
		for (int i = 0; i < nsoftware; i++) {
			exploits[i] = new VulnerabilityExploit(i, probAttack[i], i);
		}
	}

	// To speedup simulations, we reuse the same array;
	private boolean[] infectedAtCurrentTime;

	/**
	 * This method propagates the infection to the neighbors of the same type;
	 */
	public void propagateInfections() {
		if (infectedAtCurrentTime == null) {
			infectedAtCurrentTime = new boolean[isInfected.length];
		}
		for (int n = 0; n < neighbors.length; n++) {
			if (isInfected[n]) {
				// Add to the infected at current time
				infectedAtCurrentTime[n] = true;
				// Propagate to neighbors of same type
				for (Integer neigh : neighbors[n]) {
					if (type[n] == type[neigh]) {
						infectedAtCurrentTime[neigh] = true;
					}
				}
			}
		}
		swapArraysAndSetToFalse();
	}

	/**
	 * This method creates a new outbreak in each node according to the probability
	 * of attack;
	 */
	public void createOutbreaks() {
		for (int n = 0; n < isInfected.length; n++) {
			// If the node is not already infected
			if (!isInfected[n]) {
				if (r.nextDouble() < exploits[type[n]].getProbAttack()) {
					isInfected[n] = true;
				}
			}
		}
	}

	/**
	 * This method heals infected node according to the probability of healing;
	 */
	public void healNodes() {
		double healingProbability = (meanHealingT - 1.0) / meanHealingT;
		for (int n = 0; n < isInfected.length; n++) {
			// If the node is already infected
			if (isInfected[n]) {
				if (r.nextDouble() > healingProbability) {
					// Here it is higher than because the larger the probability calculated
					// means that the larger the average time to realize that a node should
					// heal and, therefore, the lower the chances that we heal it at the
					// current moment
					isInfected[n] = false;
				}
			}
		}
	}

	private void swapArraysAndSetToFalse() {
		boolean[] aux = isInfected;
		isInfected = infectedAtCurrentTime;
		infectedAtCurrentTime = aux;
		for (int i = 0; i < infectedAtCurrentTime.length; i++) {
			infectedAtCurrentTime[i] = false;
		}

	}

	@Override
	public String toString() {
		String result = "";

		for (int i = 0; i < neighbors.length; i++) {
			result += "Node " + i + "- Neighbors: ";
			for (Integer n : neighbors[i]) {
				result += n + ", ";
			}
			result += System.getProperty("line.separator");

		}
		return result;
	}

	public double calculateProportionInfected() {
		double numInfected = 0.0;
		for (int i = 0; i < isInfected.length; i++) {
			if (isInfected[i]) {
				numInfected++;
			}
		}
		return numInfected / ((double) isInfected.length);
	}

	public double calculateProportionHealthy() {
		return 1.0 - calculateProportionInfected();
	}

	public void resetInfected() {
		for (int i = 0; i < isInfected.length; i++) {
			isInfected[i] = false;
		}

	}

	public int getNodeWithMostNeighbors() {
		int maxNeighbors = 0;
		int maxNeighborsIndex = 0;
		for (int i = 0; i < neighbors.length; i++) {
			if (numberOfNeighbors(i) > maxNeighbors) {
				maxNeighbors = numberOfNeighbors(i);
				maxNeighborsIndex = i;
			}
		}
		return maxNeighborsIndex;
	}


	public int numberOfNeighbors(int node) {
		return neighbors[node].size();
	}

	public ArrayList<Integer> getNeighbors(int node) {
		return neighbors[node];
	}

	public int typeOf(int node) {
		return type[node];
	}

	public boolean isConnected() {

		boolean[] visited = new boolean[neighbors.length];

		// depth-first-search marking visited nodes
		dfs(0, visited);


		for (int i = 0; i < visited.length; i++) {
			if (!visited[i]) {
				return false;
			}
		}

		return true;
	}

	public void dfs(int origin, boolean[] visited) {

		visited[origin] = true;

		for (int i = 0; i < neighbors[origin].size(); i++) {

			int neighbor = neighbors[origin].get(i);

			if (!visited[neighbor]) {
				dfs(neighbor, visited);
			}
		}
	}

	public int[] getNodesSortedByNeighborNumber() {
		int[] nodes = new int[neighbors.length];
		for(int i=0; i< nodes.length ; i++) {
			nodes[i]=i;
		}
		sortByNeighbors(nodes);
		return nodes;
	}

	private void sortByNeighbors(int[] nodes) {
		//Simple quadratic sorting
		for(int i=0; i<nodes.length; i++) {
			int auxPosition = getMaxPosition(nodes, i); //get the position with maximum value starting in j position
			int auxValue = nodes[auxPosition];
			nodes[auxPosition]=nodes[i];
			nodes[i]=auxValue;


		}

	}

	private int getMaxPosition(int[] nodes, int i) {
		int maxValue=0;
		int maxPosition=0;
		for(int j=i; j<nodes.length; j++) {
			if(numberOfNeighbors(nodes[j])>maxValue) {
				maxValue=numberOfNeighbors(nodes[j]);
				maxPosition=j;
			}
		}
		return maxPosition;
	}

	public int countAllNeighbors() {
		int sum=0;
		for(int i=0; i<neighbors.length; i++) {
			sum+=neighbors[i].size();
		}
		return sum/2;
	}

	public int getNumberOfNodes() {
		return neighbors.length;
	}

	public ArrayList<Integer>[] getNeighbors() {
		return neighbors;
	}

}
