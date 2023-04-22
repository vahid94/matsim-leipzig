//package org.matsim.run;
//
//import com.google.inject.Inject;
//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;
//import org.matsim.api.core.v01.Scenario;
//import org.matsim.api.core.v01.TransportMode;
//import org.matsim.api.core.v01.network.Network;
//import org.matsim.api.core.v01.population.Plan;
//import org.matsim.api.core.v01.population.PlanElement;
//import org.matsim.api.core.v01.population.PopulationFactory;
//import org.matsim.core.config.groups.GlobalConfigGroup;
//import org.matsim.core.network.filter.NetworkFilterManager;
//import org.matsim.core.population.algorithms.PlanAlgorithm;
//import org.matsim.core.replanning.modules.AbstractMultithreadedModule;
//import org.matsim.core.router.TripRouter;
//import org.matsim.core.router.TripStructureUtils;
//import playground.vsp.simpleParkingCostHandler.ParkingCostConfigGroup;
//
//import java.util.ArrayList;
//import java.util.List;
//
//class ParkingCorrectionMultithreadedModule extends AbstractMultithreadedModule {
//
//	@Inject
//	Scenario scenario;
//
//	@Inject
//	ParkingCostConfigGroup parkingCostConfigGroup;
//
//
//	private static final Logger log = LogManager.getLogger(ParkingCorrectionMultithreadedModule.class );
//	private final PopulationFactory pf;
//
//	public ParkingCorrectionMultithreadedModule( GlobalConfigGroup globalConfigGroup, PopulationFactory pf ){
//		super( globalConfigGroup );
//		this.pf = pf;
//
//		NetworkFilterManager managerResidential = new NetworkFilterManager(scenario.getNetwork(), scenario.getConfig().network());
//		managerResidential.addLinkFilter(new LinkAttributeNetworkLinkFilter(parkingCostConfigGroup.getResidentialParkingFeeAttributeName(), "0."));
//
//		Network nonResidentialParkingNetwork = managerResidential.applyFilters();
//
//		NetworkFilterManager managerShop = new NetworkFilterManager(scenario.getNetwork(), scenario.getConfig().network());
//		//this is kind of ugly, but otherwise we would need a shp file here to filter for the links with shopping garages -sme0423
//		//maybe put this into a facility instead of networkAttribute
//		managerShop.addLinkFilter(new LinkAttributeNetworkLinkFilter("shoppingGarage", "true"));
//
//		Network shoppingNetwork = managerShop.applyFilters();
//
//
//	}
//	@Override public PlanAlgorithm getPlanAlgoInstance(){
//		return new PlanAlgorithm(){
//			@Override public void run( Plan plan ){
//
//				List<TripStructureUtils.Trip> trips = TripStructureUtils.getTrips( plan.getPlanElements() );
//
//				for( TripStructureUtils.Trip oldTrip : trips ){
//					//we dont need to check if the trip is routed because only plans affected by SMC will come here
//					// -> only plans with non-routed trips
////					if ( isRouted(oldTrip ) ) {
////						continue;;
////					}
//					// else check if parking is affected etc.
//
//					oldTrip.getOriginActivity();
//
//					oldTrip.getDestinationActivity();
//
//
//					List<PlanElement> newTrip = new ArrayList<>();
//
////					newTrip.add( oldTrip.getOriginActivity() );
//
//					newTrip.add( pf.createLeg( TransportMode.walk ) );
//
//					newTrip.add( pf.createInteractionActivityFromLinkId( ... ) );
//
//					newTrip .add( pf.createLeg( TransportMode.car );
//
//					newTrip.add( pf.createInteractionActivityFromLinkId( ... ) );
//
//					newTrip.add( pf.createLeg(  ))
//
////					newTrip.add( oldTrip.getDestinationActivity() );
//
//
//					TripRouter.insertTrip(
//							plan,
//							oldTrip.getOriginActivity(),
//							newTrip,
//							oldTrip.getDestinationActivity() );
//
//				}
//
//			}
//		};
//	}
//}
