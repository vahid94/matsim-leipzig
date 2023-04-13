package org.matsim.run;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.groups.GlobalConfigGroup;
import org.matsim.core.population.algorithms.PlanAlgorithm;
import org.matsim.core.replanning.modules.AbstractMultithreadedModule;
import org.matsim.core.router.TripStructureUtils;

import java.util.List;

class ParkingCorrectionMultithreadedModule extends AbstractMultithreadedModule {
	private static final Logger log = LogManager.getLogger(ParkingCorrectionMultithreadedModule.class );

	public ParkingCorrectionMultithreadedModule( GlobalConfigGroup globalConfigGroup ){
		super( globalConfigGroup );
	}
	@Override public PlanAlgorithm getPlanAlgoInstance(){
		return new PlanAlgorithm(){
			@Override public void run( Plan plan ){

				List<TripStructureUtils.Trip> trips = TripStructureUtils.getTrips( plan.getPlanElements() );

				for( TripStructureUtils.Trip trip : trips ){
					if ( isRouted(trip ) ) {
						continue;;
					}
					// else check if parking is affected etc.

					trip.getOriginActivity();

					trip.getDestinationActivity();

					Plan newPlan = null ;

					// ...


					plan = newPlan ; /// does not work

				}

			}
		};
	}
}
