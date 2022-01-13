package org.matsim.run.prepare;

import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigReader;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;

public class DeleteRoutesFromPlans {
//    private static final String inputConfig = "C:/Users/Simon/Desktop/leipzig-v1.0-test.with-drt.config.xml";
    private static String outputPop = "C:/Users/Simon/Desktop/testPop.xml.gz";

    private Config config;

    public DeleteRoutesFromPlans(Config config) { this.config = config;}

     public Config deleteRoutesFromPlans(Config config) {

        Population pop = PopulationUtils.readPopulation(config.plans().getInputFile());
        Population newPop = PopulationUtils.createPopulation(config);

        for(Person person  : pop.getPersons().values()) {
            for(Plan plan :person.getPlans()) {
                PopulationUtils.resetRoutes(plan);
            }
            if(!newPop.getPersons().containsKey(person.getId())) {
                newPop.addPerson(person);
            }
        }
        PopulationUtils.writePopulation(newPop, outputPop);
        System.out.println("Pop with deleted routes written to: " + outputPop);
        System.out.println("Putting newly written pop as input pop");
        System.out.println("size of new population: " + newPop.getPersons().size());
        System.out.println("size of old population: " + pop.getPersons().size());
        config.plans().setInputFile(outputPop);
        return config;

    }
}
