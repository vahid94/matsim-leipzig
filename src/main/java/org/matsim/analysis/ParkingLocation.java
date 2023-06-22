package org.matsim.analysis;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.application.MATSimAppCommand;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.vehicles.Vehicle;
import picocli.CommandLine;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.matsim.application.ApplicationUtils.globFile;

public class ParkingLocation implements MATSimAppCommand {

	@CommandLine.Option(names = "--directory", description = "path to matsim output directory", required = true)
	private Path directory;

	public static void main (String args []) {
		new ParkingLocation().execute(args);
	}

	@Override
	public Integer call() throws Exception {
		Path eventsPath = globFile(directory, "*output_events.*");
		Path networkPath = globFile(directory, "*output_network.*");
		EventsManager manager = EventsUtils.createEventsManager();
		Network network = NetworkUtils.readNetwork(String.valueOf(networkPath));
		List<ParkingData> listOfParkingActivities = new ArrayList<>();
		manager.addHandler(new ParkingActivites(listOfParkingActivities, network));
		manager.initProcessing();
		MatsimEventsReader matsimEventsReader = new MatsimEventsReader(manager);
		matsimEventsReader.readFile(eventsPath.toString());
		manager.finishProcessing();
		writeResults(directory, listOfParkingActivities);

		return null;
	}


	private static class ParkingActivites implements ActivityStartEventHandler {
		private final List<ParkingData> listOfParkingData;
		private final Network network;

		ParkingActivites(List<ParkingData> listOfParkingData, Network network) {
			this.listOfParkingData = listOfParkingData;
			this.network = network;
		}

		@Override
		public void handleEvent(ActivityStartEvent activityStartEvent) {
			if (activityStartEvent.getActType().equals("parking interaction")) {
				Link l =  network.getLinks().get(activityStartEvent.getLinkId());
				ParkingData pd = new ParkingData(l.getCoord(), activityStartEvent.getLinkId());
				listOfParkingData.add(pd);
			}
		}
	}

	private static void writeResults(Path outputFolder, List<ParkingData> listOfParkingData) throws IOException {
		BufferedWriter writer = IOUtils.getBufferedWriter(outputFolder.toString() + "/parkingActivities.tsv");
		writer.write("x" + "\t" + "y" + "\t"  + "linkId" );
		writer.newLine();
		for (int i = 0; i < listOfParkingData.size(); i++) {
			ParkingData pd = listOfParkingData.get(i);
			writer.write(pd.coord.getX() + "\t" + pd.coord.getY() + "\t" + pd.linkId);
			writer.newLine();
		}
		writer.close();

		Map<ParkingData, Integer> duplicateCountMap = listOfParkingData
				.stream()
				.collect(
						Collectors.toMap(Function.identity(), company -> 1, Math::addExact)
				);

		BufferedWriter writerWithCounts = IOUtils.getBufferedWriter(outputFolder.toString() + "/parkingActivitiesWithCount.tsv");

		writerWithCounts.write(("x" + "\t" + "y" + "\t"  + "linkId" + "\t" + "count"));
		writerWithCounts.newLine();
		for(ParkingData parkingData: duplicateCountMap.keySet()) {
			writerWithCounts.write( parkingData.coord.getX() + "\t" + parkingData.coord.getY() +"\t"
					+ parkingData.linkId + "\t" + duplicateCountMap.get(parkingData));
			writerWithCounts.newLine();
		}
		writerWithCounts.close();
	}

	record ParkingData (Coord coord, Id<Link> linkId) {}
}
