package org.matsim.run;


import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.examples.ExamplesUtils;
import org.matsim.run.prepare.NetworkOptions;
import org.matsim.testcases.MatsimTestUtils;
import playground.vsp.openberlinscenario.cemdap.output.ActivityTypes;

import java.io.IOException;

public class ChessboardParkingTest {
	@Rule
	public MatsimTestUtils utils = new MatsimTestUtils();


	@Test
	public final void runChessboardParkingTest() throws IOException {
		String inputPath = String.valueOf(ExamplesUtils.getTestScenarioURL("chessboard"));

		Config config = ConfigUtils.loadConfig(inputPath + "/config.xml");
		config.controler().setLastIteration(1);
		config.controler().setOutputDirectory("output/chessboardParkingTest/");
		config.global().setNumberOfThreads(1);
		config.qsim().setNumberOfThreads(1);

		Scenario scenario = ScenarioUtils.loadScenario(config);
		Population population = PopulationUtils.createPopulation(config);

		createExampleParkingPopulation(population);





		NetworkOptions networkOptions = new NetworkOptions();

		// help
		networkOptions.prepare(scenario.getNetwork());


		Controler controler = new Controler(scenario);



	}

	final void createExampleParkingPopulation(Population population) {

		PopulationFactory factory = population.getFactory();

		Leg carLeg = factory.createLeg(TransportMode.car);

		Person residentInResidentialArea = factory.createPerson(Id.createPersonId("residentInResidentialArea"));

		Plan plan1 = factory.createPlan();
		//maybe we also need to set start / end times for the activities..
		plan1.addActivity(factory.createActivityFromLinkId(ActivityTypes.HOME, Id.createLinkId("80")));
		plan1.addLeg(carLeg);
		plan1.addActivity(factory.createActivityFromLinkId(ActivityTypes.EDUCATION, Id.createLinkId("81")));
		residentInResidentialArea.addPlan(plan1);
		residentInResidentialArea.getAttributes().putAttribute("parkingType", "residential");

		Person residentOutsideResidentialArea = factory.createPerson(Id.createPersonId("residentOutsideResidentialArea"));

		Plan plan2 = factory.createPlan();
		plan2.addActivity(factory.createActivityFromLinkId(ActivityTypes.HOME, Id.createLinkId("35")));
		plan2.addLeg(carLeg);
		plan2.addActivity(factory.createActivityFromLinkId(ActivityTypes.WORK, Id.createLinkId("31")));
		residentOutsideResidentialArea.addPlan(plan2);
		residentOutsideResidentialArea.getAttributes().putAttribute("parkingType", "residential");

		Person nonResidentInResidentialAreaNoShop = factory.createPerson(Id.createPersonId("nonResidentInResidentialAreaNoShop"));

		Plan plan3 = factory.createPlan();
		plan3.addActivity(factory.createActivityFromLinkId(ActivityTypes.HOME, Id.createLinkId("41")));
		plan3.addLeg(carLeg);
		plan3.addActivity(factory.createActivityFromLinkId(ActivityTypes.WORK, Id.createLinkId("45")));
		nonResidentInResidentialAreaNoShop.addPlan(plan3);
		nonResidentInResidentialAreaNoShop.getAttributes().putAttribute("parkingType", "non-residential");

		Person nonResidentInResidentialAreaShop = factory.createPerson(Id.createPersonId("nonResidentInResidentialAreaShop"));

		Plan plan4 = factory.createPlan();
		plan4.addActivity(factory.createActivityFromLinkId(ActivityTypes.HOME, Id.createLinkId("40")));
		plan4.addLeg(carLeg);
		plan4.addActivity(factory.createActivityFromLinkId(ActivityTypes.SHOPPING, Id.createLinkId("135")));
		nonResidentInResidentialAreaShop.addPlan(plan4);
		nonResidentInResidentialAreaNoShop.getAttributes().putAttribute("parkingType", "non-residential");

		Person nonResidentOutsideResidentialArea = factory.createPerson(Id.createPersonId("nonResidentOutsideResidentialArea"));

		Plan plan5 = factory.createPlan();
		plan5.addActivity(factory.createActivityFromLinkId(ActivityTypes.HOME, Id.createLinkId("64")));
		plan5.addLeg(carLeg);
		plan5.addActivity(factory.createActivityFromLinkId(ActivityTypes.LEISURE, Id.createLinkId("66")));
		nonResidentOutsideResidentialArea.addPlan(plan5);
		nonResidentOutsideResidentialArea.getAttributes().putAttribute("parkingType", "non-residential");

		//residential area is maximum including the following edges (square): 124-126, 34-36, 178-180, 88-90

		population.addPerson(residentInResidentialArea);
		population.addPerson(residentOutsideResidentialArea);
		population.addPerson(nonResidentOutsideResidentialArea);
		population.addPerson(nonResidentInResidentialAreaNoShop);
		population.addPerson(nonResidentInResidentialAreaShop);

	}
}
