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

package analysis.emissions;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.CrsOptions;
import org.matsim.contrib.emissions.EmissionModule;
import org.matsim.contrib.emissions.HbefaVehicleCategory;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.contrib.emissions.VspHbefaRoadTypeMapping;
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
import org.matsim.vehicles.EngineInformation;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import picocli.CommandLine;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.matsim.application.ApplicationUtils.globFile;


/**
 * This analysis class requires two parameters as arguments,
 * (1) the root/working directory, e.g. "Users/rgraebe/IdeaProjects/matsim-leipzig/";
 * (2) the path to your (SVN) folder containing the exported HBEFA files,  e.g. "/Users/rgraebe/shared-svn/".
 *
 * @author rgraebe
*/

public class RunOfflineAirPollutionAnalysisByVehicleCategory { // todo: implements MATSimAppCommand (rakow?)

	private static final Logger log = Logger.getLogger(RunOfflineAirPollutionAnalysisByVehicleCategory.class);
	private final String runDirectory;
	private final String runId;
	private final String hbefaWarmFile;
	private final String hbefaColdFile;
	private final String analysisOutputDirectory;
	
	public RunOfflineAirPollutionAnalysisByVehicleCategory(String runDirectory, String runId, String hbefaFileWarm, String hbefaFileCold, String analysisOutputDirectory) {
		this.runDirectory = runDirectory;
		this.runId = runId;
		this.hbefaWarmFile = hbefaFileWarm;
		this.hbefaColdFile = hbefaFileCold;
		
		if (!analysisOutputDirectory.endsWith("/")) analysisOutputDirectory = analysisOutputDirectory + "/";
		this.analysisOutputDirectory = analysisOutputDirectory;
	}
	
	public static void main(String[] args) {
		
		if (args.length == 2) {
			String rootDirectory = args[0];
			String svnDirectory = args[1];
			if (!rootDirectory.endsWith("/")) rootDirectory = rootDirectory + "/";
			if (!svnDirectory.endsWith("/")) svnDirectory = svnDirectory + "/";

			// based on the simulation output available within this project
			final String runDirectory = "output/it-1pct/";
			final String runId = "leipzig-25pct";

			// Currently (05/22), these files are (only) available in the shared-svn
			final String hbefaFileCold = "projects/matsim-germany/hbefa/hbefa-files/v4.1/EFA_ColdStart_Vehcat_2020_Average_withHGVetc.csv";
			final String hbefaFileWarm = "projects/matsim-germany/hbefa/hbefa-files/v4.1/EFA_HOT_Vehcat_2020_Average.csv";

			RunOfflineAirPollutionAnalysisByVehicleCategory analysis = new RunOfflineAirPollutionAnalysisByVehicleCategory(
					rootDirectory + runDirectory,
					runId,
					svnDirectory + hbefaFileWarm,
					svnDirectory + hbefaFileCold,
					rootDirectory + runDirectory + "emission-analysis-hbefa-v4.1");
			try {
				analysis.run();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

		} else {
			throw new RuntimeException("Please set the two (root and input) directory paths. \nCheck the class description for more details. Aborting...");
		}
	}

	void run() throws IOException {
		
		Config config = ConfigUtils.createConfig();
		config.vehicles().setVehiclesFile(runDirectory + runId + ".output_vehicles.xml.gz");
		config.network().setInputFile(runDirectory + runId + ".output_network.xml.gz");
		config.transit().setTransitScheduleFile(runDirectory + runId + ".output_transitSchedule.xml.gz");
		config.transit().setVehiclesFile(runDirectory + runId + ".output_transitVehicles.xml.gz");
		config.global().setCoordinateSystem("EPSG:25832");
		config.plans().setInputFile(null);
		config.parallelEventHandling().setNumberOfThreads(null);
		config.parallelEventHandling().setEstimatedNumberOfEvents(null);
		config.global().setNumberOfThreads(1);
		
		EmissionsConfigGroup eConfig = ConfigUtils.addOrGetModule(config, EmissionsConfigGroup.class);
		eConfig.setDetailedVsAverageLookupBehavior(DetailedVsAverageLookupBehavior.directlyTryAverageTable);
		eConfig.setAverageColdEmissionFactorsFile(this.hbefaColdFile);
		eConfig.setAverageWarmEmissionFactorsFile(this.hbefaWarmFile);
		eConfig.setNonScenarioVehicles(NonScenarioVehicles.ignore);

		// input and outputs of emissions analysis
		final String eventsFile = runDirectory + runId + ".output_events.xml.gz";
		File dir = new File(analysisOutputDirectory);
		if ( !dir.exists() ) { dir.mkdir(); }
		final String emissionEventOutputFile = analysisOutputDirectory + runId + ".emission.events.offline.xml.gz";
		// for SimWrapper
		final String linkEmissionPerMOutputFile = analysisOutputDirectory + runId + ".emissionsPerLinkPerM.csv";

		Scenario scenario = ScenarioUtils.loadScenario(config);
		
		// network
		new VspHbefaRoadTypeMapping().addHbefaMappings(scenario.getNetwork());
		
		// vehicles
		Id<VehicleType> carVehicleTypeId = Id.create("car", VehicleType.class);
		Id<VehicleType> freightVehicleTypeId = Id.create("freight", VehicleType.class);
		
		VehicleType carVehicleType = scenario.getVehicles().getVehicleTypes().get(carVehicleTypeId);
		VehicleType freightVehicleType = scenario.getVehicles().getVehicleTypes().get(freightVehicleTypeId);
		
		EngineInformation carEngineInformation = carVehicleType.getEngineInformation();
		VehicleUtils.setHbefaVehicleCategory( carEngineInformation, HbefaVehicleCategory.PASSENGER_CAR.toString());
		VehicleUtils.setHbefaTechnology( carEngineInformation, "average" );
		VehicleUtils.setHbefaSizeClass( carEngineInformation, "average" );
		VehicleUtils.setHbefaEmissionsConcept( carEngineInformation, "average" );
		
		EngineInformation freightEngineInformation = freightVehicleType.getEngineInformation();
		VehicleUtils.setHbefaVehicleCategory( freightEngineInformation, HbefaVehicleCategory.HEAVY_GOODS_VEHICLE.toString());
		VehicleUtils.setHbefaTechnology( freightEngineInformation, "average" );
		VehicleUtils.setHbefaSizeClass( freightEngineInformation, "average" );
		VehicleUtils.setHbefaEmissionsConcept( freightEngineInformation, "average" );
		
		// public transit vehicles should be considered as non-hbefa vehicles
		for (VehicleType type : scenario.getTransitVehicles().getVehicleTypes().values()) {
			EngineInformation engineInformation = type.getEngineInformation();
			// TODO: Check! Is this a zero emission vehicle?!
			VehicleUtils.setHbefaVehicleCategory( engineInformation, HbefaVehicleCategory.NON_HBEFA_VEHICLE.toString());
			VehicleUtils.setHbefaTechnology( engineInformation, "average" );
			VehicleUtils.setHbefaSizeClass( engineInformation, "average" );
			VehicleUtils.setHbefaEmissionsConcept( engineInformation, "average" );			
		}

		// ignore bikes as they return null and crash when looking for an HbefaVehicleCategory ~rjg 05/22
		VehicleType bikeType = scenario.getVehicles().getVehicleTypes().get(Id.create("bike", VehicleType.class));
		EngineInformation bikeEngineInformation = bikeType.getEngineInformation();
		VehicleUtils.setHbefaVehicleCategory( bikeEngineInformation, HbefaVehicleCategory.NON_HBEFA_VEHICLE.toString() );
		
		// the following is copy paste from the example...
		
        EventsManager eventsManager = EventsUtils.createEventsManager();

		AbstractModule module = new AbstractModule(){
			@Override
			public void install(){
				bind( Scenario.class ).toInstance( scenario );
				bind( EventsManager.class ).toInstance( eventsManager );
				bind( EmissionModule.class ) ;
			}
		};

		com.google.inject.Injector injector = Injector.createInjector(config, module);

        EmissionModule emissionModule = injector.getInstance(EmissionModule.class);

        EventWriterXML emissionEventWriter = new EventWriterXML(emissionEventOutputFile);
        emissionModule.getEmissionEventsManager().addHandler(emissionEventWriter);

		// necessary for link emissions [g/m] output
		EmissionsOnLinkHandler emissionsEventHandler = new EmissionsOnLinkHandler();
		eventsManager.addHandler(emissionsEventHandler);

        eventsManager.initProcessing();
        MatsimEventsReader matsimEventsReader = new MatsimEventsReader(eventsManager);
        matsimEventsReader.readFile(eventsFile);
		log.info("Done reading the events file.");
		log.info("Finish processing...");
		eventsManager.finishProcessing();
		log.info("Closing events file...");
        emissionEventWriter.closeFile();
		log.info("Writing (more) output...");

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
			log.info("Output written to " + linkEmissionPerMOutputFile);
		}
	}

}

