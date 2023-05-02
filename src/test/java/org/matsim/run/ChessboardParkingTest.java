package org.matsim.run;


import com.google.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.*;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.algorithms.PlanAlgorithm;
import org.matsim.core.replanning.PlanStrategy;
import org.matsim.core.replanning.PlanStrategyImpl;
import org.matsim.core.replanning.modules.AbstractMultithreadedModule;
import org.matsim.core.replanning.selectors.RandomPlanSelector;
import org.matsim.core.router.*;
import org.matsim.core.scenario.MutableScenario;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.timing.TimeInterpretation;
import org.matsim.examples.ExamplesUtils;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.run.prepare.LeipzigUtils;
import org.matsim.testcases.MatsimTestUtils;
import playground.vsp.openberlinscenario.cemdap.output.ActivityTypes;

import javax.inject.Provider;
import java.net.URL;
import java.util.*;

import static org.matsim.core.config.groups.PlanCalcScoreConfigGroup.*;

/**
 * abc
 */
public class ChessboardParkingTest {
	private static final Logger log = LogManager.getLogger(ChessboardParkingTest.class);
	@Rule
	public MatsimTestUtils utils = new MatsimTestUtils();
	private static final String HOME_ZONE_ID = "homeLinkId";
	final String RE_ROUTE_LEIPZIG = "ReRouteLeipzig";

	enum Situation {
		residentInResidentialArea, residentOutsideResidentialArea, nonResidentInResidentialAreaNoShop,
		nonResidentInResidentialAreaShop, nonResidentOutsideResidentialArea, restrictedToNormal, normalToNormal, fromShpFile
	}

	// yyyyyy Bitte auch Tests, wo diese Unterscheidungen am Ziel stattfinden.  Danke!  kai, apr'23

	@Test
	public final void runChessboardParkingTest1() {

		URL url = IOUtils.extendUrl(ExamplesUtils.getTestScenarioURL("chessboard"), "config.xml");

		for (Situation situation : Situation.values()) {
			Config config = ConfigUtils.loadConfig(url);
			config.controler().setLastIteration(1);
			config.controler().setOutputDirectory(utils.getOutputDirectory());
			config.global().setNumberOfThreads(0);
			config.qsim().setNumberOfThreads(1);
			config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
			config.plansCalcRoute().setAccessEgressType(PlansCalcRouteConfigGroup.AccessEgressType.accessEgressModeToLink);

			config.strategy().clearStrategySettings();
			{
				StrategyConfigGroup.StrategySettings stratSets = new StrategyConfigGroup.StrategySettings();
				stratSets.setWeight(1.);
				stratSets.setStrategyName(RE_ROUTE_LEIPZIG);
				config.strategy().addStrategySettings(stratSets);
			}

			config.facilities().setFacilitiesSource(FacilitiesConfigGroup.FacilitiesSource.onePerActivityLinkInPlansFile);

			config.planCalcScore().addActivityParams(new ActivityParams(TripStructureUtils.createStageActivityType("parking")).setScoringThisActivityAtAll(false));

			config.vspExperimental().setVspDefaultsCheckingLevel(VspExperimentalConfigGroup.VspDefaultsCheckingLevel.warn);

			MutableScenario scenario = (MutableScenario) ScenarioUtils.loadScenario(config);

			Population population = PopulationUtils.createPopulation(config);
			createExampleParkingPopulation(population, scenario.getNetwork(), situation);
			scenario.setPopulation(population);
			log.warn("population size=" + scenario.getPopulation().getPersons().size() +" for case" + situation);

//		System.exit(-1);

//		NetworkOptions networkOptions = new NetworkOptions();
//		// help // yy what does the "help" mean here?
//		networkOptions.prepare(scenario.getNetwork());
			// yy I don't know what the above is supposed to do.  Thus commented it out.  kai, apr'23

			Controler controler = new Controler(scenario);
			controler.addOverridingModule(new AbstractModule() {
				@Override
				public void install() {
//				this.bind( MultimodalLinkChooser.class ).toInstance( new LeipzigMultimodalLinkChooser() );
					this.addPlanStrategyBinding(RE_ROUTE_LEIPZIG).toProvider(LeipzigRoutingStrategyProvider.class);
					// yyyy this only uses it during replanning!!!  kai, apr'23
				}
			});

			TestParkingListener handler = new TestParkingListener();
			controler.addOverridingModule(new AbstractModule() {
				@Override
				public void install() {
					addEventHandlerBinding().toInstance(handler);
				}
			});
			controler.run();

			getAssertions(situation, handler);
		}


	}

	static final class LeipzigRoutingStrategyProvider implements Provider<PlanStrategy> {
		// is a provider in matsim core.  maybe try without.  kai, apr'23
		@Inject
		private GlobalConfigGroup globalConfigGroup;
		@Inject
		private ActivityFacilities facilities;
		@Inject
		private Provider<TripRouter> tripRouterProvider;
		@Inject
		private SingleModeNetworksCache singleModeNetworksCache;
		@Inject
		private Scenario scenario;
		@Inject
		private TimeInterpretation timeInterpretation;
		@Inject
		MultimodalLinkChooser linkChooser;

		@Override
		public PlanStrategy get() {
			PlanStrategyImpl.Builder builder = new PlanStrategyImpl.Builder(new RandomPlanSelector<>());
			builder.addStrategyModule(new AbstractMultithreadedModule(globalConfigGroup) {
				@Override
				public final PlanAlgorithm getPlanAlgoInstance() {
					return new LeipzigRouterPlanAlgorithm(tripRouterProvider.get(), facilities, timeInterpretation, singleModeNetworksCache, scenario, linkChooser);
				}
			});
			return builder.build();
		}
	}


//	static final class LeipzigMultimodalLinkChooser implements MultimodalLinkChooser {
//
//		private final MultimodalLinkChooser delegate;
//		@Inject LeipzigMultimodalLinkChooser() {
//			this.delegate = RouterUtils.getMultimodalLinkChooserDefault();
//		}
//
//		@Override public Link decideOnLink( Facility facility, Network network, RoutingRequest routingRequest ){
//			Link result = null ;
//
////			Person person = routingRequest.getPerson();
////
////			String homeZoneId = (String) person.getAttributes().getAttribute( HOME_ZONE_ID );
////
////			if ( homeLinkId.equals( facility.getLinkId().toString() ) ) {
////
////			 fall back on default:
////			if ( result == null ){
////				result = this.delegate.decideOnLink( facility, network, routingRequest );
////			}
//			return result;
//		}
//	}

	static void createExampleParkingPopulation(Population population, Network network, Situation situation) {

		PopulationFactory factory = population.getFactory();
		Leg carLeg = factory.createLeg(TransportMode.car);
		Link originLink = network.getLinks().get(Id.createLinkId("80"));
		Link destinationLink = network.getLinks().get(Id.createLinkId("81"));

		Person person = factory.createPerson(Id.createPersonId(situation.toString()));
		Plan plan = factory.createPlan();
		final Activity originActivity = factory.createActivityFromLinkId(ActivityTypes.LEISURE, originLink.getId());
		originActivity.setEndTime(3600.);
		plan.addActivity(originActivity);
		plan.addLeg(factory.createLeg(TransportMode.car));
		plan.addActivity(factory.createActivityFromLinkId(ActivityTypes.LEISURE, destinationLink.getId()));
		person.addPlan(plan);
		population.addPerson(person);

		switch (situation) {
			case normalToNormal -> {
				// do nothing
			}
			case restrictedToNormal -> {
				LeipzigUtils.setParkingToRestricted(originLink);
			}

			// yy Ich habe den Setup der Fälle, die hier kommen, leider nicht verstanden.  Daher habe ich obigen Fall "restrictedToNormal" neu gebaut.  Sorry ...  kai, apr'23

			case fromShpFile -> {
//				for( Link link : network.getLinks().values() ){
//					if ( link is in polygon ) {
//						LeipzigUtils.parkingIsRestricted( link );
//					}
//				}
			}
			case residentInResidentialArea -> {
				population.getPersons().clear();
				Person residentInResidentialArea = factory.createPerson(Id.createPersonId(situation.toString()));
				LeipzigUtils.setParkingToNonRestricted(residentInResidentialArea);
				LeipzigUtils.parkingIsRestricted(originLink);
				residentInResidentialArea.addPlan(plan);
				population.addPerson(residentInResidentialArea);
			}
			case residentOutsideResidentialArea -> {
				population.getPersons().clear();
				Person residentOutsideResidentialArea = factory.createPerson(Id.createPersonId(situation.toString()));
				residentOutsideResidentialArea.addPlan(plan);
				population.addPerson(residentOutsideResidentialArea);
			}
			case nonResidentInResidentialAreaNoShop -> {
				population.getPersons().clear();
				Person nonResidentInResidentialAreaNoShop = factory.createPerson(Id.createPersonId(situation.toString()));
				nonResidentInResidentialAreaNoShop.addPlan(plan);
				LeipzigUtils.setParkingToRestricted(nonResidentInResidentialAreaNoShop);
				LeipzigUtils.setParkingToRestricted(destinationLink);
				population.addPerson(nonResidentInResidentialAreaNoShop);
			}
			case nonResidentInResidentialAreaShop -> {
				/*population.getPersons().clear();
				Person nonResidentInResidentialAreaShop = factory.createPerson(Id.createPersonId("nonResidentInResidentialAreaShop"));
				Plan plan4 = factory.createPlan();
				plan4.addActivity(factory.createActivityFromLinkId(ActivityTypes.HOME, Id.createLinkId("40")));
				plan4.addLeg(carLeg);
				plan4.addActivity(factory.createActivityFromLinkId(ActivityTypes.SHOPPING, Id.createLinkId("135")));
				nonResidentInResidentialAreaShop.addPlan(plan4);
				nonResidentInResidentialAreaShop.getAttributes().putAttribute("parkingType", "non-residential");
				population.addPerson(nonResidentInResidentialAreaShop);*/
			}
			case nonResidentOutsideResidentialArea -> {
				population.getPersons().clear();
				Person nonResidentOutsideResidentialArea = factory.createPerson(Id.createPersonId(situation.toString()));
				nonResidentOutsideResidentialArea.addPlan(plan);
				LeipzigUtils.setParkingToRestricted(nonResidentOutsideResidentialArea);
				population.addPerson(nonResidentOutsideResidentialArea);
			}
			default -> throw new IllegalStateException("Unexpected value: " + situation);
		}


		//residential area is maximum including the following edges (square): 124-126, 34-36, 178-180, 88-90

	}

	class TestParkingListener implements ActivityStartEventHandler {

		HashMap<Id<Person>, List<ActivityStartEvent>> parkingActivities = new HashMap<>();

		@Override
		public void handleEvent(ActivityStartEvent activityStartEvent) {
			if (activityStartEvent.getActType().equals("parking interaction")) {
				if (!parkingActivities.containsKey(activityStartEvent.getPersonId())) {
					parkingActivities.put(activityStartEvent.getPersonId(), new ArrayList<>(Arrays.asList(activityStartEvent)));
				} else parkingActivities.get(activityStartEvent).add(activityStartEvent);
			}
		}

		@Override
		public void reset(int iteration) {
			ActivityStartEventHandler.super.reset(iteration);
		}
	}

	private void getAssertions(Situation situation, TestParkingListener handler) {

		switch (situation) {
			case normalToNormal -> {

			}

			case restrictedToNormal -> {
				Assert.assertTrue(handler.parkingActivities.containsKey(Id.createPersonId(String.valueOf(Situation.restrictedToNormal))));
				Assert.assertEquals("wrong number of parking activites!", 1, handler.parkingActivities.get(Id.createPersonId(String.valueOf(Situation.restrictedToNormal))).size());
				Assert.assertEquals("wrong link", Id.createLinkId("169"), handler.parkingActivities.get(Id.createPersonId(String.valueOf(Situation.restrictedToNormal))).get(0).getLinkId());
			}

			case residentInResidentialArea -> {
				Assert.assertTrue(!handler.parkingActivities.containsKey(Id.createPersonId(String.valueOf(Situation.residentInResidentialArea))));
			}

			case residentOutsideResidentialArea -> {
				Assert.assertTrue(!handler.parkingActivities.containsKey(Id.createPersonId(String.valueOf(Situation.residentOutsideResidentialArea))));
			}

			case nonResidentInResidentialAreaNoShop -> {
				Assert.assertTrue(handler.parkingActivities.containsKey(Id.createPersonId(String.valueOf(Situation.nonResidentInResidentialAreaNoShop))));
				Assert.assertEquals("wrong number of parking activites!", 1, handler.parkingActivities.get(Id.createPersonId(String.valueOf(Situation.nonResidentInResidentialAreaNoShop))).size());
				Assert.assertNotEquals("wrong link", Id.createLinkId("81"), handler.parkingActivities.get(Id.createPersonId(String.valueOf(Situation.nonResidentInResidentialAreaNoShop))).get(0).getLinkId());
			}



		}

	}
}
