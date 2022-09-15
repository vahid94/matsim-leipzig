package org.matsim.run.prepare;

import com.opencsv.CSVWriter;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.ShpOptions;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.fleet.DvrpVehicleSpecification;
import org.matsim.contrib.dvrp.fleet.FleetWriter;
import org.matsim.contrib.dvrp.fleet.ImmutableDvrpVehicleSpecification;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.opengis.feature.simple.SimpleFeature;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@CommandLine.Command(
        name = "create-drt-vehicles",
        description = "Writes drt vehicles file"
)

public class LeipzigDrtVehicleCreator implements MATSimAppCommand {

    private final Random random = MatsimRandom.getRandom();
    List<DvrpVehicleSpecification> vehicles = new ArrayList<>();

    @CommandLine.Mixin
    private ShpOptions shp = new ShpOptions();

    @CommandLine.Option(names = "--network", description = "network file", required = true)
    private String network;

    @CommandLine.Option(names = "--drt-mode", description = "network mode for which the vehicle fleet is created", defaultValue = "drt")
    private String drtMode;

    @CommandLine.Option(names = "--no-vehicles", description = "no of vehicles per service area to create", required = true)
    private int noVehiclesPerArea;

    @CommandLine.Option(names = "--seats", description = "no of seats per vehicle", defaultValue = "6")
    private int seats;

    @CommandLine.Option(names = "--service-start-time", description = "start of vehicle service time in seconds", defaultValue = "18000")
    private int serviceStartTime;

    @CommandLine.Option(names = "--service-end-time", description = "end of vehicle service time in seconds", defaultValue = "86400")
    private int serviceEndTime;

    @CommandLine.Option(names = "--output", description = "path to output file. Use / instead of backslash.", required = true)
    private String outputFile;

    public static void main(String[] args) throws IOException { new LeipzigDrtVehicleCreator().execute(args); }

    @Override
    public Integer call() throws Exception {

        Config config = ConfigUtils.createConfig();
        config.network().setInputFile(network);
        Scenario scenario = ScenarioUtils.loadScenario(config);
        Network network = scenario.getNetwork();

        Network drtNetwork = NetworkUtils.createNetwork();
        Set<String> modes = new HashSet<>();
        modes.add(drtMode);

        TransportModeNetworkFilter filter = new TransportModeNetworkFilter(network);
        filter.filter(drtNetwork, modes);

        List<SimpleFeature> serviceAreas = shp.readFeatures();

        for(SimpleFeature serviceArea : serviceAreas) {
            createVehiclesByRandomPointInShape(serviceArea, drtNetwork, noVehiclesPerArea, seats, serviceStartTime,
                    serviceEndTime, serviceAreas.indexOf(serviceArea));
        }

        //write files
        new FleetWriter(vehicles.stream()).write(outputFile);
        writeVehStartPositionsCSV(drtNetwork, outputFile);

        return 0;
    }

    private void createVehiclesByRandomPointInShape(SimpleFeature feature, Network network, int noVehiclesPerArea, int seats,
                                                    int serviceStartTime, int serviceEndTime, int serviceAreaCount) {
        Geometry geometry = (Geometry) feature.getDefaultGeometry();

        for (int i = 0; i < noVehiclesPerArea; i++) {
            Link link = null;

            while(link == null) {
                Point randomPoint = getRandomPointInFeature(random, geometry);
                link = NetworkUtils.getNearestLinkExactly(network, MGC.point2Coord(randomPoint));

                if(MGC.coord2Point(link.getFromNode().getCoord()).within(geometry) &&
                        MGC.coord2Point(link.getToNode().getCoord()).within(geometry)) {

                } else {
                    link = null;
                }
            }
            vehicles.add(ImmutableDvrpVehicleSpecification.newBuilder()
                    .id(Id.create("drt" + serviceAreaCount + i, DvrpVehicle.class))
                    .startLinkId(link.getId())
                    .capacity(seats)
                    .serviceBeginTime(Math.round(serviceStartTime))
                    .serviceEndTime(Math.round(serviceEndTime))
                    .build());
        }
    }

    //copied from BerlinShpUtils -sm0922
    private static Point getRandomPointInFeature(Random rnd, Geometry g) {
        Point p = null;
        double x, y;
        do {
            x = g.getEnvelopeInternal().getMinX() + rnd.nextDouble()
                    * (g.getEnvelopeInternal().getMaxX() - g.getEnvelopeInternal().getMinX());
            y = g.getEnvelopeInternal().getMinY() + rnd.nextDouble()
                    * (g.getEnvelopeInternal().getMaxY() - g.getEnvelopeInternal().getMinY());
            p = MGC.xy2Point(x, y);
        }
        while (!g.contains(p));
        return p;
    }

    //copied and adapted from matsim-berlin DrtVehicleCreator -sm0922
    private void writeVehStartPositionsCSV(Network drtNetwork, String outputFile) {
        Map<Id<Link>, Long> linkId2NrVeh = vehicles.stream().
                map(veh -> veh.getStartLinkId()).
                collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        try {
            CSVWriter writer = new CSVWriter(Files.newBufferedWriter(Paths.get(outputFile + "_startPositions.csv")), ';', '"', '"', "\n");
            writer.writeNext(new String[]{"link", "x", "y", "drtVehicles"}, false);
            linkId2NrVeh.forEach( (linkId, numberVeh) -> {
                Coord coord = drtNetwork.getLinks().get(linkId).getCoord();
                double x = coord.getX();
                double y = coord.getY();
                writer.writeNext(new String[]{linkId.toString(), "" + x, "" + y, "" + numberVeh}, false);
            });

            writer.close();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }
}
