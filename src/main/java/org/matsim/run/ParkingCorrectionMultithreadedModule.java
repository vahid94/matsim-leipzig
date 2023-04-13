package org.matsim.run;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.groups.GlobalConfigGroup;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.algorithms.PlanAlgorithm;
import org.matsim.core.replanning.modules.AbstractMultithreadedModule;
import org.matsim.core.router.TripRouter;
import org.matsim.core.router.TripStructureUtils;

import java.util.ArrayList;
import java.util.List;

class ParkingCorrectionMultithreadedModule extends AbstractMultithreadedModule {
	private static final Logger log = LogManager.getLogger(ParkingCorrectionMultithreadedModule.class );
	private final PopulationFactory pf;

	public ParkingCorrectionMultithreadedModule( GlobalConfigGroup globalConfigGroup, PopulationFactory pf ){
		super( globalConfigGroup );
		this.pf = pf;
	}
	@Override public PlanAlgorithm getPlanAlgoInstance(){
		return new PlanAlgorithm(){
			@Override public void run( Plan plan ){

				List<TripStructureUtils.Trip> trips = TripStructureUtils.getTrips( plan.getPlanElements() );

				for( TripStructureUtils.Trip oldTrip : trips ){
					if ( isRouted(oldTrip ) ) {
						continue;;
					}
					// else check if parking is affected etc.

					oldTrip.getOriginActivity();

					oldTrip.getDestinationActivity();


					List<PlanElement> newTrip = new ArrayList<>();

//					newTrip.add( oldTrip.getOriginActivity() );

					newTrip.add( pf.createLeg( TransportMode.walk ) );

					newTrip.add( pf.createInteractionActivityFromLinkId( ... ) );

					newTrip .add( pf.createLeg( TransportMode.car );

					newTrip.add( pf.createInteractionActivityFromLinkId( ... ) );

					newTrip.add( pf.createLeg(  ))

//					newTrip.add( oldTrip.getDestinationActivity() );


					TripRouter.insertTrip(
							plan,
							oldTrip.getOriginActivity(),
							newTrip,
							oldTrip.getDestinationActivity() );

				}

			}
		};
	}
}
