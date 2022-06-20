package org.matsim.run.prepare;

import com.google.common.collect.Sets;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.ShpOptions;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.MultimodalNetworkCleaner;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.opengis.feature.simple.SimpleFeature;
import picocli.CommandLine;

import java.util.*;

@CommandLine.Command(
        name = "network",
        description = "Adapt network to one or more car-free zones. Therefore a shape file of the wished car-free area is needed. "
)

public class PrepareNetworkCarFree implements MATSimAppCommand {

    @CommandLine.Option(names = "--network", description = "Path to network file", required = true)
    private String networkFile;

    @CommandLine.Mixin()
    private ShpOptions shp = new ShpOptions();

    @CommandLine.Option(names = "--output", description = "Output path of the prepared network", required = true)
    private String outputPath;

    @CommandLine.Option(names = "--modes", description = "List of modes to remove", required = true, defaultValue = TransportMode.car)
    private String modes;

    public static void main(String[] args) {
        new PrepareNetworkCarFree().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        Network network = NetworkUtils.readNetwork(networkFile);
        Geometry carFreeArea = null;
        List<SimpleFeature> features = shp.readFeatures();
        for (SimpleFeature feature : features) {
            if (carFreeArea == null) {
                carFreeArea = (Geometry) feature.getDefaultGeometry();
            } else {
                carFreeArea = carFreeArea.union((Geometry) feature.getDefaultGeometry());
            }
        }

        String[] modesToRemove = modes.split(",");

        for (Link link : network.getLinks().values()) {

            if (!link.getAllowedModes().contains("car")){
                continue;
            }

            boolean isInsideCarFreeZone = MGC.coord2Point(link.getFromNode().getCoord()).within(carFreeArea) ||
                    MGC.coord2Point(link.getToNode().getCoord()).within(carFreeArea);

            if (isInsideCarFreeZone) {
                Set<String> allowedModes = new HashSet<>(link.getAllowedModes());

                for( String mode : modesToRemove) {
                    allowedModes.remove(mode);
                }
                link.setAllowedModes(allowedModes);
            }
        }
        MultimodalNetworkCleaner multimodalNetworkCleaner = new MultimodalNetworkCleaner(network);
        multimodalNetworkCleaner.run(Sets.newHashSet(modesToRemove));


        NetworkUtils.writeNetwork(network, outputPath);
        System.out.println("Network including a car-free area has been written to " + outputPath);

        return 0;
    }

}