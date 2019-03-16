package model;

import java.util.*;

import javafx.util.Pair;
import org.jblas.DoubleMatrix;
import org.jblas.Solve;
import org.jblas.*;
/**
 * why do we need to know revenue densities from a previous step?????? in paper
 * i calculate using gaussian elimination
 *
 * precision of revenue densities
 *
 * normalize reward given at a step?!!!!!!!!!
 *
 * they converge but some of then attack some of them not
 *
 * 3 pool case also works with equally sized pools
 *
 * Good value for revebue per block + normalization
 *
 * simplification with assigning tasks to miners based of their ppow (pPoW + 1)
 *
 * TODO:
 * - convergance based on revenue density not changing anymore
 * - "earn less if noone has attacked" is decided based on revenue densities. see paper for the questions
 *    even coded for per miner income and this does not occur
 * - do i really need graphs?
 */

public class Pool {

    private Simulation sim;
    private final int id;
    private double contributionFees;
    private double revenue;
    private double revenueDensity;
    private double revenueDensityPrevRound;
    private double revenueDensityIfNooneAttack;
    private int ownInfiltrationRate;
    private int[] infiltrationRates;
    private ArrayList<int[]> infeltrationPermutations;
    private double[] revenueFromSabotagers;
    private double incomeWholeGame;
    private double incomeWholeGameNooneattack;
    //members are miners who mine in own pool, sabotagers - who sabotage. 2 disjoint sets
    private ArrayList<Miner> members;
    private ArrayList<AttackingMiner> sabotagers = new ArrayList<>();
    // first double - partial proof of work; second - full proof of work
    private HashMap<Integer, Pair<Double, Double>> PoW = new HashMap<>();

    private final double revenueForBlock = 100;

    public Pool(Simulation sim, int id, double fee, ArrayList<Miner> miners){
        this.sim = sim;
        this.id = id;
        this.revenue = 0;
        this.contributionFees = fee;
        this.ownInfiltrationRate = 0;
        this.members = miners;
        this.incomeWholeGame = 0;
        this.incomeWholeGameNooneattack = 0;

        this.infiltrationRates = new int[sim.getAmountPools()];
        for(int i = 0; i < sim.getAmountPools(); i++){
            infiltrationRates[i] = 0;
        }

        this.revenueFromSabotagers = new double[sim.getAmountPools()];
        for(int i = 0; i < this.revenueFromSabotagers.length; i++){
            revenueFromSabotagers[i] = 0.0;
        }
        for(Miner m: miners){
            PoW.put(m.getId(), new Pair<>(m.getpPoW(), m.getfPoW()));
        }

        this.infeltrationPermutations = new ArrayList<>();
    }

    // SIMPLIFICATION
    static void assignTask(Miner m){
        m.setTask(new Task((int)m.getpPoW() + 1));
    }

    public void assignTasks(){
        for(Miner m: members) {
            if (!m.isWorking()) {
                assignTask(m);
                m.generatePoW();
            }
        }
    }

    public void roundOfWork(){
        for(Miner m: members){
            m.work();
        }
    }

    public void updatePoF(){
        for(Miner m: members){

            if (!m.isWorking()) {
                Pair<Double, Double> p = m.publish();
                PoW.put(m.getId(), p);
                if(p.getValue() > 1.0){
                    collectRevenueFromMiner(m);
                }
            }
        }

        incomeWholeGameNooneattack += this.revenue / (members.size() + sabotagers.size() - ownInfiltrationRate);
    }

    public void changeMiners(){
        System.out.println("??????before??????/");
        System.out.println(this.sabotagers.size());
        System.out.println("???????before?????/");
        int n;
        int[] newRate;
        newRate = calculateBestInfRate();

        for(Pool p: sim.getPools()){
            int poolId = p.getId();
            System.out.println("/_________/");
            System.out.println(newRate[poolId] + " - newrate; " + infiltrationRates[poolId] + " - infrate;");
            System.out.println("/_________/");
            while(newRate[poolId] > infiltrationRates[poolId]){
                infiltrationRates[poolId]++;
                p.increaseOwnInfiltrationRate();
                n = 0;
                while (n < members.size() && (members.get(n) instanceof AttackingMiner)) {
                    n++;
                }
                if (n < members.size()) {
                    Miner m = members.get(n);
                    members.remove(m);
                    PoW.remove(m.getId());

                    AttackingMiner am = new AttackingMiner(this.sim, m.getId(), this.id);
                    am.setAttackedPoolId(poolId);
                    System.out.println("bef add " + this.sabotagers.size());
                    this.sabotagers.add(am);
                    System.out.println("af add " + this.sabotagers.size());

                    ArrayList<Miner> newMembers = p.getMembers();
                    newMembers.add(am);
                    p.setMembers(newMembers);

                    HashMap<Integer, Pair<Double, Double>> updatedMap = p.getPoW();
                    updatedMap.put(am.getId(), new Pair<>(0.0, 0.0));
                    p.setPoW(updatedMap);
                }
            }

            while(newRate[poolId] < infiltrationRates[poolId]){
                infiltrationRates[poolId]--;
                p.decreaseOwnInfiltrationRate();

                ArrayList<AttackingMiner> remove = new ArrayList<>();
                AttackingMiner am = new AttackingMiner(sim, 0, 0);
                
                for(int i = 0; i < sabotagers.size(); i++){
                    if(sabotagers.get(i).getAttackedPoolId() == poolId){
                        am = sabotagers.get(i);
                        break;
                    }
                }

                ArrayList<Miner> newMembers = p.getMembers();
                newMembers.remove(am);
                p.setMembers(newMembers);

                HashMap<Integer, Pair<Double, Double>> updatedMap = p.getPoW();
                updatedMap.remove(am.getId());
                p.setPoW(updatedMap);

                HonestMiner hm = new HonestMiner(this.sim, am.getId(), this.id);
                this.members.add(hm);

                System.out.println("bef " + this.sabotagers.size());
                sabotagers.remove(am);
                System.out.println("af " + this.sabotagers.size());
            }
        }
        System.out.println("????????????/");
        System.out.println(this.sabotagers.size());
        System.out.println("????????????/");
    }

    public void updateFees(){

    }

    public double publishRevenue(){
        double perMinerRev = this.revenue/(members.size() + sabotagers.size());
        incomeWholeGame += perMinerRev;
        return perMinerRev;
    }

    public void collectRevenueFromSabotagers(){
        for(AttackingMiner m: this.sabotagers){
            this.revenueFromSabotagers[m.getAttackedPoolId()] = m.getRevenueInAttackedPool();
            this.revenue += m.getRevenueInAttackedPool();
            m.setRevenueInAttackedPool(0);
        }
    }

    public void collectRevenueFromMiner(Miner m){
        this.revenue += revenueForBlock;
    }

    public void sendRevenueToAll(){
        double amountpow = 0;
        for(int i = 0; i < this.sabotagers.size(); i++){
            amountpow += sabotagers.get(i).getpPoW();
        }
        for(int i = 0; i < this.members.size(); i++){
            amountpow += members.get(i).getpPoW();
        }
        double eachRevenue = this.revenue/amountpow;
        for(AttackingMiner m: this.sabotagers){
            m.setRevenueInOwnPool(eachRevenue * m.getpPoW());
        }

        for(Miner m: this.members){
            if(m instanceof HonestMiner){
                m.setRevenueInOwnPool(eachRevenue * m.getpPoW());
            }

            if(m instanceof AttackingMiner){
                ((AttackingMiner) m).setRevenueInAttackedPool(eachRevenue * m.getpPoW());
            }
        }

        this.revenue = 0;
    }

    public double calculateExpectedRevenueDensityGeneral(int[] rates){
        int newInfRate = 0;
        double directRevenue;
        double[][] coefs = new double[sim.getAmountPools()][sim.getAmountPools()];
        double[][] constants = new double[sim.getAmountPools()][1];

        for(int i = 0; i < rates.length; i++){
            newInfRate += rates[i];
        }

        int simAttackingPower = newInfRate;

        for(Pool p: sim.getPools()){
            simAttackingPower += p.getOwnInfiltrationRate() - infiltrationRates[p.getId()];
        }

        int loyalMiners = members.size() - ownInfiltrationRate + sabotagers.size();

        directRevenue = (double)(loyalMiners - newInfRate) /
                (sim.getAmountMiners() - simAttackingPower);

        constants[id][0] = directRevenue / (loyalMiners + ownInfiltrationRate);
        coefs[id][id] = 1;

        for(int i = 0; i < sim.getAmountPools(); i++){
            if(i != id){
                coefs[id][i] = -1 * (double)rates[i]/(loyalMiners + ownInfiltrationRate);
            }
        }

        // calculate coefs for all other pools except the one that changes miners
        for(Pool p: sim.getPools()){
            int poolId = p.getId();
            int infRate = 0;

            if(poolId != this.id) {
                for (int i = 0; i < rates.length; i++) {
                    infRate += p.getInfiltrationRates()[i];
                }

                int ownNewInfRate = p.getOwnInfiltrationRate() - infiltrationRates[poolId] + rates[poolId];

                loyalMiners = p.getMembers().size() - p.getOwnInfiltrationRate() + p.getSabotagers().size();

                directRevenue = (double)(loyalMiners - infRate) /
                        (sim.getAmountMiners() - simAttackingPower);

                constants[poolId][0] = directRevenue / (loyalMiners + ownNewInfRate);
                coefs[poolId][poolId] = 1;

                for (int i = 0; i < sim.getAmountPools(); i++) {
                    if (i != poolId) {
                        coefs[poolId][i] = -1 * (double)p.getInfiltrationRates()[i] / (loyalMiners + ownNewInfRate);
                    }
                }
            }
        }

        DoubleMatrix coef_matrix = new DoubleMatrix(coefs);
        DoubleMatrix const_matrix = new DoubleMatrix(constants);
        DoubleMatrix res = Solve.solve(coef_matrix, const_matrix);
        //System.out.println(res.get(id));
        /*
            DoubleMatrix res_try = (Solve.pinv(coef_matrix)).mmul(const_matrix);
            System.out.println(res_try.get(id));
            System.out.println(res_try.get(id));
            double[] ress = RevenueCalculator.calculateRevenues(coefs, constants);
            System.out.println(ress[id]);
        */
        return res.get(id);
    }

/**
    // Specific to 2-pool case!
    // public double calculateRevenueDensity()
    public double calculateExpectedRevenueDensity(int[] rates){
        Pool p;
        double newRevDen = 0;
        double directRevenue, directRevenueOpponent;

        if(this.id == 0){
            p = sim.getPools().get(1);
        } else{
            p = sim.getPools().get(0);
        }

        int loyalMiners = members.size() - ownInfiltrationRate + sabotagers.size();
        int loyalMinersApponet = p.getMembers().size() - p.getOwnInfiltrationRate() + p.getSabotagers().size();

        directRevenue = (loyalMiners - rates[p.getId()]) /
                (sim.getAmountMiners() - (double)ownInfiltrationRate - rates[p.getId()]);

        directRevenueOpponent = (loyalMinersApponet - ownInfiltrationRate) /
                (sim.getAmountMiners() - (double)ownInfiltrationRate - rates[p.getId()]);

        newRevDen = directRevenue * loyalMinersApponet + rates[p.getId()] * (directRevenue + directRevenueOpponent);

        double denominator = loyalMinersApponet * loyalMiners + loyalMiners * rates[p.getId()]
                + loyalMinersApponet * ownInfiltrationRate;

        newRevDen /= denominator;
        return newRevDen;
    }
*/

    
    public double calculateExpectedRevenueDensityHardCoded(int[] rates){
        Pool p;
        double m1, m2, x12, x21, R1, R2;
        double newRevDen = 0;

        if(this.id == 0){
            p = sim.getPools().get(1);
            m1 = members.size() - ownInfiltrationRate + sabotagers.size();
            m2 = p.getMembers().size() - p.getOwnInfiltrationRate() + p.getSabotagers().size();
            x12 = rates[1];
            x21 = p.getInfiltrationRates()[0];
            R1 = (m1 - x12)/(sim.getAmountMiners() - x12 - x21);
            R2 = (m2 - x21)/(sim.getAmountMiners() - x12 - x21);
            newRevDen = (m2 * R1 + x12 * (R1 + R2)) / (m1*m2 + m1*x12 + m2*x21);
        } else{
            p = sim.getPools().get(0);
            m2 = members.size() - ownInfiltrationRate + sabotagers.size();
            m1 = p.getMembers().size() - p.getOwnInfiltrationRate() + p.getSabotagers().size();
            x21 = rates[0];
            x12 = p.getInfiltrationRates()[1];
            R1 = (m1 - x12)/(sim.getAmountMiners() - x12 - x21);
            R2 = (m2 - x21)/(sim.getAmountMiners() - x12 - x21);
            newRevDen = (m1 * R2 + x21 * (R1 + R2)) / (m1*m2 + m1*x12 + m2*x21);
        }

        return newRevDen;
    }

    public int[] calculateBestInfRate(){
        int[] bestRate = infiltrationRates;

        // feasible range from paper
        int top = members.size() - ownInfiltrationRate + sabotagers.size();

        double maxRev = calculateExpectedRevenueDensityGeneral(infiltrationRates);

        generateInfiltrationPermutations(top, 0, new int[sim.getAmountPools()]);

        for(int[] permutation: this.infeltrationPermutations){
           /* System.out.println("--------------S---------------");
            for(int i = 0; i < permutation.length; i++){
                System.out.print(permutation[i]);
                System.out.print(" ");
            }
            System.out.println();
            System.out.println("------------------------------");*/
            double res = calculateExpectedRevenueDensityGeneral(permutation);
            //double res2 = calculateExpectedRevenueDensityHardCoded(permutation);
            //System.out.println(res + " VS " + res2 + " naxrev " + maxRev);
            /** double check = calculateExpectedRevenueDensity(permutation);

            System.out.println(res + " VS " + check);
            */

            if(res > maxRev){
                maxRev = res;
                bestRate = permutation;
            }
        }

        this.infeltrationPermutations.clear();
        this.revenueDensityPrevRound = this.revenueDensity;
        this.revenueDensity = maxRev;
        
        System.out.println("--------------!!!!!!!!!!!!!---------------");
            for(int i = 0; i < bestRate.length; i++){
                System.out.print(bestRate[i]);
                System.out.print(" ");
            }
            System.out.println();
            System.out.println("-------------!!!!!!!!!!!!!!!-----------------");
        return bestRate;
    }

    public void generateInfiltrationPermutations(int possibleAmountMiners, int pools, int[] permutation) {
        if(pools == this.sim.getAmountPools()){
            this.infeltrationPermutations.add(permutation.clone());
        }
        else {
            for (int i = 0; i <= possibleAmountMiners; i++) {
                permutation[pools] = i;
                generateInfiltrationPermutations(possibleAmountMiners - i, pools + 1, permutation);

                if(pools == id){
                    break;
                }
            }
        }
    }

    public ArrayList<Miner> getMembers() {
        return members;
    }

    public void setMembers(ArrayList<Miner> members) {
        this.members = members;
    }

    public int getId() {
        return id;
    }

    public ArrayList<AttackingMiner> getSabotagers() {
        return sabotagers;
    }

    public double getContributionFees() {
        return contributionFees;
    }

    public void setContributionFees(double fee) {
        this.contributionFees = fee;
    }

    public HashMap<Integer, Pair<Double, Double>> getPoW() {
        return PoW;
    }

    public void setPoW(HashMap<Integer, Pair<Double, Double>> poW) {
        PoW = poW;
    }

    public int[] getInfiltrationRates() {
        return infiltrationRates;
    }

    public double getRevenueDensity() {
        return revenueDensity;
    }

    public void setRevenueDensity(double revenueDensity) {
        this.revenueDensity = revenueDensity;
    }

    public void decreaseOwnInfiltrationRate() {
        this.ownInfiltrationRate--;
    }

    public void increaseOwnInfiltrationRate() {
        this.ownInfiltrationRate++;
    }

    public int getOwnInfiltrationRate() {
        return ownInfiltrationRate;
    }

    public double getRevenueDensityIfNooneAttack() {
        return revenueDensityIfNooneAttack;
    }

    public void setRevenueDensityIfNooneAttack(double revenueDensityIfNooneAttack) {
        this.revenueDensityIfNooneAttack = revenueDensityIfNooneAttack;
    }

    public double getRevenueDensityPrevRound() {
        return revenueDensityPrevRound;
    }

    public void setRevenueDensityPrevRound(double revenueDensityPrevRound) {
        this.revenueDensityPrevRound = revenueDensityPrevRound;
    }

    public double getIncomeWholeGame() {
        return incomeWholeGame;
    }

    public double getIncomeWholeGameNooneattack() {
        return incomeWholeGameNooneattack;
    }
}
