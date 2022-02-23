package org.matsim.run.prepare;

import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.MATSimApplication;
import org.matsim.application.options.ShpOptions;
import org.matsim.application.prepare.network.CleanNetwork;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.MultimodalNetworkCleaner;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.opengis.feature.simple.SimpleFeature;
import picocli.CommandLine;

import java.util.*;

@CommandLine.Command(
        name = "network",
        description = "Option1: Add allowed mode DRT and / or AV to network. Therefore a shape file of the wished service area is needed. " +
                "Option2: Create network of city area only. A shape file of the city area is needed here."
)
public class PrepareNetwork implements MATSimAppCommand {
    @CommandLine.Option(names = "--network", description = "Path to network file", required = true)
    private String networkFile;

    @CommandLine.Mixin()
    private ShpOptions shp = new ShpOptions();

    @CommandLine.Option(names = "--output", description = "Output path of the prepared network", required = true)
    private String outputPath;

    @CommandLine.Option(names = "--modes", description = "List of modes to add", required = true, defaultValue = TransportMode.drt)
    private Set<String> modesToAdd;

    @CommandLine.Option(names = "--cityAreaNetwork", description = "Cut out network of city area only", required = false)
    private boolean cityAreaNetwork;

    public static void main(String[] args) {
        new PrepareNetwork().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        Geometry drtOperationArea = null;
        Geometry avOperationArea = null;
        Geometry cityArea = null;
        List<SimpleFeature> features = shp.readFeatures();
        for (SimpleFeature feature : features) {
            if (!cityAreaNetwork) {
                if (feature.getAttribute("mode").equals("drt")) {
                    if (drtOperationArea == null) {
                        drtOperationArea = (Geometry) feature.getDefaultGeometry();
                    } else {
                        drtOperationArea = drtOperationArea.union((Geometry) feature.getDefaultGeometry());
                    }
                } else {
                    drtOperationArea = avOperationArea.getFactory().createPoint();
                    cityArea = drtOperationArea;
                }

                if (feature.getAttribute("mode").equals("av")) {
                    if (avOperationArea == null) {
                        avOperationArea = (Geometry) feature.getDefaultGeometry();
                    } else {
                        avOperationArea = avOperationArea.union((Geometry) feature.getDefaultGeometry());
                    }
                } else {
                    avOperationArea = drtOperationArea.getFactory().createPoint();
                    cityArea = avOperationArea;
                    System.out.println(avOperationArea);
                }

            } else {
                if (cityArea == null) {
                    cityArea = (Geometry) feature.getDefaultGeometry();
                } else {
                    cityArea = cityArea.union((Geometry) feature.getDefaultGeometry());
                }
                drtOperationArea = avOperationArea = cityArea.getFactory().createPoint();
            }
        }

        Network network = NetworkUtils.readNetwork(networkFile);

        Map<Id<Node>, Node> cityNodes = new HashMap<>();
        Map<Id<Link>, Link> cityLinks = new HashMap<>();

        for (Link link : network.getLinks().values()) {
            if(!cityAreaNetwork) {
                if (!link.getAllowedModes().contains("car")){
                    continue;
                }
            } else {
                if (!(link.getAllowedModes().contains("car") || link.getAllowedModes().contains("bike"))){
                    continue;
                }
            }

            boolean isDrtAllowed = MGC.coord2Point(link.getFromNode().getCoord()).within(drtOperationArea) &&
                    MGC.coord2Point(link.getToNode().getCoord()).within(drtOperationArea);
            boolean isAvAllowed = MGC.coord2Point(link.getFromNode().getCoord()).within(avOperationArea) &&
                    MGC.coord2Point(link.getToNode().getCoord()).within(avOperationArea);
            boolean isInsideCityArea = MGC.coord2Point(link.getFromNode().getCoord()).within(cityArea) &&
                    MGC.coord2Point(link.getToNode().getCoord()).within(cityArea);


            if(!cityAreaNetwork) {
                if (isDrtAllowed) {
                    Set<String> allowedModes = new HashSet<>(link.getAllowedModes());
                    allowedModes.addAll(modesToAdd);
                    link.setAllowedModes(allowedModes);
                }

                if (isAvAllowed) {
                    Set<String> allowedModes = new HashSet<>(link.getAllowedModes());
                    allowedModes.addAll(modesToAdd);
                    link.setAllowedModes(allowedModes);
                }
            } else {
                if(isInsideCityArea) {
                    cityNodes.putIfAbsent(link.getFromNode().getId(), link.getFromNode());
                    cityNodes.putIfAbsent(link.getToNode().getId(), link.getToNode());

                    cityLinks.putIfAbsent(link.getId(), link);
                }
            }
        }

        if (cityAreaNetwork) {
            Network cityNetwork = NetworkUtils.createNetwork();
            cityNodes.values().forEach(cityNetwork::addNode);
            cityLinks.values().forEach(cityNetwork::addLink);

            NetworkCleaner networkCleaner = new NetworkCleaner();
            networkCleaner.run(cityNetwork);


            NetworkUtils.writeNetwork(cityNetwork, outputPath);
            System.out.println("Network of city area has been written to " + outputPath +
                    ". \n If you wish to write a network with drt / av as allowed transport modes please check your command line options!");
        } else {
            MultimodalNetworkCleaner multimodalNetworkCleaner = new MultimodalNetworkCleaner(network);
            multimodalNetworkCleaner.run(modesToAdd);
            NetworkUtils.writeNetwork(network, outputPath);

            System.out.println("The modes " + modesToAdd + " were added to the network. File has been written to " + outputPath +
                    ". \n If you wish to write a city area network please check your command line options!");
        }
        return 0;
    }
}
