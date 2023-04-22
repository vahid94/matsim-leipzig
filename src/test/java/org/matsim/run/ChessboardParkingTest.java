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
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.GlobalConfigGroup;
import org.matsim.core.config.groups.StrategyConfigGroup;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.algorithms.PlanAlgorithm;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.replanning.PlanStrategy;
import org.matsim.core.replanning.PlanStrategyImpl;
import org.matsim.core.replanning.modules.AbstractMultithreadedModule;
import org.matsim.core.replanning.selectors.RandomPlanSelector;
import org.matsim.core.router.*;
import org.matsim.core.scenario.MutableScenario;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.timing.TimeInterpretation;
import org.matsim.core.utils.timing.TimeTracker;
import org.matsim.examples.ExamplesUtils;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.FacilitiesUtils;
import org.matsim.facilities.Facility;
import org.matsim.run.prepare.LeipzigUtils;
import org.matsim.testcases.MatsimTestUtils;
import org.matsim.vehicles.Vehicle;
import playground.vsp.openberlinscenario.cemdap.output.ActivityTypes;

import javax.inject.Provider;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.matsim.run.prepare.LeipzigUtils.parkingIsRestricted;

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

	static final class LeipzigPlanRouter implements PlanAlgorithm {
		private final TripRouter tripRouter;
		private final ActivityFacilities facilities;
		private final TimeInterpretation timeInterpretation;
		private final Network fullModalNetwork;
		private final Network reducedNetwork;
		private final MultimodalLinkChooser linkChooser;
		private final Scenario scenario;
		LeipzigPlanRouter( final TripRouter tripRouter, final ActivityFacilities facilities, final TimeInterpretation timeInterpretation,
					  SingleModeNetworksCache singleModeNetworksCache, Scenario scenario, MultimodalLinkChooser linkChooser ) {
			this.tripRouter = tripRouter;
			this.facilities = facilities;
			this.timeInterpretation = timeInterpretation;
			this.scenario = scenario;
			this.fullModalNetwork = singleModeNetworksCache.getSingleModeNetworksCache().get( TransportMode.car );

			// yyyy one should look at the networks cache and see how the following is done.  And maybe even register it there.
			this.reducedNetwork = NetworkUtils.createNetwork( scenario.getConfig().network() );
			this.linkChooser = linkChooser;
			for( Node node : this.fullModalNetwork.getNodes().values() ){
				reducedNetwork.addNode( node );
			}
			for( Link link : this.fullModalNetwork.getLinks().values() ){
				if ( !LeipzigUtils.parkingIsRestricted( link ) ) {
					reducedNetwork.addLink( link );
				}
			}
			log.warn("returning LeipzigPlanRouter");
		}
		enum ParkingType { normal, restricted, shopping }
		@Override public void run(final Plan plan) {
			final List<TripStructureUtils.Trip> trips = TripStructureUtils.getTrips( plan );
			TimeTracker timeTracker = new TimeTracker(timeInterpretation);

			log.warn( "=== old plan: ===" );
			for( PlanElement tripElement : plan.getPlanElements() ){
				log.warn( tripElement );
			}
			log.warn( "======" );

			for ( TripStructureUtils.Trip oldTrip : trips) {
				final String routingMode = TripStructureUtils.identifyMainMode( oldTrip.getTripElements() );
				timeTracker.addActivity(oldTrip.getOriginActivity());

				final Facility fromFacility = FacilitiesUtils.toFacility( oldTrip.getOriginActivity(), facilities );
				final Facility toFacility = FacilitiesUtils.toFacility( oldTrip.getDestinationActivity(), facilities );

				// At this point, I only want to deal with residential parking.  Shopping comes later (and is simpler).

				ParkingType parkingTypeAtOrigin = getParkingType( fullModalNetwork, oldTrip.getOriginActivity() );
				ParkingType parkingTypeAtDestination = getParkingType( fullModalNetwork, oldTrip.getDestinationActivity() );

				if ( parkingTypeAtOrigin==ParkingType.normal  && parkingTypeAtDestination==ParkingType.normal ){
					// standard case:

					final List<? extends PlanElement> newTripElements = tripRouter.calcRoute( routingMode, fromFacility, toFacility,
							timeTracker.getTime().seconds(), plan.getPerson(), oldTrip.getTripAttributes() );
					putVehicleFromOldTripIntoNewTripIfMeaningful( oldTrip, newTripElements );
					TripRouter.insertTrip( plan, oldTrip.getOriginActivity(), newTripElements, oldTrip.getDestinationActivity() );
					timeTracker.addElements( newTripElements );
				} else if ( parkingTypeAtOrigin==ParkingType.restricted && parkingTypeAtDestination==ParkingType.normal ) {
					// restricted parking at origin:

					// first find parking:
//					final Link parkingLink = NetworkUtils.getNearestLink( reducedNetwork, oldTrip.getOriginActivity().getCoord() );

					final Link parkingLink = linkChooser.decideOnLink( fromFacility, reducedNetwork );

					final Activity parkingActivity = scenario.getPopulation().getFactory().createInteractionActivityFromLinkId(
							TripStructureUtils.createStageActivityType( "parking" ), parkingLink.getId() );
					final Facility parkingFacility = FacilitiesUtils.toFacility( parkingActivity, facilities );

					List<PlanElement> newTripElements = new ArrayList<>();

					// trip from origin to parking:
					final List<? extends PlanElement> walkTripElements = tripRouter.calcRoute( TransportMode.walk, fromFacility, parkingFacility,
							timeTracker.getTime().seconds(), plan.getPerson(), oldTrip.getTripAttributes() );
					newTripElements.addAll( walkTripElements );

					// parking interaction:
					newTripElements.add( parkingActivity );

					// trip from parking to final destination:
					final List<? extends PlanElement> carTripElements = tripRouter.calcRoute( routingMode, parkingFacility, toFacility,
							timeTracker.getTime().seconds(), plan.getPerson(), oldTrip.getTripAttributes() );
					newTripElements.addAll( carTripElements );

					putVehicleFromOldTripIntoNewTripIfMeaningful( oldTrip, newTripElements );
					TripRouter.insertTrip( plan, parkingActivity, newTripElements, oldTrip.getDestinationActivity() );
					timeTracker.addElements( newTripElements );


				} else  {
					throw new RuntimeException();
					// to be implemented!
				}

			}
			log.warn( "=== new plan: ===" );
			for( PlanElement tripElement : plan.getPlanElements() ){
				log.warn( tripElement );
			}
			log.warn( "======" );

		}
		private static ParkingType getParkingType( Network fullModalNetwork, Activity originActivity ){
			ParkingType parkingTypeAtOrigin = ParkingType.normal;

			// check if non-home activity (since otherwise we assume that there is no parking restriction):
			if ( !originActivity.getType().equals( ActivityTypes.HOME ) ) {

				// if non-home activity, check if in restricted zone:
				Link link = fullModalNetwork.getLinks().get( originActivity.getLinkId() );
				if ( parkingIsRestricted(link) ) {
					parkingTypeAtOrigin = ParkingType.restricted;
				}

			}
			return parkingTypeAtOrigin;
		}

		/**
		 * If the old trip had vehicles set in its network routes, and it used a single vehicle,
		 * and if the new trip does not come with vehicles set in its network routes,
		 * then put the vehicle of the old trip into the network routes of the new trip.
		 * @param oldTrip The old trip
		 * @param newTrip The new trip
		 * @deprecated -- use from PlanRouter
		 */
		private static void putVehicleFromOldTripIntoNewTripIfMeaningful( TripStructureUtils.Trip oldTrip, List<? extends PlanElement> newTrip ) {
			Id<Vehicle> oldVehicleId = getUniqueVehicleId(oldTrip );
			if (oldVehicleId != null) {
				for (Leg leg : TripStructureUtils.getLegs(newTrip)) {
					if (leg.getRoute() instanceof NetworkRoute ) {
						if (((NetworkRoute) leg.getRoute()).getVehicleId() == null) {
							((NetworkRoute) leg.getRoute()).setVehicleId(oldVehicleId);
						}
					}
				}
			}
		}

		/**
		 * @param trip
		 * @return
		 * 		 * @deprecated -- use from PlanRouter
		 */
		private static Id<Vehicle> getUniqueVehicleId( TripStructureUtils.Trip trip ) {
			Id<Vehicle> vehicleId = null;
			for (Leg leg : trip.getLegsOnly()) {
				if (leg.getRoute() instanceof NetworkRoute) {
					if (vehicleId != null && (!vehicleId.equals(((NetworkRoute) leg.getRoute()).getVehicleId()))) {
						return null; // The trip uses several vehicles.
					}
					vehicleId = ((NetworkRoute) leg.getRoute()).getVehicleId();
				}
			}
			return vehicleId;
		}


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
					return new LeipzigPlanRouter( tripRouterProvider.get(), facilities, timeInterpretation, singleModeNetworksCache, scenario, linkChooser );
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
