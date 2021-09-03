package org.matsim.run;

import org.junit.jupiter.api.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class RunLeipzigIntegrationTest {

	private static final String URL = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/leipzig/leipzig-v1/input/";

	@Test
	public void run1pct() {

		// https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/leipzig/leipzig-v1/input/leipzig-v1.0-1pct.plans.xml.gz

		Path output = Path.of("output/it-1pct");

		Config config = ConfigUtils.loadConfig("scenarios/input/leipzig-v1.0-25pct.config.xml");

		// Change input paths
		config.plans().setInputFile(URL + config.plans().getInputFile());
		config.transit().setTransitScheduleFile(URL + config.transit().getTransitScheduleFile());
		config.transit().setVehiclesFile(URL + config.transit().getVehiclesFile());
		config.network().setInputFile(URL + config.network().getInputFile());

		Controler controler = RunLeipzigScenario.prepare(RunLeipzigScenario.class, config,
				"run", "--1pct"
		);

		config.controler().setLastIteration(1);
		config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		config.controler().setOutputDirectory(output.toString());

		controler.run();

		assertThat(output)
				.exists()
				.isNotEmptyDirectory();

	}
}
