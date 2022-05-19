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

import com.google.common.collect.Iterables;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
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
import org.matsim.contrib.emissions.events.ColdEmissionEvent;
import org.matsim.contrib.emissions.events.ColdEmissionEventHandler;
import org.matsim.contrib.emissions.events.WarmEmissionEvent;
import org.matsim.contrib.emissions.events.WarmEmissionEventHandler;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import static org.matsim.application.ApplicationUtils.globFile;


@CommandLine.Command(
        name = "air-pollution-by-vehicle",
        description = "Run offline air pollution analysis assuming default vehicles",
        mixinStandardHelpOptions = true,
        showDefaultValues = true
)
public class AirPollutionByVehicleCategory implements MATSimAppCommand {

    private static final Logger log = LogManager.getLogger(AirPollutionByVehicleCategory.class);

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

    @CommandLine.Option(names = "--use-default-road-types", description = "Add default hbefa_road_type link attributes to the network", defaultValue = "false")
    private boolean useDefaultRoadTypes;

    @CommandLine.Mixin
    private final CrsOptions crs = new CrsOptions();

    public AirPollutionByVehicleCategory() {
    }

    public AirPollutionByVehicleCategory(Path runDirectory, String runId, Path hbefaFileWarm, Path hbefaFileCold, Path output) {
        this.runDirectory = runDirectory;
        // e.g. "Users/rgraebe/IdeaProjects/matsim-leipzig/output/it-1pct/"
        this.runId = runId;
        // e.g. "leipzig-25pct"
        this.hbefaWarmFile = hbefaFileWarm;
        // e.g. "Users/rgraebe/shared-svn/projects/matsim-germany/hbefa/hbefa-files/v4.1/EFA_HOT_Vehcat_2020_Average.csv"
        this.hbefaColdFile = hbefaFileCold;
        // e.g. "Users/rgraebe/shared-svn/projects/matsim-germany/hbefa/hbefa-files/v4.1/EFA_ColdStart_Vehcat_2020_Average_withHGVetc.csv"
        this.output = output;
        // e.g. "Users/rgraebe/IdeaProjects/matsim-leipzig/output/it-1pct/leipzig-25pct.output_events.xml.gz"
    }

    @Override
    public Integer call() throws Exception {

        if (password != null) {
            System.setProperty(CipherUtils.ENVIRONMENT_VARIABLE, new String(password));
            // null out the arrays when done
            Arrays.fill(password, ' ');
        }

        Config config = ConfigUtils.createConfig();
        config.vehicles().setVehiclesFile(globFile(runDirectory, runId, "vehicles"));
        config.network().setInputFile(globFile(runDirectory, runId, "network"));
        config.transit().setTransitScheduleFile(globFile(runDirectory, runId, "transitSchedule"));
        config.transit().setVehiclesFile(globFile(runDirectory, runId, "transitVehicles"));

        config.global().setCoordinateSystem(crs.getInputCRS());
        log.info("Using coordinate system '{}'", config.global().getCoordinateSystem());

        config.plans().setInputFile(null);
        config.parallelEventHandling().setNumberOfThreads(null);
        config.parallelEventHandling().setEstimatedNumberOfEvents(null);
        config.global().setNumberOfThreads(1);

        EmissionsConfigGroup eConfig = ConfigUtils.addOrGetModule(config, EmissionsConfigGroup.class);
        eConfig.setDetailedVsAverageLookupBehavior(lookupBehavior);
        eConfig.setAverageColdEmissionFactorsFile(this.hbefaColdFile.toString());
        eConfig.setAverageWarmEmissionFactorsFile(this.hbefaWarmFile.toString());
//        eConfig.setHbefaRoadTypeSource(HbefaRoadTypeSource.fromLinkAttributes);
        eConfig.setNonScenarioVehicles(NonScenarioVehicles.ignore);

        final String eventsFile = globFile(runDirectory, runId, "events");

        // for SimWrapper
        final String linkEmissionPerMOutputFile = output + runId + ".emissionsPerLinkPerM.csv";

        Scenario scenario = ScenarioUtils.loadScenario(config);

        if (useDefaultRoadTypes) {
            log.info("Using integrated road types");
            addDefaultRoadTypes(scenario.getNetwork());
        }

        log.info("Using vehicle category mapping: {}", vehicleCategories);

        for (VehicleType type : Iterables.concat(
                scenario.getVehicles().getVehicleTypes().values(),
                scenario.getTransitVehicles().getVehicleTypes().values())) {

            HbefaVehicleCategory cat = vehicleCategories.computeIfAbsent(type.getId().toString(), (k) -> {
                log.warn("Vehicle type {} not mapped to a category, using {}", k, HbefaVehicleCategory.NON_HBEFA_VEHICLE);
                return HbefaVehicleCategory.NON_HBEFA_VEHICLE;
            });

            EngineInformation carEngineInformation = type.getEngineInformation();
            VehicleUtils.setHbefaVehicleCategory(carEngineInformation, cat.toString());
            VehicleUtils.setHbefaTechnology(carEngineInformation, "average");
            VehicleUtils.setHbefaSizeClass(carEngineInformation, "average");
            VehicleUtils.setHbefaEmissionsConcept(carEngineInformation, "average");
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

        if (output == null) {
            output = Path.of(eventsFile.replace(".xml", ".emissions.xml"));
            log.info("Writing to output {}", output);
        }

        com.google.inject.Injector injector = Injector.createInjector(config, module);

        EmissionModule emissionModule = injector.getInstance(EmissionModule.class);

        EventWriterXML emissionEventWriter = new EventWriterXML(output.toString());

        Handler aggrHandler = new Handler();

        emissionModule.getEmissionEventsManager().addHandler(emissionEventWriter);
        emissionModule.getEmissionEventsManager().addHandler(aggrHandler);

        // necessary for link emissions [g/m] output
        EmissionsOnLinkHandler emissionsEventHandler = new EmissionsOnLinkHandler();
        eventsManager.addHandler(emissionsEventHandler);

        eventsManager.initProcessing();
        MatsimEventsReader matsimEventsReader = new MatsimEventsReader(eventsManager);
        matsimEventsReader.readFile(eventsFile);
        log.info("Done reading the events file.");
        eventsManager.finishProcessing();
        log.info("Finish processing...");
        log.info("Closing events file...");
        emissionEventWriter.closeFile();

        // writing per link pollutant emissions
        try (CSVPrinter printer = new CSVPrinter(Files.newBufferedWriter(Path.of(output.toString()
                .replace(".xml", ".footprint.csv").replace(".gz", ""))), CSVFormat.DEFAULT)) {
            printer.printRecord("pollutant", "g");
            for (Object2DoubleMap.Entry<Pollutant> e : aggrHandler.pollution.object2DoubleEntrySet()) {
                printer.printRecord(e.getKey(), e.getDoubleValue());
            }
        }
        log.info("Done");
        log.info("Writing (more) output...");

        // writing emissions (per link) per meter
        {
            File file1 = new File(linkEmissionPerMOutputFile);
            BufferedWriter bw1 = new BufferedWriter(new FileWriter(file1));

            bw1.write("linkId");

            for (Pollutant pollutant : Pollutant.values()) {
                bw1.write(";" + pollutant + " [g/m]");
            }
            bw1.newLine();

            Map<Id<Link>, Map<Pollutant, Double>> link2pollutants = emissionsEventHandler.getLink2pollutants();

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
        }

        return 0;
    }

    private void addDefaultRoadTypes(Network network) {
        // network
        new VspHbefaRoadTypeMapping().addHbefaMappings(network);
    }

    private static class Handler implements ColdEmissionEventHandler, WarmEmissionEventHandler {

        private final Object2DoubleMap<Pollutant> pollution = new Object2DoubleOpenHashMap<>();

        @Override
        public void handleEvent(ColdEmissionEvent event) {

            for (Map.Entry<Pollutant, Double> pollutant : event.getColdEmissions().entrySet()) {
                pollution.mergeDouble(pollutant.getKey(), pollutant.getValue(), Double::sum);
            }
        }

        @Override
        public void handleEvent(WarmEmissionEvent event) {

            for (Map.Entry<Pollutant, Double> pollutant : event.getWarmEmissions().entrySet()) {
                pollution.mergeDouble(pollutant.getKey(), pollutant.getValue(), Double::sum);
            }
        }
    }

}

