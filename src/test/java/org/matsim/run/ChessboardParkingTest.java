package org.matsim.run;


import com.google.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.GlobalConfigGroup;
import org.matsim.core.config.groups.StrategyConfigGroup;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
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

/**
 * abc
 */
public class ChessboardParkingTest {
	private static final Logger log = LogManager.getLogger(ChessboardParkingTest.class );
	@Rule public MatsimTestUtils utils = new MatsimTestUtils();
	private static final String HOME_ZONE_ID = "homeLinkId";
	final String RE_ROUTE_LEIPZIG = "ReRouteLeipzig";
	enum Situation{ residentInResidentialArea, residentOutsideResidentialArea, nonResidentInResidentialAreaNoShop,
		nonResidentInResidentialAreaShop, nonResidentOutsideResidentialArea, restrictedToNormal, normalToNormal }

	// yyyyyy Bitte auch Tests, wo diese Unterscheidungen am Ziel stattfinden.  Danke!  kai, apr'23

	@Test public final void runChessboardParkingTest1() {
		URL url = IOUtils.extendUrl( ExamplesUtils.getTestScenarioURL( "chessboard" ), "config.xml" );
		Config config = ConfigUtils.loadConfig( url );
		config.controler().setLastIteration(1);
		config.controler().setOutputDirectory( utils.getOutputDirectory() );
		config.global().setNumberOfThreads(0);
		config.qsim().setNumberOfThreads(1);

		config.strategy().clearStrategySettings();
		{
			StrategyConfigGroup.StrategySettings stratSets = new StrategyConfigGroup.StrategySettings();
			stratSets.setWeight( 1. );
			stratSets.setStrategyName( RE_ROUTE_LEIPZIG );
			config.strategy().addStrategySettings( stratSets );
		}

		config.vspExperimental().setVspDefaultsCheckingLevel( VspExperimentalConfigGroup.VspDefaultsCheckingLevel.warn );

		MutableScenario scenario = (MutableScenario) ScenarioUtils.loadScenario(config );

		Population population = PopulationUtils.createPopulation(config);
		createExampleParkingPopulation(population, scenario.getNetwork(), Situation.restrictedToNormal );
		scenario.setPopulation( population );
		log.warn("population size=" + scenario.getPopulation().getPersons().size() );

//		System.exit(-1);

//		NetworkOptions networkOptions = new NetworkOptions();
//		// help // yy what does the "help" mean here?
//		networkOptions.prepare(scenario.getNetwork());
		// yy I don't know what the above is supposed to do.  Thus commented it out.  kai, apr'23

		Controler controler = new Controler(scenario);

		controler.addOverridingModule( new AbstractModule(){
			@Override public void install(){
//				this.bind( MultimodalLinkChooser.class ).toInstance( new LeipzigMultimodalLinkChooser() );
				this.addPlanStrategyBinding( RE_ROUTE_LEIPZIG ).toProvider( LeipzigRoutingStrategyProvider.class );
				// yyyy this only uses it during replanning!!!  kai, apr'23
			}
		} );

		controler.run();
	}

	static final class LeipzigRoutingStrategyProvider implements Provider<PlanStrategy> {
		// is a provider in matsim core.  maybe try without.  kai, apr'23
		@Inject private GlobalConfigGroup globalConfigGroup;
		@Inject private ActivityFacilities facilities;
		@Inject private Provider<TripRouter> tripRouterProvider;
		@Inject private SingleModeNetworksCache singleModeNetworksCache;
		@Inject private Scenario scenario;
		@Inject private TimeInterpretation timeInterpretation;
		@Inject MultimodalLinkChooser linkChooser;
		@Override public PlanStrategy get() {
			PlanStrategyImpl.Builder builder = new PlanStrategyImpl.Builder( new RandomPlanSelector<>()) ;
			builder.addStrategyModule( new AbstractMultithreadedModule( globalConfigGroup ){
				@Override public final PlanAlgorithm getPlanAlgoInstance() {
					return new LeipzigRouterPlanAlgorithm( tripRouterProvider.get(), facilities, timeInterpretation, singleModeNetworksCache, scenario, linkChooser );
				}
			} );
			return builder.build() ;
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

	static void createExampleParkingPopulation( Population population, Network network, Situation situation ) {

		PopulationFactory factory = population.getFactory();

		Leg carLeg = factory.createLeg(TransportMode.car);

		switch( situation ) {
			case normalToNormal -> {
				Link originLink = network.getLinks().get( Id.createLinkId( "80" ));
				Link destinationLink = network.getLinks().get( Id.createLinkId( "81" ));

//				LeipzigUtils.setParkingToRestricted( originLink );

				Person person = factory.createPerson( Id.createPersonId( situation.toString() ) );

				Plan plan = factory.createPlan();
				final Activity originActivity = factory.createActivityFromLinkId( ActivityTypes.LEISURE, originLink.getId() );
				originActivity.setEndTime( 3600. );
				plan.addActivity( originActivity );
				plan.addLeg( factory.createLeg( TransportMode.car ) );
				plan.addActivity( factory.createActivityFromLinkId( ActivityTypes.LEISURE, destinationLink.getId()) );

				person.addPlan( plan );

				population.addPerson( person );
			}
			case restrictedToNormal -> {
				Link originLink = network.getLinks().get( Id.createLinkId( "80" ));
				Link destinationLink = network.getLinks().get( Id.createLinkId( "81" ));

				LeipzigUtils.setParkingToRestricted( originLink );

				Person person = factory.createPerson( Id.createPersonId( situation.toString() ) );

				Plan plan = factory.createPlan();
				final Activity originActivity = factory.createActivityFromLinkId( ActivityTypes.LEISURE, originLink.getId() );
				originActivity.setEndTime( 3600. );
				plan.addActivity( originActivity );
				plan.addLeg( factory.createLeg( TransportMode.car ) );
				plan.addActivity( factory.createActivityFromLinkId( ActivityTypes.LEISURE, destinationLink.getId()) );

				person.addPlan( plan );

				population.addPerson( person );
			}

			// yy Ich habe den Setup der Fälle, die hier kommen, leider nicht verstanden.  Daher habe ich obigen Fall "restrictedToNormal" neu gebaut.  Sorry ...  kai, apr'23

			case residentInResidentialArea -> {
				Person residentInResidentialArea = factory.createPerson(Id.createPersonId("residentInResidentialArea"));

				Plan plan1 = factory.createPlan();
				//maybe we also need to set start / end times for the activities..
				plan1.addActivity(factory.createActivityFromLinkId(ActivityTypes.HOME, Id.createLinkId("80")));
				plan1.addLeg(carLeg);
				// (yy The way that was set up it would not have worked: You cannot re-use a leg that is also inserted in some other plan.  kai, apr'23)

				plan1.addActivity(factory.createActivityFromLinkId(ActivityTypes.EDUCATION, Id.createLinkId("81")));
				residentInResidentialArea.addPlan(plan1);
				residentInResidentialArea.getAttributes().putAttribute("parkingType", "residential");

				population.addPerson(residentInResidentialArea);
			}
			case residentOutsideResidentialArea -> {
				Person residentOutsideResidentialArea = factory.createPerson(Id.createPersonId("residentOutsideResidentialArea"));

				Plan plan2 = factory.createPlan();
				plan2.addActivity(factory.createActivityFromLinkId(ActivityTypes.HOME, Id.createLinkId("35")));
				plan2.addLeg(carLeg);
				plan2.addActivity(factory.createActivityFromLinkId(ActivityTypes.WORK, Id.createLinkId("31")));
				residentOutsideResidentialArea.addPlan(plan2);
				residentOutsideResidentialArea.getAttributes().putAttribute("parkingType", "residential");

				population.addPerson( residentOutsideResidentialArea );
			}
			case nonResidentInResidentialAreaNoShop -> {
				Person nonResidentInResidentialAreaNoShop = factory.createPerson(Id.createPersonId("nonResidentInResidentialAreaNoShop"));

				Plan plan3 = factory.createPlan();
				plan3.addActivity(factory.createActivityFromLinkId(ActivityTypes.HOME, Id.createLinkId("41")));
				plan3.addLeg(carLeg);
				plan3.addActivity(factory.createActivityFromLinkId(ActivityTypes.WORK, Id.createLinkId("45")));
				nonResidentInResidentialAreaNoShop.addPlan(plan3);
				nonResidentInResidentialAreaNoShop.getAttributes().putAttribute("parkingType", "non-residential");

				population.addPerson( nonResidentInResidentialAreaNoShop );
			}
			case nonResidentInResidentialAreaShop -> {
				Person nonResidentInResidentialAreaShop = factory.createPerson(Id.createPersonId("nonResidentInResidentialAreaShop"));

				Plan plan4 = factory.createPlan();
				plan4.addActivity(factory.createActivityFromLinkId(ActivityTypes.HOME, Id.createLinkId("40")));
				plan4.addLeg(carLeg);
				plan4.addActivity(factory.createActivityFromLinkId(ActivityTypes.SHOPPING, Id.createLinkId("135")));
				nonResidentInResidentialAreaShop.addPlan(plan4);
				nonResidentInResidentialAreaShop.getAttributes().putAttribute("parkingType", "non-residential");

				population.addPerson(nonResidentInResidentialAreaShop);
			}
			case nonResidentOutsideResidentialArea -> {
				Person nonResidentOutsideResidentialArea = factory.createPerson(Id.createPersonId("nonResidentOutsideResidentialArea"));

				Plan plan5 = factory.createPlan();
				plan5.addActivity(factory.createActivityFromLinkId(ActivityTypes.HOME, Id.createLinkId("64")));
				plan5.addLeg(carLeg);
				plan5.addActivity(factory.createActivityFromLinkId(ActivityTypes.LEISURE, Id.createLinkId("66")));
				nonResidentOutsideResidentialArea.addPlan(plan5);
				nonResidentOutsideResidentialArea.getAttributes().putAttribute("parkingType", "non-residential");

				population.addPerson( nonResidentOutsideResidentialArea );
			}
			default -> throw new IllegalStateException( "Unexpected value: " + situation );
		}


		//residential area is maximum including the following edges (square): 124-126, 34-36, 178-180, 88-90

	}
}
