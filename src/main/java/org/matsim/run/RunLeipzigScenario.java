package org.matsim.run;

import analysis.LeipzigMainModeIdentifier;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;
import com.google.common.collect.Sets;
import org.matsim.analysis.ModeChoiceCoverageControlerListener;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.application.MATSimApplication;
import org.matsim.application.analysis.CheckPopulation;
import org.matsim.application.analysis.traffic.LinkStats;
import org.matsim.application.analysis.travelTimeValidation.TravelTimeAnalysis;
import org.matsim.application.options.SampleOptions;
import org.matsim.application.prepare.CreateLandUseShp;
import org.matsim.application.prepare.freight.ExtractRelevantFreightTrips;
import org.matsim.application.prepare.network.CleanNetwork;
import org.matsim.application.prepare.network.CreateNetworkFromSumo;
import org.matsim.application.prepare.population.*;
import org.matsim.application.prepare.pt.CreateTransitScheduleFromGtfs;
import org.matsim.contrib.drt.fare.DrtFareParams;
import org.matsim.contrib.drt.routing.DrtRoute;
import org.matsim.contrib.drt.routing.DrtRouteFactory;
import org.matsim.contrib.drt.run.DrtConfigs;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtModule;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpModule;
import org.matsim.contrib.dvrp.run.DvrpQSimComponents;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.router.AnalysisMainModeIdentifier;
import org.matsim.run.prepare.PrepareNetwork;
import org.matsim.run.prepare.PreparePopulation;
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
        MergePopulations.class, ExtractRelevantFreightTrips.class, DownSamplePopulation.class, PrepareNetwork.class, CleanNetwork.class,
        CreateLandUseShp.class, ResolveGridCoordinates.class, PreparePopulation.class, CleanPopulation.class
})
@MATSimApplication.Analysis({
        CheckPopulation.class, TravelTimeAnalysis.class, LinkStats.class
})
public class RunLeipzigScenario extends MATSimApplication {

    static final String VERSION = "1.0";

    @CommandLine.Mixin
    private final SampleOptions sample = new SampleOptions(1, 10, 25);

    @CommandLine.Option(names = "--with-drt", defaultValue = "false", description = "enable DRT service")
    private boolean drt;

    public RunLeipzigScenario(@Nullable Config config) {
        super(config);
    }

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

            for (String act : List.of("home", "restaurant", "other", "visit", "errands",
                    "educ_higher", "educ_secondary", "educ_primary", "educ_tertiary", "educ_kiga", "educ_other")) {
                config.planCalcScore()
                        .addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams(act + "_" + ii + ".0").setTypicalDuration(ii));
            }

            config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("work_" + ii + ".0").setTypicalDuration(ii)
                    .setOpeningTime(6. * 3600.).setClosingTime(20. * 3600.));
            config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("business_" + ii + ".0").setTypicalDuration(ii)
                    .setOpeningTime(6. * 3600.).setClosingTime(20. * 3600.));
            config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("leisure_" + ii + ".0").setTypicalDuration(ii)
                    .setOpeningTime(9. * 3600.).setClosingTime(27. * 3600.));

            config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("shop_daily_" + ii + ".0").setTypicalDuration(ii)
                    .setOpeningTime(8. * 3600.).setClosingTime(20. * 3600.));
            config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("shop_other_" + ii + ".0").setTypicalDuration(ii)
                    .setOpeningTime(8. * 3600.).setClosingTime(20. * 3600.));
        }

        config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("car interaction").setTypicalDuration(60));
        config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("other").setTypicalDuration(600 * 3));

        config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("freight_start").setTypicalDuration(60 * 15));
        config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("freight_end").setTypicalDuration(60 * 15));

        config.controler().setOutputDirectory(sample.adjustName(config.controler().getOutputDirectory()));
        config.plans().setInputFile(sample.adjustName(config.plans().getInputFile()));

        config.qsim().setFlowCapFactor(sample.getSize() / 100.0);
        config.qsim().setStorageCapFactor(sample.getSize() / 100.0);

        // also consider the unclosed trips
        config.subtourModeChoice().setProbaForRandomSingleTripMode(0.5);

        config.vspExperimental().setVspDefaultsCheckingLevel(VspExperimentalConfigGroup.VspDefaultsCheckingLevel.info);
        config.plansCalcRoute().setAccessEgressType(PlansCalcRouteConfigGroup.AccessEgressType.accessEgressModeToLink);

        if(drt) {
            MultiModeDrtConfigGroup multiModeDrtConfigGroup = ConfigUtils.addOrGetModule(config, MultiModeDrtConfigGroup.class);
            ConfigUtils.addOrGetModule(config, DvrpConfigGroup.class);
            DrtConfigs.adjustMultiModeDrtConfig(multiModeDrtConfigGroup, config.planCalcScore(), config.plansCalcRoute());
        }

        return config;
    }

    @Override
    protected void prepareScenario(Scenario scenario) {

//Commented this section out as we need a drt vehicles files anyways, vehicle types are defined in said file - sm0122

//        VehiclesFactory f = VehicleUtils.getFactory();

//        VehicleType car = f.createVehicleType( Id.create("car", VehicleType.class ) );
//        car.setMaximumVelocity(140.0/3.6);
//        car.setPcuEquivalents(1.0);
//        scenario.getVehicles().addVehicleType(car);
//
//        VehicleType ride = f.createVehicleType( Id.create("ride", VehicleType.class ) );
//        ride.setMaximumVelocity(140.0/3.6);
//        ride.setPcuEquivalents(1.0);
//        scenario.getVehicles().addVehicleType(ride);
//
//        VehicleType freight = f.createVehicleType(Id.create("freight", VehicleType.class));
//        freight.setMaximumVelocity(100.0/3.6);
//        freight.setPcuEquivalents(4);
//        scenario.getVehicles().addVehicleType(freight);
//
//        VehicleType bike = f.createVehicleType(Id.create("bike", VehicleType.class));
//        bike.setMaximumVelocity(15.0/3.6);
//        bike.setPcuEquivalents(0.25);
//        scenario.getVehicles().addVehicleType(bike);


        for (Link link : scenario.getNetwork().getLinks().values()) {
            Set<String> modes = link.getAllowedModes();

            // allow freight traffic together with cars
            if (modes.contains("car")) {
                HashSet<String> newModes = Sets.newHashSet(modes);
                newModes.add("freight");

                // the bike network is not fully connected yet
                //newModes.add("bike");

                link.setAllowedModes(newModes);
            }
        }

        if(drt) {
            scenario.getPopulation().getFactory().getRouteFactories().setRouteFactory(DrtRoute.class, new DrtRouteFactory());
        }
    }

    @Override
    protected void prepareControler(Controler controler) {
        Config config = controler.getConfig();

        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                install(new SwissRailRaptorModule());

                addTravelTimeBinding(TransportMode.ride).to(networkTravelTime());
                addTravelDisutilityFactoryBinding(TransportMode.ride).to(carTravelDisutilityFactoryKey());

                bind(AnalysisMainModeIdentifier.class).to(LeipzigMainModeIdentifier.class);
                addControlerListenerBinding().to(ModeChoiceCoverageControlerListener.class);
            }
        });

        if(drt) {
            MultiModeDrtConfigGroup multiModeDrtConfigGroup = ConfigUtils.addOrGetModule(config, MultiModeDrtConfigGroup.class);

            //set fare params; flexa has the same prices as leipzig PT: 3€ per trip - sm 4.1.22
            DrtFareParams drtFareParams = new DrtFareParams();
            drtFareParams.setBaseFare(3.);
            drtFareParams.setDistanceFare_m(0.);
            drtFareParams.setTimeFare_h(0.);
            drtFareParams.setDailySubscriptionFee(0.);
            drtFareParams.setDistanceFare_m(0.);

            multiModeDrtConfigGroup.getModalElements().forEach(drtConfigGroup -> {
                drtConfigGroup.addParameterSet(drtFareParams);
                DrtConfigs.adjustDrtConfig(drtConfigGroup, config.planCalcScore(), config.plansCalcRoute());
            });

            controler.addOverridingModule(new DvrpModule());
            controler.addOverridingModule(new MultiModeDrtModule());
            controler.configureQSimComponents(DvrpQSimComponents.activateAllModes(multiModeDrtConfigGroup));

        }
    }
}
