package org.matsim.run.prepare;

import org.matsim.api.core.v01.network.Network;
import org.matsim.application.options.ShpOptions;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Defines all options and parameters related to network modifications.
 */
public class NetworkOptions {

	@CommandLine.Option(names = "--drt-area", description = "Path to SHP file specifying where DRT mode is allowed")
	private Path drtArea;
	@CommandLine.Option(names = "--car-free-area", description = "Path to SHP file specifying car-free area")
	private Path carFreeArea;
	@CommandLine.Option(names = "--parking-area", description = "Path to SHP file specifying parking area")
	private Path parkingArea;

	/**
	 * Return whether a car free area is defined.
	 */
	public boolean hasCarFreeArea() {
		return isDefined(carFreeArea);
	}


	/**
	 * Prepare network with given options.
	 */
	public void prepare(Network network) {

		if (isDefined(drtArea)) {
			if (!Files.exists(drtArea))
				throw new IllegalArgumentException("Path to drt area not found: " + drtArea);

			PrepareNetwork.prepareDRT(network, new ShpOptions(drtArea, null, null));
		}

		if (isDefined(carFreeArea)) {
			if (!Files.exists(carFreeArea))
				throw new IllegalArgumentException("Path to car free area not found: " + carFreeArea);

			PrepareNetwork.prepareCarFree(network, new ShpOptions(carFreeArea, null, null));
		}

		if (isDefined(parkingArea)) {
			if (!Files.exists(parkingArea))
				throw new IllegalArgumentException("Path to parking area not found: " + parkingArea);

			PrepareNetwork.prepareParking(network, new ShpOptions(parkingArea, null, null));
		}


	}

	private boolean isDefined(Path p) {
		return p != null && !carFreeArea.toString().isBlank();
	}

}
