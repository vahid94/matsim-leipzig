package analysis.emissions;

/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2017 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.CrsOptions;
import org.matsim.contrib.emissions.EmissionModule;
import org.matsim.contrib.emissions.HbefaVehicleCategory;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.contrib.emissions.VspHbefaRoadTypeMapping;
import org.matsim.contrib.emissions.analysis.EmissionsOnLinkEventHandler;
import org.matsim.contrib.emissions.utils.EmissionsConfigGroup;
import org.matsim.contrib.emissions.utils.EmissionsConfigGroup.DetailedVsAverageLookupBehavior;
import org.matsim.contrib.emissions.utils.EmissionsConfigGroup.NonScenarioVehicles;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Injector;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.events.algorithms.EventWriterXML;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.CipherUtils;
import org.matsim.vehicles.EngineInformation;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import picocli.CommandLine;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import static org.matsim.application.ApplicationUtils.globFile;

/**
 * This class can be run by using the command line.
 * Required arguments include <br>
 * (1) path to run directory; <br>
 * (2) the run ID (e.g. "leipzig-25pct") <br>
 * (3) hbefa warm file path; <br>
 * (4) hbefa cold file path; <br>
 * <br>
 * Output files include
 * <br> (1) total emissions per link and
 * <br> (2) emissions in g/m, per link
 * (used for visualisation in SimWrapper).
 *
 * @author Ruan J. Gräbe (rgraebe)
 */

@CommandLine.Command(
        name = "air-pollution-by-vehicle",
        description = "Run offline air pollution analysis assuming default vehicles",
        mixinStandardHelpOptions = true,
        showDefaultValues = true
)
public class RunEmissionAnalysisByVehicleCategory implements MATSimAppCommand {

    private static final Logger log = LogManager.getLogger(RunEmissionAnalysisByVehicleCategory.class);

    @CommandLine.Parameters(paramLabel = "INPUT", arity = "1", description = "Path to run directory")
    private Path runDirectory;

    @CommandLine.Option(names = "--runId", description = "Pattern to match runId.", defaultValue = "*")
    private String runId;

    @CommandLine.Option(names = "--hbefa-warm", required = true)
    private Path hbefaWarmFile;

    @CommandLine.Option(names = "--hbefa-cold", required = true)
    private Path hbefaColdFile;

    @CommandLine.Option(names = "--hbefa-lookup", description = "Emission detailedVsAverageLookupBehavior", defaultValue = "directlyTryAverageTable")
    private DetailedVsAverageLookupBehavior lookupBehavior;

    @CommandLine.Option(names = "-p", description = "Password for encrypted hbefa files", interactive = true, required = false)
    private char[] password;

    @CommandLine.Option(names = "--output", description = "Output events file", required = false)
    private Path output;

    @CommandLine.Option(names = "--vehicle-type", description = "Map vehicle type to Hbefa category", defaultValue = "defaultVehicleType=PASSENGER_CAR")
    private Map<String, HbefaVehicleCategory> vehicleCategories;

    @CommandLine.Option(names = "--use-default-road-types", description = "Add default hbefa_road_type link attributes to the network", defaultValue = "true")
    private boolean useDefaultRoadTypes;

    @CommandLine.Mixin
    private final CrsOptions crs = new CrsOptions();

    public RunEmissionAnalysisByVehicleCategory() {
    }

    public static void main(String[] args) {
        new RunEmissionAnalysisByVehicleCategory().execute(args);
    }

    @Override
    public Integer call() throws Exception {

        if (password != null) {
            System.setProperty(CipherUtils.ENVIRONMENT_VARIABLE, new String(password));
            // null out the arrays when done
            Arrays.fill(password, ' ');
        }

        Config config = ConfigUtils.createConfig();
        config.vehicles().setVehiclesFile(String.valueOf(globFile(runDirectory, runId, "vehicles")));
        config.network().setInputFile(String.valueOf(globFile(runDirectory, runId, "network")));
        config.transit().setTransitScheduleFile(String.valueOf(globFile(runDirectory, runId, "transitSchedule")));
        config.transit().setVehiclesFile(String.valueOf(globFile(runDirectory, runId, "transitVehicles")));

        if (crs.getInputCRS() != null) {
            config.global().setCoordinateSystem(crs.getInputCRS());
        } else {
            config.global().setCoordinateSystem("EPSG:25832");
            log.info("Using coordinate system '{}'", config.global().getCoordinateSystem());
        }

        config.plans().setInputFile(null);
        config.parallelEventHandling().setNumberOfThreads(null);
        config.parallelEventHandling().setEstimatedNumberOfEvents(null);
        config.global().setNumberOfThreads(1);

        EmissionsConfigGroup eConfig = ConfigUtils.addOrGetModule(config, EmissionsConfigGroup.class);
        eConfig.setDetailedVsAverageLookupBehavior(lookupBehavior);
        eConfig.setAverageColdEmissionFactorsFile(this.hbefaColdFile.toString());
        eConfig.setAverageWarmEmissionFactorsFile(this.hbefaWarmFile.toString());
        eConfig.setNonScenarioVehicles(NonScenarioVehicles.ignore);

        String runDirectoryStr = String.valueOf(runDirectory);
        if (!runDirectoryStr.endsWith("/")) {
            runDirectoryStr = runDirectoryStr + "/";
        }
        final String analysisOutputDirectory = runDirectoryStr + "emission-analysis";
        File dir = new File(analysisOutputDirectory);
        if ( !dir.exists() ) { dir.mkdir(); }

        // search for the events file if not provided as (optional) input
        if (output == null) {
            output = Path.of(globFile(runDirectory, runId, "events"));
            log.info("Found events file {}. Using this for emissions analysis.", output);
        }
        String eventsFile = String.valueOf(output);

        // write emissions output to two files...
        final String outputEmissionsFile = analysisOutputDirectory + "/" + runId + ".emission.events.xml.gz";
        log.info("-------------------------------------------------");
        log.info("Writing emissions (link totals) to: {}", outputEmissionsFile);
        // for SimWrapper (visualisation)
        final String linkEmissionPerMOutputFile = analysisOutputDirectory + "/" + runId + ".emissionsPerLinkPerM.csv";
        log.info("Writing emissions per link [g/m] to: {}", linkEmissionPerMOutputFile);
        log.info("-------------------------------------------------");

        Scenario scenario = ScenarioUtils.loadScenario(config);

        // network
        if (useDefaultRoadTypes) {
            log.info("Using integrated road types");
            addDefaultRoadTypes(scenario.getNetwork());
        }
        { // vehicles
            Id<VehicleType> carVehicleTypeId = Id.create("car", VehicleType.class);
            Id<VehicleType> freightVehicleTypeId = Id.create("freight", VehicleType.class);
            Id<VehicleType> drtVehicleTypeId = Id.create("conventional_vehicle", VehicleType.class);

            VehicleType carVehicleType = scenario.getVehicles().getVehicleTypes().get(carVehicleTypeId);
            VehicleType freightVehicleType = scenario.getVehicles().getVehicleTypes().get(freightVehicleTypeId);
            VehicleType drtVehicleType = scenario.getVehicles().getVehicleTypes().get(drtVehicleTypeId);

            EngineInformation carEngineInformation = carVehicleType.getEngineInformation();
            VehicleUtils.setHbefaVehicleCategory(carEngineInformation, HbefaVehicleCategory.PASSENGER_CAR.toString());
            VehicleUtils.setHbefaTechnology(carEngineInformation, "average");
            VehicleUtils.setHbefaSizeClass(carEngineInformation, "average");
            VehicleUtils.setHbefaEmissionsConcept(carEngineInformation, "average");

            EngineInformation freightEngineInformation = freightVehicleType.getEngineInformation();
            VehicleUtils.setHbefaVehicleCategory(freightEngineInformation, HbefaVehicleCategory.HEAVY_GOODS_VEHICLE.toString());
            VehicleUtils.setHbefaTechnology(freightEngineInformation, "average");
            VehicleUtils.setHbefaSizeClass(freightEngineInformation, "average");
            VehicleUtils.setHbefaEmissionsConcept(freightEngineInformation, "average");

            EngineInformation drtEngineInformation = drtVehicleType.getEngineInformation();
            VehicleUtils.setHbefaVehicleCategory(drtEngineInformation, HbefaVehicleCategory.PASSENGER_CAR.toString());
            VehicleUtils.setHbefaTechnology(drtEngineInformation, "average");
            VehicleUtils.setHbefaSizeClass(drtEngineInformation, "average");
            VehicleUtils.setHbefaEmissionsConcept(drtEngineInformation, "average");

            // public transit vehicles should be considered as non-hbefa vehicles
            for (VehicleType type : scenario.getTransitVehicles().getVehicleTypes().values()) {
                EngineInformation engineInformation = type.getEngineInformation();
                // TODO: Check! Is this a zero emission vehicle?!
                VehicleUtils.setHbefaVehicleCategory(engineInformation, HbefaVehicleCategory.NON_HBEFA_VEHICLE.toString());
                VehicleUtils.setHbefaTechnology(engineInformation, "average");
                VehicleUtils.setHbefaSizeClass(engineInformation, "average");
                VehicleUtils.setHbefaEmissionsConcept(engineInformation, "average");
            }

            // ignore bikes
            VehicleType bikeType = scenario.getVehicles().getVehicleTypes().get(Id.create("bike", VehicleType.class));
            EngineInformation bikeEngineInformation = bikeType.getEngineInformation();
            VehicleUtils.setHbefaVehicleCategory(bikeEngineInformation, HbefaVehicleCategory.NON_HBEFA_VEHICLE.toString());
        }

        EventsManager eventsManager = EventsUtils.createEventsManager();

        AbstractModule module = new AbstractModule() {
            @Override
            public void install() {
                bind(Scenario.class).toInstance(scenario);
                bind(EventsManager.class).toInstance(eventsManager);
                bind(EmissionModule.class);
            }
        };

        com.google.inject.Injector injector = Injector.createInjector(config, module);

        EmissionModule emissionModule = injector.getInstance(EmissionModule.class);

        EventWriterXML emissionEventWriter = new EventWriterXML(outputEmissionsFile);
        emissionModule.getEmissionEventsManager().addHandler(emissionEventWriter);

        // necessary for link emissions [g/m] output
        EmissionsOnLinkEventHandler emissionsOnLinkEventHandler = new EmissionsOnLinkEventHandler(10.);
        eventsManager.addHandler(emissionsOnLinkEventHandler);

        eventsManager.initProcessing();
        MatsimEventsReader matsimEventsReader = new MatsimEventsReader(eventsManager);
        matsimEventsReader.readFile(eventsFile);
        log.info("-------------------------------------------------");
        log.info("Done reading the events file");
        log.info("Finish processing...");
        eventsManager.finishProcessing();
        log.info("Closing events file...");
        emissionEventWriter.closeFile();
        log.info("Done");
        log.info("Writing (more) output...");

        { // writing emissions (per link) per meter
            File file1 = new File(linkEmissionPerMOutputFile);
            BufferedWriter bw1 = new BufferedWriter(new FileWriter(file1));

            bw1.write("linkId");

            for (Pollutant pollutant : Pollutant.values()) {
                bw1.write(";" + pollutant + " [g/m]");
            }
            bw1.newLine();

            Map<Id<Link>, Map<Pollutant, Double>> link2pollutants = emissionsOnLinkEventHandler.getLink2pollutants();

            for (Id<Link> linkId : link2pollutants.keySet()) {
                bw1.write(linkId.toString());

                for (Pollutant pollutant : Pollutant.values()) {
                    double emission = 0.;
                    if (link2pollutants.get(linkId).get(pollutant) != null) {
                        emission = link2pollutants.get(linkId).get(pollutant);
                    }

                    double emissionPerM = Double.NaN;
                    Link link = scenario.getNetwork().getLinks().get(linkId);
                    if (link != null) {
                        emissionPerM = emission / link.getLength();
                    }

                    bw1.write(";" + emissionPerM);
                }
                bw1.newLine();
            }

            bw1.close();
            log.info("Done");
            log.info("Output written to " + linkEmissionPerMOutputFile);
            log.info("-------------------------------------------------");
        }
        return 0;
    }

    private void addDefaultRoadTypes(Network network) {
        // network
        new VspHbefaRoadTypeMapping().addHbefaMappings(network);
    }

}

