package org.matsim.run;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.algorithms.PlanAlgorithm;
import org.matsim.core.router.MultimodalLinkChooser;
import org.matsim.core.router.SingleModeNetworksCache;
import org.matsim.core.router.TripRouter;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.utils.timing.TimeInterpretation;
import org.matsim.core.utils.timing.TimeTracker;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.FacilitiesUtils;
import org.matsim.facilities.Facility;
import org.matsim.run.prepare.LeipzigUtils;
import playground.vsp.openberlinscenario.cemdap.output.ActivityTypes;

import java.util.ArrayList;
import java.util.List;

import static org.matsim.core.router.PlanRouter.putVehicleFromOldTripIntoNewTripIfMeaningful;
import static org.matsim.run.prepare.LeipzigUtils.isLinkParkingTypeInsideResidentialArea;

final class LeipzigRouterPlanAlgorithm implements PlanAlgorithm{
	private static final Logger log = LogManager.getLogger(LeipzigRouterPlanAlgorithm.class );
	private final TripRouter tripRouter;
	private final ActivityFacilities facilities;
	private final TimeInterpretation timeInterpretation;
	private final Network fullModalNetwork;
	private final Network reducedNetwork;
	private final MultimodalLinkChooser linkChooser;
	private final Scenario scenario;
	private final Network networkForShopping;

	LeipzigRouterPlanAlgorithm( final TripRouter tripRouter, final ActivityFacilities facilities, final TimeInterpretation timeInterpretation,
				    SingleModeNetworksCache singleModeNetworksCache, Scenario scenario, MultimodalLinkChooser linkChooser ){
		this.tripRouter = tripRouter;
		this.facilities = facilities;
		this.timeInterpretation = timeInterpretation;
		this.scenario = scenario;
		this.fullModalNetwork = singleModeNetworksCache.getSingleModeNetworksCache().get( TransportMode.car );

		// yyyy one should look at the networks cache and see how the following is done.  And maybe even register it there.
		this.reducedNetwork = NetworkUtils.createNetwork( scenario.getConfig().network() );
		this.linkChooser = linkChooser;
		this.networkForShopping = NetworkUtils.createNetwork(scenario.getConfig().network());
		for( Node node : this.fullModalNetwork.getNodes().values() ){
			reducedNetwork.addNode( node );
		}
		for( Link link : this.fullModalNetwork.getLinks().values() ){
			if( !LeipzigUtils.isLinkParkingTypeInsideResidentialArea( link ) ){
				reducedNetwork.addLink( link );
			}
		}
		log.warn( "returning LeipzigPlanRouter" );
	}

	@Override public void run( final Plan plan ){
		final List<TripStructureUtils.Trip> trips = TripStructureUtils.getTrips( plan );
		TimeTracker timeTracker = new TimeTracker( timeInterpretation );

		log.warn( "=== old plan: ===" );
		for( PlanElement tripElement : plan.getPlanElements() ){
			log.warn( tripElement );
		}
		log.warn( "======" );

		for( TripStructureUtils.Trip oldTrip : trips ){
			final String routingMode = TripStructureUtils.identifyMainMode( oldTrip.getTripElements() );
			timeTracker.addActivity( oldTrip.getOriginActivity() );

			final Facility fromFacility = FacilitiesUtils.toFacility( oldTrip.getOriginActivity(), facilities );
			final Facility toFacility = FacilitiesUtils.toFacility( oldTrip.getDestinationActivity(), facilities );

			log.warn("fromFacility=" + fromFacility);
//			System.exit(-1);

			// At this point, I only want to deal with residential parking.  Shopping comes later (and is simpler).

			LeipzigUtils.PersonParkingBehaviour parkingBehaviourAtOrigin = getParkingBehaviour( fullModalNetwork, oldTrip.getOriginActivity() );
			LeipzigUtils.PersonParkingBehaviour parkingBehaviourAtDestination = getParkingBehaviour( fullModalNetwork, oldTrip.getDestinationActivity() );

			if ( parkingBehaviourAtOrigin == LeipzigUtils.PersonParkingBehaviour.defaultLogic
					&& parkingBehaviourAtDestination == LeipzigUtils.PersonParkingBehaviour.defaultLogic){
				// standard case:
				final List<? extends PlanElement> newTripElements = tripRouter.calcRoute( routingMode, fromFacility, toFacility,
						timeTracker.getTime().seconds(), plan.getPerson(), oldTrip.getTripAttributes() );
				putVehicleFromOldTripIntoNewTripIfMeaningful( oldTrip, newTripElements );
				TripRouter.insertTrip( plan, oldTrip.getOriginActivity(), newTripElements, oldTrip.getDestinationActivity() );
				timeTracker.addElements( newTripElements );

			} else if ( parkingBehaviourAtOrigin == LeipzigUtils.PersonParkingBehaviour.parkingSearchLogicLeipzig
					&& parkingBehaviourAtDestination == LeipzigUtils.PersonParkingBehaviour.defaultLogic){

				TripRouter.insertTrip( plan, oldTrip.getOriginActivity(),
						createTripForParkingAtOrigin(oldTrip, routingMode, fromFacility, toFacility, plan, timeTracker), oldTrip.getDestinationActivity() );

			} else if (parkingBehaviourAtOrigin == LeipzigUtils.PersonParkingBehaviour.defaultLogic
					&& parkingBehaviourAtDestination == LeipzigUtils.PersonParkingBehaviour.parkingSearchLogicLeipzig) {

				TripRouter.insertTrip(plan, oldTrip.getOriginActivity(),
						createTripForParkingAtDestination(oldTrip, routingMode, fromFacility, toFacility, plan, timeTracker), oldTrip.getDestinationActivity());


			} else if (parkingBehaviourAtOrigin == LeipzigUtils.PersonParkingBehaviour.parkingSearchLogicLeipzig
					&& parkingBehaviourAtDestination == LeipzigUtils.PersonParkingBehaviour.parkingSearchLogicLeipzig){

				List<PlanElement> parkingAtOriginTrip = createTripForParkingAtOrigin(oldTrip, routingMode, fromFacility, toFacility, plan, timeTracker);

				//throw away last leg of the above trip because we need to route to dest parking first
				PlanElement removedElement = parkingAtOriginTrip.remove(parkingAtOriginTrip.size() -1);
				//timeTracker receives the now deleted leg as an element to track (in the createTripForParkingAtOrigin method)
				// it seems like we cannot remove it anymore. Is this a problem? -sme0523

				Activity parkingInteraction = null;

				if (parkingAtOriginTrip.get(parkingAtOriginTrip.size() - 1) instanceof Activity) {
					parkingInteraction = (Activity) parkingAtOriginTrip.get(parkingAtOriginTrip.size() - 1);
				} else {
					throw new RuntimeException();
				}

				Facility fromFacilityParking = FacilitiesUtils.toFacility( parkingInteraction, facilities );

				List<PlanElement> parkingAtDestinationTrip = createTripForParkingAtDestination(oldTrip, routingMode, fromFacilityParking, toFacility, plan, timeTracker);
				List<PlanElement> parkingAtDestinationTrip2 = createTripForParkingAtDestination(oldTrip, routingMode, fromFacilityParking, toFacility, plan, timeTracker);

				parkingAtOriginTrip.addAll(parkingAtDestinationTrip);

				TripRouter.insertTrip( plan, oldTrip.getOriginActivity(), parkingAtOriginTrip, oldTrip.getDestinationActivity() );
			} else {
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

	private static LeipzigUtils.PersonParkingBehaviour getParkingBehaviour(Network fullModalNetwork, Activity originActivity ){
		LeipzigUtils.PersonParkingBehaviour parkingBehaviourAtOrigin = LeipzigUtils.PersonParkingBehaviour.defaultLogic;

		// if we find out that there are time restrictions on all the links
		//originActivity.getEndTime();

		// an dieser stelle waere es besser abzufragen, ob die person in der naehe wohnt anstatt nur die home act -> residential parking zuzuordnen
		// check if non-home activity (since otherwise we assume that there is no parking restriction):
		if( !originActivity.getType().equals( ActivityTypes.HOME ) ){

			// if non-home activity, check if in residential parking area:
			Link link = fullModalNetwork.getLinks().get( originActivity.getLinkId() );
			if( isLinkParkingTypeInsideResidentialArea( link ) ){
				parkingBehaviourAtOrigin = LeipzigUtils.PersonParkingBehaviour.parkingSearchLogicLeipzig;
//				if (originActivity.getType().equals(ActivityTypes.SHOPPING)) {
//					// change this to parking type normal/ unrestricted
//					// on activity link, unrestricted
//					parkingBehaviourAtOrigin = LeipzigUtils.PersonParkingBehaviour.shopping;
//				}
			}

		}
		return parkingBehaviourAtOrigin;
	}

	private List<PlanElement> createTripForParkingAtOrigin(TripStructureUtils.Trip oldTrip, String routingMode, Facility fromFacility, Facility toFacility, Plan plan, TimeTracker timeTracker) {
		List<PlanElement> newTripElements = new ArrayList<>();

		// restricted parking at origin:
		// first find parking:
//				final Link parkingLink = NetworkUtils.getNearestLink( reducedNetwork, oldTrip.getOriginActivity().getCoord() );
		final Link parkingLink = linkChooser.decideOnLink( fromFacility, reducedNetwork );

		final Activity parkingActivity = scenario.getPopulation().getFactory().createInteractionActivityFromLinkId(
				TripStructureUtils.createStageActivityType( "parking" ), parkingLink.getId() );
		final Facility parkingFacility = FacilitiesUtils.toFacility( parkingActivity, facilities );

		// trip from origin to parking:
		final List<? extends PlanElement> walkTripElements = tripRouter.calcRoute( TransportMode.walk, fromFacility, parkingFacility,
				timeTracker.getTime().seconds(), plan.getPerson(), oldTrip.getTripAttributes() );
		for( PlanElement tripElement : walkTripElements ){
			if ( tripElement instanceof Leg ) {
				TripStructureUtils.setRoutingMode( (Leg) tripElement, TransportMode.car );
			}
		}

		newTripElements.addAll( walkTripElements );
		// parking interaction:
		newTripElements.add( parkingActivity );
		// trip from parking to final destination:
		final List<? extends PlanElement> carTripElements = tripRouter.calcRoute( routingMode, parkingFacility, toFacility,
				timeTracker.getTime().seconds(), plan.getPerson(), oldTrip.getTripAttributes() );
		newTripElements.addAll( carTripElements );

		putVehicleFromOldTripIntoNewTripIfMeaningful( oldTrip, newTripElements );
		timeTracker.addElements( newTripElements );

		return newTripElements;
	}

	private List<PlanElement> createTripForParkingAtDestination(TripStructureUtils.Trip oldTrip, String routingMode, Facility fromFacility, Facility toFacility, Plan plan, TimeTracker timeTracker) {
		List<PlanElement> newTripElements = new ArrayList<>();

		//parking at destination
		final Link parkingLink = linkChooser.decideOnLink(toFacility, reducedNetwork);
		final Activity parkingActivity = scenario.getPopulation().getFactory().createInteractionActivityFromLinkId(
				TripStructureUtils.createStageActivityType("parking"), parkingLink.getId());
		final Facility parkingFacility = FacilitiesUtils.toFacility(parkingActivity, facilities);

		// trip from origin to parking:
		final List<? extends PlanElement> carTripElements = tripRouter.calcRoute(routingMode, fromFacility, parkingFacility,
				timeTracker.getTime().seconds(), plan.getPerson(), oldTrip.getTripAttributes());
		// parking interaction:

		newTripElements.addAll(carTripElements);
		newTripElements.add(parkingActivity);

		// trip from parking to destination:
		final List<? extends PlanElement> walkTripElements = tripRouter.calcRoute(TransportMode.walk, parkingFacility, toFacility,
				timeTracker.getTime().seconds(), plan.getPerson(), oldTrip.getTripAttributes());
		for (PlanElement tripElement : walkTripElements) {
			if (tripElement instanceof Leg) {
				TripStructureUtils.setRoutingMode((Leg) tripElement, TransportMode.car);
			}
		}
		newTripElements.addAll(walkTripElements);

		putVehicleFromOldTripIntoNewTripIfMeaningful(oldTrip, newTripElements);

		timeTracker.addElements(newTripElements);

		return newTripElements;
	}


}
