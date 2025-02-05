package model;

import java.util.*;
import java.util.Random;

import javafx.util.Pair;

/**
 * Main simulation class. Here, the simulation is initialized and proceed.
 */
public class Simulation extends Observable {

	/**
	 * An integer that keeps track of time.
	 */
	private int time = 0;
	/**
	 * Three separate variable to store amount of pool miners, solo miners and pools in the simulation.
	 */
	private int amountMiners;
	private int amountSoloMiners;
	private int amountPools;
	/**
	 * A boolean to signal that all pools in a simulation has converged to a fixed size.
	 */
	private boolean isConverged;
	/**
	 * ArrayLists with all miners and all pools.
	 */
	private ArrayList<Pool> pools;
	private ArrayList<Miner> miners;
	/**
	 * Array with pool revenues at each step.
	 */
	private double[] poolRevenues;
	/**
	 * Additional convergence variable to check that all miners converge to a particular pool.
	 */
	private int checkConvergence = 0;
	/**
	 * Based on the bound variable miners can be separated quantitetivelly into different pools. 
	 */
	private int bound;
	private int bound2;
	private Random rand = new Random();
	/**
	 * An integer that may be used for amount of steps normalization.
	 */
	private final int s = 1;
	/**
	 * Integer variables that keep track of which pool is it to change infiltration rates
	 * and which miner turn is it to swtch pools.
	 */
	private int currentPoolRoundRobin = 0;
	private int currentMinerRoundRobin = 0;
	/**
	 * Revenue for a mined block.
	 */
	private final double revenueForBlock = 100;

	public Simulation(int amountMiners, int amountPools, int amountSoloM){
		this.amountMiners = amountMiners;
		this.amountPools = amountPools;
		this.amountSoloMiners = amountSoloM;
		this.isConverged = false;
		this.bound = bound;
		this.bound2 = bound2;
		this.poolRevenues = new double[amountPools];
		pools = new ArrayList<>(amountPools);
		miners = new ArrayList<>(amountMiners);
		initialize();
	}

	/**
	 * Initialize simulation.
	 */
	private void initialize(){
		for(int i = 0; i < amountMiners; i++){
			int pool = i % amountPools;

			//Options for dividing miners between pools

			/*int pool = 0;
			int pool = rand.nextInt(amountPools);
			if(i >= bound && i < bound2){
				pool = 1;
			}
			if(i >= bound2){
				pool = 2;
			}*/
			
			/*int pool = 0;
			if(i > bound){
				pool = 1;
			}*/
			HonestMiner m = new HonestMiner(this, i, pool);
			miners.add(m);
		}

		for(int i = 0; i < amountPools; i++){
			this.poolRevenues[i] = 0.0;
			ArrayList<Miner> poolMiners = new ArrayList<>();

			for(int j = 0; j < miners.size(); j++){
				Miner m = miners.get(j);
				if(m instanceof HonestMiner){
					if (((HonestMiner) m).getPoolId() == i){
						poolMiners.add(miners.get(j));
					}
				}
			}

			Pool p = new Pool(this, i, i * 0.01, poolMiners);
			pools.add(p);
		}

		for (Pool p: pools){
			double set = p.calculateExpectedRevenueDensityGeneral(p.getInfiltrationRates());
			p.setRevenueDensity(set);
			p.setRevenueDensityPrevRound(set);
			p.setRevenueDensityIfNooneAttack(set);
		}

		for(int i = 0 ; i < amountSoloMiners; i++){
			SoloMiner m = new SoloMiner(this, amountMiners + i);
			miners.add(m);
		}
	}

	/**
	 * Function that represents 1 time step of a simulation.
	 */
	public void timeStep(){
		time ++;

		for(Miner m: this.miners){
			if(m instanceof SoloMiner){
				((SoloMiner) m).work();
				Pair<Double, Double> pow = ((SoloMiner) m).publish();
				if(pow.getValue() > 1.0){
					((SoloMiner) m).setRevenueInOwnPool(revenueForBlock);
				}
			}
		}

		for(Pool p: this.pools){
			p.assignTasks();
			p.roundOfWork();
		}

		int poolId = 0;
		for(Pool p: this.pools){
			p.updatePoF();
			p.collectRevenueFromSabotagers();

			this.poolRevenues[poolId] = p.publishRevenue();
			poolId++;
			p.sendRevenueToAll();
		}

		for(Miner m: miners){
			m.calculateOwnRevDen();
		}

		// Once in a while (determined by s), one pool can change its inf rates and one miner can switch pool.
		if(time % s == 0){
			miners.get(currentMinerRoundRobin).changePool(currentMinerRoundRobin);
			currentMinerRoundRobin++;

			if(currentMinerRoundRobin == miners.size()){
				currentMinerRoundRobin = 0;
			}

			for(Pool p: pools){
				checkPool(p);
			}

			if(currentPoolRoundRobin >= pools.size()){
				currentPoolRoundRobin = 0;
			}
			pools.get(currentPoolRoundRobin).changeMiners();
			currentPoolRoundRobin++;

			isConverged = true;
			checkConvergence();

			if(isConverged){
				checkConvergence ++;
			} else {
				checkConvergence = 0;
			}
		}

		// Simulation has converged.
		if(isConverged && checkConvergence >= (amountMiners + amountSoloMiners)){
			for(Pool p: pools){
				//For debug purposes
				System.out.println("id " + p.getId() + " " + (p.getMembers().size() + p.getSabotagers().size() - p.getOwnInfiltrationRate()));
			}
		} else {
			isConverged = false;
		}

		setChanged();
		notifyObservers();
	}

	/**
	 * Checks whether pools has any loyal members.
	 * If it does not, empty the pool and return all sabotagers to their own pools to mine honestly.
	 * 
	 * @param p pool that is being checked
	 */
	public void checkPool(Pool p){
		if((p.getMembers().size() - p.getOwnInfiltrationRate() + p.getSabotagers().size()) == 0){
			for(Miner m: p.getMembers()){
				HonestMiner nm = new HonestMiner(this, m.getId(), ((AttackingMiner)m).getPoolId());
				pools.get(((AttackingMiner)m).getPoolId()).getMembers().add(nm);
				pools.get(((AttackingMiner)m).getPoolId()).getSabotagers().remove(m);
				miners.add(nm);
				miners.remove(m);
			}
			for(Pool pool: pools){
				if(!pool.equals(p)){
					int [] infr = pool.getInfiltrationRates();
					infr[p.getId()] = 0;
					pool.setInfiltrationRates(infr);
				}
			}
			p.setOwnInfiltrationRate(0);
			p.setMembers(new ArrayList<Miner>());
			p.setSabotagers(new ArrayList<AttackingMiner>());
		}
	}

	/**
	 * Check convergence of a simulation by checking whether any miner or any pool 
	 * has changed its revenue densities from the previous round.
	 */
	public void checkConvergence(){
		for (Miner m: miners){
			if(m.getOwnRevDen() != m.getOwnRevDenPrevRound() && !Double.isNaN(m.getOwnRevDen())){
				isConverged = false;
			}
		}
		for (Pool p: pools){
			if(p.getRevenueDensity() != p.getRevenueDensityPrevRound() && !Double.isNaN(p.getRevenueDensity())){
				isConverged = false;
			}
		}
	}

	/**
	 * Calculates the mining power of a simulation.
	 * 
	 * @return amount of mining miners in a simulation.
	 */
	public int getMiningPower(){
		int miningPower = 0;
		for(Miner m: miners){
			if(!(m instanceof AttackingMiner)){
				miningPower ++;
			}
		}
		return miningPower;
	}

	public double[] getPoolRevenues() {
		return poolRevenues;
	}

	public int getAmountMiners() {
		return amountMiners;
	}

	public ArrayList<Miner> getMiners() {
		return miners;
	}

	public void setMiners(ArrayList<Miner> a) {
		this.miners = a;
	}

	public int getAmountPools() {
		return amountPools;
	}

	public ArrayList<Pool> getPools() {
		return pools;
	}

	public int getTime() {
		return time;
	}

	public boolean isConverged() {
		return isConverged;
	}

	public double getRevenueForBlock(){
		return revenueForBlock;
	}

	public int getAmountSoloMiners() {
		return amountSoloMiners;
	}

	public void setAmountSoloMiners(int asm) {
		this.amountSoloMiners = asm;
	}

}

