package org.matsim.run;

import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;
import org.matsim.application.MATSimApplication;
import org.matsim.application.analysis.AnalysisSummary;
import org.matsim.application.analysis.TravelTimeAnalysis;
import org.matsim.application.prepare.CreateNetworkFromSumo;
import org.matsim.application.prepare.CreateTransitScheduleFromGtfs;
import org.matsim.application.prepare.GenerateShortDistanceTrips;
import org.matsim.application.prepare.TrajectoryToPlans;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import picocli.CommandLine;

import javax.annotation.Nullable;
import java.util.List;

@CommandLine.Command(header = ":: Open Leipzig Scenario ::", version = RunLeipzigScenario.VERSION)
@MATSimApplication.Prepare({
        CreateNetworkFromSumo.class, CreateTransitScheduleFromGtfs.class, TrajectoryToPlans.class, GenerateShortDistanceTrips.class
})
@MATSimApplication.Analysis({
        AnalysisSummary.class, TravelTimeAnalysis.class
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

        return config;
    }

    @Override
    protected void prepareControler(Controler controler) {
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                install(new SwissRailRaptorModule());
            }
        });
    }
}
