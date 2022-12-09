package org.matsim.run.prepare;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
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
        description = "Prepare network with various policy options."
)
public class PrepareNetwork implements MATSimAppCommand {
    @CommandLine.Option(names = "--network", description = "Path to network file", required = true)
    private String networkFile;

    @CommandLine.Option(names = "--output", description = "Output path of the prepared network", required = true)
    private String outputPath;


    @CommandLine.Mixin
    private NetworkOptions options;

    public static void main(String[] args) {
        new PrepareNetwork().execute(args);
    }

    @Override
    public Integer call() throws Exception {

        Network network = NetworkUtils.readNetwork(networkFile);
        options.prepare(network);
        NetworkUtils.writeNetwork(network, outputPath);

        return 0;
    }

    static void prepareDRT(Network network, ShpOptions shp) {

        boolean cityAreaNetwork = false;
        String modes = "drt";

        Set<String> modesToAdd = new HashSet<>(Arrays.asList(modes.split(",")));
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

        } else {
            MultimodalNetworkCleaner multimodalNetworkCleaner = new MultimodalNetworkCleaner(network);
            multimodalNetworkCleaner.run(modesToAdd);
        }

    }

    /**
     * Adapt network to one or more car-free zones. Therefore, a shape file of the wished car-free area is needed.
     */
    static void prepareCarFree(Network network, ShpOptions shp) {

        Set<String> modes = Set.of(TransportMode.car);

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

    }

    static void prepareParking(Network network, ShpOptions shp) {


    }

}
