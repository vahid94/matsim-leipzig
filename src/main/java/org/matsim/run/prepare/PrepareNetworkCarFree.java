package org.matsim.run.prepare;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.ShpOptions;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.MultimodalNetworkCleaner;
import org.matsim.core.utils.geometry.geotools.MGC;
import picocli.CommandLine;

import java.util.*;

@CommandLine.Command(
        name = "car-free-network",
        description = "Adapt network to one or more car-free zones. Therefore a shape file of the wished car-free area is needed. "
)

public class PrepareNetworkCarFree implements MATSimAppCommand {

    private static final Logger log = LogManager.getLogger(PrepareNetworkCarFree.class);

    @CommandLine.Option(names = "--network", description = "Path to network file", required = true)
    private String networkFile;

    @CommandLine.Option(names = "--output", description = "Output path of the prepared network", required = true)
    private String outputPath;

    @CommandLine.Option(names = "--modes", description = "List of modes to remove", defaultValue = TransportMode.car, split = ",", required = true)
    private Set<String> modes;

    @CommandLine.Mixin
    private ShpOptions shp = new ShpOptions();

    @CommandLine.Option(names = "--parking-capacities", description = "Path to csv file containing parking capacity data per link")
    private String inputParkingCapacities;

    public static void main(String[] args) {
        new PrepareNetworkCarFree().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        Network network = NetworkUtils.readNetwork(networkFile);
        Geometry carFreeArea = shp.getGeometry();

        GeometryFactory gf = new GeometryFactory();

        for (Link link : network.getLinks().values()) {

            if (!link.getAllowedModes().contains(TransportMode.car)) {
                continue;
            }

            LineString line = gf.createLineString(new Coordinate[]{
                    MGC.coord2Coordinate(link.getFromNode().getCoord()),
                    MGC.coord2Coordinate(link.getToNode().getCoord())
            });

            boolean isInsideCarFreeZone = line.intersects(carFreeArea);

            if (isInsideCarFreeZone) {
                Set<String> allowedModes = new HashSet<>(link.getAllowedModes());

                for( String mode : modes) {
                    allowedModes.remove(mode);
                }
                link.setAllowedModes(allowedModes);
            }
        }

        MultimodalNetworkCleaner multimodalNetworkCleaner = new MultimodalNetworkCleaner(network);
        modes.forEach(m -> multimodalNetworkCleaner.run(Set.of(m)));

        if(inputParkingCapacities != null) {
            ParkingNetworkWriter writer = new ParkingNetworkWriter(network, inputParkingCapacities);
            writer.addParkingInformationToLinks();
        }

        NetworkUtils.writeNetwork(network, outputPath);

        log.info("Network including a car-free area has been written to {}", outputPath);

        return 0;
    }
}