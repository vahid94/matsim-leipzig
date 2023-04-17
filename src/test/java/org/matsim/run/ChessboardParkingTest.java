package org.matsim.run;


import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.examples.ExamplesUtils;
import org.matsim.run.prepare.NetworkOptions;
import org.matsim.testcases.MatsimTestUtils;

import java.io.IOException;

public class ChessboardParkingTest {
	@Rule
	public MatsimTestUtils utils = new MatsimTestUtils();


	@Test
	public final void runParkingChessboardTest() throws IOException {
		String inputPath = String.valueOf(ExamplesUtils.getTestScenarioURL("chessboard"));

		Config config = ConfigUtils.loadConfig(inputPath + "/config.xml");
		Scenario scenario = ScenarioUtils.loadScenario(config);
		NetworkOptions networkOptions = new NetworkOptions();

		// help
		networkOptions.prepare(scenario.getNetwork());

	}
}
