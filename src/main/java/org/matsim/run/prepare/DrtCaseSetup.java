package org.matsim.run.prepare;

import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.routing.pt.raptor.RaptorIntermodalAccessEgress;
import com.google.common.collect.ImmutableSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.application.options.ShpOptions;
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
import org.matsim.core.config.groups.SubtourModeChoiceConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.extensions.pt.fare.intermodalTripFareCompensator.IntermodalTripFareCompensatorConfigGroup;
import org.matsim.extensions.pt.fare.intermodalTripFareCompensator.IntermodalTripFareCompensatorsConfigGroup;
import org.matsim.extensions.pt.fare.intermodalTripFareCompensator.IntermodalTripFareCompensatorsModule;
import org.matsim.extensions.pt.routing.EnhancedRaptorIntermodalAccessEgress;
import org.matsim.extensions.pt.routing.ptRoutingModes.PtIntermodalRoutingModesConfigGroup;
import org.matsim.extensions.pt.routing.ptRoutingModes.PtIntermodalRoutingModesModule;
import org.opengis.feature.simple.SimpleFeature;

import java.nio.file.Path;
import java.util.*;

/**
 * This class configures drt config, stops, transitStopsFile and vehicles regarding enum DrtCase of RunLeipzigScenario.
 */
public final class DrtCaseSetup {

	private static final Logger log = LogManager.getLogger(DrtCaseSetup.class);

	/**
	 * Defines if drt is modelled at all (none), with 2 separate modes (twoSeparateServiceAreas) or with 1 single drt mode (oneServiceArea).
	 * As this class is only triggered if a shp of the drt service area was provided, none is inactive for now
	 */
	public enum DrtCase {/*none,*/ twoSeparateServiceAreas, oneServiceArea}

	/**
	 * Defines if intermodality between drt and pt is modelled or not.
	 */
	public enum PtDrtIntermodality {drtAndPtSeparateFromEachOther, drtAsAccessEgressForPt}

	//TODO configure drt config, stops, transitstopsFile and vehicles re enum DrtCase -> 3 cases

	private DrtCaseSetup(){}

	//TODO I am pretty sure this does not work because we cannot download the file from URL using the path..
	//Is there a workaround?
	//If it does work, we have to move the file to public svn instead of shared svn
	private static final ShpOptions flexaArea2021 = new ShpOptions(Path.of(
			"https://svn.vsp.tu-berlin.de/repos/shared-svn/projects/NaMAV/data/shapefiles/leipzig_flexa_service_area_2021/leipzig_flexa_service_area_2021.shp"),
			null, null);

	static Set<String> drtModes = new HashSet<>();


	public static void prepareConfig(Config config, DrtCase drtCase, ShpOptions drtArea, String version) {

		MultiModeDrtConfigGroup multiModeDrtConfigGroup = ConfigUtils.addOrGetModule(config, MultiModeDrtConfigGroup.class);
		ConfigUtils.addOrGetModule(config, DvrpConfigGroup.class);
		DrtConfigs.adjustMultiModeDrtConfig(multiModeDrtConfigGroup, config.planCalcScore(), config.plansCalcRoute());

		//set fare params; flexa has the same prices as leipzig PT: Values taken out of LeipzigPtFareModule -sm0522
		Double ptBaseFare = 2.4710702921120262;
		Double ptDistanceFare = 0.00017987993018495408;

		DrtFareParams drtFareParams = new DrtFareParams();
		drtFareParams.baseFare = ptBaseFare;
		drtFareParams.distanceFare_m = ptDistanceFare;
		drtFareParams.timeFare_h = 0.;
		drtFareParams.dailySubscriptionFee = 0.;

		CreateDrtStopsFromNetwork drtStopsCreator = new CreateDrtStopsFromNetwork();

		switch (drtCase) {
			case twoSeparateServiceAreas -> {
				//flexa case with 2 separate drt bubbles (north and southeast) -> 2 separate drt modes
				multiModeDrtConfigGroup.getModalElements().forEach(drtConfigGroup -> {
					drtConfigGroup.addParameterSet(drtFareParams);
					DrtConfigs.adjustDrtConfig(drtConfigGroup, config.planCalcScore(), config.plansCalcRoute());
					drtModes.add(drtConfigGroup.getMode());

					//TODO: stops file will be deleted if saved in output folder.. mb save it in dir of config? how do we get the configpath?
					drtStopsCreator.execute("--network", config.network().getInputFile(),
							"--mode", drtConfigGroup.getMode(), "--shp", flexaArea2021.getShapeFile().toString(), "--modeFilteredNetwork",
							"--output-folder", config.controler().getOutputDirectory());

					//naming pattern comes from @DrtStopsWriter line 81. Should be ok to hard code it here. -sme0523
					drtConfigGroup.transitStopFile = config.controler().getOutputDirectory() +
							"/leipzig-v" + version + "-" + drtConfigGroup.getMode() + "-stops.xml";
				});
			}

			case oneServiceArea -> {
				//"normal" drt, modelled as one single drt mode
				multiModeDrtConfigGroup.getModalElements().forEach(drtConfigGroup -> {
					drtConfigGroup.addParameterSet(drtFareParams);
					DrtConfigs.adjustDrtConfig(drtConfigGroup, config.planCalcScore(), config.plansCalcRoute());
					drtModes.add(drtConfigGroup.getMode());

					//TODO: stops file will be deleted if saved in output folder.. mb save it in dir of config? how do we get the configpath?
					drtStopsCreator.execute("--network", config.network().getInputFile(),
							"--mode", drtConfigGroup.getMode(), "--shp", drtArea.getShapeFile().toString(), "--modeFilteredNetwork",
							"--output-folder", config.controler().getOutputDirectory());

					//naming pattern comes from @DrtStopsWriter line 81. Should be ok to hard code it here. -sme0523
					drtConfigGroup.transitStopFile = config.controler().getOutputDirectory() +
							"/leipzig-v" + version + "-" + drtConfigGroup.getMode() + "-stops.xml";
				});

			}
			default -> throw new IllegalStateException("Unexpected value: " + (drtCase));
		}
	}

	public static void prepareScenario (Scenario scenario, DrtCase drtCase, ShpOptions drtArea, PtDrtIntermodality ptDrtIntermodality) {

		scenario.getPopulation().getFactory().getRouteFactories().setRouteFactory(DrtRoute.class, new DrtRouteFactory());
		// (matsim core does not know about DRT routes. This makes it possible to read them before the controler is there.)

		String drtMode = null;
		Integer noVehicles = null;

		switch (drtCase) {
			case twoSeparateServiceAreas -> {
				//flexa case with 2 separate drt bubbles (north and southeast) -> 2 separate drt modes

				for (SimpleFeature feature : flexaArea2021.readFeatures()) {
					if (feature.getAttribute("Name").equals("Nord")) {
						drtMode = "drtNorth";
						noVehicles = 3;

					} else if (feature.getAttribute("Name").equals("Suedost")) {
						drtMode = "drtSoutheast";
						noVehicles = 2;
					}

					new LeipzigDrtVehicleCreator().createDrtVehiclesForSingleArea(scenario.getVehicles(), scenario.getNetwork(),
							feature, noVehicles, drtMode);

					new CreateDrtStopsFromNetwork().processNetworkForStopCreation(scenario.getNetwork(),
							false, (Geometry) feature.getDefaultGeometry(), "./" + drtMode + "_stops.csv");
				}
			}

			case oneServiceArea -> {
				//"normal" drt, modelled as one single drt mode

				//TODO make the 400 configurable??? -sme0723
				new LeipzigDrtVehicleCreator().createDrtVehicles(scenario.getVehicles(), scenario.getNetwork(),
						drtArea, 400);
				new CreateDrtStopsFromNetwork().processNetworkForStopCreation(scenario.getNetwork(),
						false, drtArea.getGeometry(), "./drt_stops.csv");

			}
			default -> throw new IllegalStateException("Unexpected value: " + (drtCase));
		}
	}

	public static void prepareControler(Controler controler, DrtCase drtCase, ShpOptions drtArea, PtDrtIntermodality ptDrtIntermodality) {

		MultiModeDrtConfigGroup multiModeDrtConfigGroup = ConfigUtils.addOrGetModule(controler.getConfig(), MultiModeDrtConfigGroup.class);
		controler.addOverridingModule(new DvrpModule());
		controler.addOverridingModule(new MultiModeDrtModule());
		controler.configureQSimComponents(DvrpQSimComponents.activateAllModes(multiModeDrtConfigGroup));

		switch (drtCase) {
			case twoSeparateServiceAreas -> {
				//flexa case with 2 separate drt bubbles (north and southeast) -> 2 separate drt modes

				//if intermodality between pt and drt -> only railways are tagged as intermodal stations (this is how it is handled in reality) -sme0723
				if (ptDrtIntermodality.equals(PtDrtIntermodality.drtAsAccessEgressForPt)) {
					preparePtDrtIntermodality(controler, flexaArea2021, true);
				}

			}

			case oneServiceArea -> {
				//"normal" drt, modelled as one single drt mode

				if (ptDrtIntermodality.equals(PtDrtIntermodality.drtAsAccessEgressForPt)) {
					preparePtDrtIntermodality(controler, drtArea, false);
				}

			}
			default -> throw new IllegalStateException("Unexpected value: " + (drtCase));
		}

	}

	private static void preparePtDrtIntermodality(Controler controler, ShpOptions shp, boolean railwaysOnly) {

		new PrepareTransitSchedule().prepareDrtIntermodality(controler.getScenario().getTransitSchedule(), shp, railwaysOnly);

		prepareDrtFareCompensation(controler, drtModes,
				ConfigUtils.addOrGetModule(controler.getConfig(), MultiModeDrtConfigGroup.class)
						.getModalElements().stream().findFirst().get().getDrtFareParams().get().baseFare);
	}

	private static void prepareDrtFareCompensation(Controler controler, Set<String> nonPtModes, Double ptBaseFare) {
		IntermodalTripFareCompensatorsConfigGroup intermodalTripFareCompensatorsConfigGroup =
				ConfigUtils.addOrGetModule(controler.getConfig(), IntermodalTripFareCompensatorsConfigGroup.class);

		IntermodalTripFareCompensatorConfigGroup drtFareCompensator = new IntermodalTripFareCompensatorConfigGroup();
		drtFareCompensator.setCompensationCondition(IntermodalTripFareCompensatorConfigGroup.CompensationCondition.PtModeUsedAnywhereInTheDay);

		//Flexa is integrated into pt system, so users only pay once
		drtFareCompensator.setCompensationMoneyPerTrip(ptBaseFare);
		drtFareCompensator.setNonPtModes(ImmutableSet.copyOf(nonPtModes));

		intermodalTripFareCompensatorsConfigGroup.addParameterSet(drtFareCompensator);
		controler.addOverridingModule(new IntermodalTripFareCompensatorsModule());

		//for intermodality between pt and drt the following modules have to be installed and configured
		String artificialPtMode = "pt_w_drt_allowed";
		PtIntermodalRoutingModesConfigGroup ptIntermodalRoutingModesConfig = ConfigUtils.addOrGetModule(controler.getConfig(), PtIntermodalRoutingModesConfigGroup.class);
		PtIntermodalRoutingModesConfigGroup.PtIntermodalRoutingModeParameterSet ptIntermodalRoutingModesParamSet
				= new PtIntermodalRoutingModesConfigGroup.PtIntermodalRoutingModeParameterSet();

		ptIntermodalRoutingModesParamSet.setDelegateMode(TransportMode.pt);
		ptIntermodalRoutingModesParamSet.setRoutingMode(artificialPtMode);

		PtIntermodalRoutingModesConfigGroup.PersonAttribute2ValuePair personAttrParamSet
				= new PtIntermodalRoutingModesConfigGroup.PersonAttribute2ValuePair();
		personAttrParamSet.setPersonFilterAttribute("canUseDrt");
		personAttrParamSet.setPersonFilterValue("true");
		ptIntermodalRoutingModesParamSet.addPersonAttribute2ValuePair(personAttrParamSet);

		ptIntermodalRoutingModesConfig.addParameterSet(ptIntermodalRoutingModesParamSet);

		controler.addOverridingModule(new PtIntermodalRoutingModesModule());

		//SRRConfigGroup needs to have the same personFilterAttr and Value as PtIntermodalRoutingModesConfigGroup
		SwissRailRaptorConfigGroup ptConfig = ConfigUtils.addOrGetModule(controler.getConfig(), SwissRailRaptorConfigGroup.class);
		for (SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet paramSet : ptConfig.getIntermodalAccessEgressParameterSets()) {
			if (paramSet.getMode().contains("drt")) {
				paramSet.setPersonFilterAttribute("canUseDrt");
				paramSet.setPersonFilterValue("true");
			}
		}

		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				bind(RaptorIntermodalAccessEgress.class).to(EnhancedRaptorIntermodalAccessEgress.class);
			}
		});

		//finally the new pt mode has to be added to subtourModeChoice
		SubtourModeChoiceConfigGroup modeChoiceConfigGroup = ConfigUtils.addOrGetModule(controler.getConfig(), SubtourModeChoiceConfigGroup.class);
		List<String> modes = new ArrayList<>();
		Collections.addAll(modes, modeChoiceConfigGroup.getModes());
		modes.add(artificialPtMode);
		modeChoiceConfigGroup.setModes(modes.toArray(new String[0]));
	}



}
