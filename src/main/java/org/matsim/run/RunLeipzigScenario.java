package org.matsim.run;

import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;
import com.google.common.collect.Sets;
import org.matsim.analysis.ModeChoiceCoverageControlerListener;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.application.MATSimApplication;
import org.matsim.application.analysis.AnalysisSummary;
import org.matsim.application.analysis.CheckPopulation;
import org.matsim.application.analysis.TravelTimeAnalysis;
import org.matsim.application.prepare.*;
import org.matsim.application.prepare.freight.ExtractRelevantFreightTrips;
import org.matsim.application.prepare.network.CreateNetworkFromSumo;
import org.matsim.application.prepare.population.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.VehiclesFactory;
import picocli.CommandLine;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@CommandLine.Command(header = ":: Open Leipzig Scenario ::", version = RunLeipzigScenario.VERSION)
@MATSimApplication.Prepare({
        CreateNetworkFromSumo.class, CreateTransitScheduleFromGtfs.class, TrajectoryToPlans.class, GenerateShortDistanceTrips.class,
        MergePopulations.class, ExtractRelevantFreightTrips.class, DownSamplePopulation.class,
        CreateLandUseShp.class, ResolveGridCoordinates.class
})
@MATSimApplication.Analysis({
        CheckPopulation.class, AnalysisSummary.class, TravelTimeAnalysis.class
})
public class RunLeipzigScenario extends MATSimApplication {

    static final String VERSION = "1.0";

    public RunLeipzigScenario() {
        super(String.format("scenarios/input/leipzig-v%s-25pct.config.xml", VERSION));
    }

    public static void main(String[] args) {
        MATSimApplication.run(RunLeipzigScenario.class, args);
    }

    @Nullable
    @Override
    protected Config prepareConfig(Config config) {

        for (long ii = 600; ii <= 97200; ii += 600) {

            for (String act : List.of("home", "restaurant", "other", "visit", "errands", "educ_higher",
                    "educ_secondary")) {
                config.planCalcScore()
                        .addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams(act + "_" + ii + ".0").setTypicalDuration(ii));
            }

            config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("work_" + ii + ".0").setTypicalDuration(ii)
                    .setOpeningTime(6. * 3600.).setClosingTime(20. * 3600.));
            config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("business_" + ii + ".0").setTypicalDuration(ii)
                    .setOpeningTime(6. * 3600.).setClosingTime(20. * 3600.));
            config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("leisure_" + ii + ".0").setTypicalDuration(ii)
                    .setOpeningTime(9. * 3600.).setClosingTime(27. * 3600.));
            config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("shopping_" + ii + ".0").setTypicalDuration(ii)
                    .setOpeningTime(8. * 3600.).setClosingTime(20. * 3600.));
        }

        config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("car interaction").setTypicalDuration(60));
        config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("other").setTypicalDuration(600 * 3));

        config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("freight_start").setTypicalDuration(60 * 15));
        config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("freight_end").setTypicalDuration(60 * 15));

        return config;
    }

    @Override
    protected void prepareScenario(Scenario scenario) {


        VehiclesFactory f = VehicleUtils.getFactory();

        VehicleType car = f.createVehicleType( Id.create("car", VehicleType.class ) );
        car.setMaximumVelocity(140.0/3.6);
        car.setPcuEquivalents(1.0);
        scenario.getVehicles().addVehicleType(car);

        VehicleType ride = f.createVehicleType( Id.create("ride", VehicleType.class ) );
        ride.setMaximumVelocity(140.0/3.6);
        ride.setPcuEquivalents(1.0);
        scenario.getVehicles().addVehicleType(ride);

        VehicleType freight = f.createVehicleType(Id.create("freight", VehicleType.class));
        freight.setMaximumVelocity(100.0/3.6);
        freight.setPcuEquivalents(4);
        scenario.getVehicles().addVehicleType(freight);

        VehicleType bike = f.createVehicleType(Id.create("bike", VehicleType.class));
        bike.setMaximumVelocity(15.0/3.6);
        bike.setPcuEquivalents(0.25);
        scenario.getVehicles().addVehicleType(bike);


        for (Link link : scenario.getNetwork().getLinks().values()) {
            Set<String> modes = link.getAllowedModes();

            // allow freight traffic together with cars
            if (modes.contains("car")) {
                HashSet<String> newModes = Sets.newHashSet(modes);
                newModes.add("freight");

                // the bike network is not fully connected yet
                newModes.add("bike");

                link.setAllowedModes(newModes);
            }
        }
    }

    @Override
    protected void prepareControler(Controler controler) {
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                install(new SwissRailRaptorModule());
                addControlerListenerBinding().to(ModeChoiceCoverageControlerListener.class);
            }
        });
    }
}
