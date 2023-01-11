package org.matsim.run.prepare;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.utils.objectattributes.attributable.Attributes;
import playground.vsp.simpleParkingCostHandler.ParkingCostConfigGroup;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ParkingNetworkWriter {

    private static final Logger log = LogManager.getLogger(ParkingNetworkWriter.class);

    Network network;
    Path inputParkingCapacities;
    private static int adaptedLinksCount = 0;
    private static int networkLinksCount = 0;
    private static double firstHourParkingCost;
    private static double extraHourParkingCost;

    ParkingNetworkWriter(Network network, Path inputParkingCapacities, Double firstHourParkingCost, Double extraHourParkingCost) {
        this.network = network;
        this.inputParkingCapacities = inputParkingCapacities;
        this.firstHourParkingCost = firstHourParkingCost;
        this.extraHourParkingCost = extraHourParkingCost;
    }

    public void addParkingInformationToLinks() {
        Map<String, String> linkParkingCapacities = getLinkParkingCapacities();

        for(Link link : network.getLinks().values()) {
            if(link.getId().toString().contains("pt_")) {
                continue;
            }
            networkLinksCount++;

            if(linkParkingCapacities.get(link.getId().toString()) != null) {
                int parkingCapacity = Integer.parseInt(linkParkingCapacities.get(link.getId().toString()));

                Attributes linkAttributes = link.getAttributes();
                linkAttributes.putAttribute("parkingCapacity", parkingCapacity);

                //TODO maybe it would be better to have a csv file with parking cost per link here instead of a fixed value -sm0123
                ParkingCostConfigGroup parkingCostConfigGroup = ConfigUtils.addOrGetModule(new Config(), ParkingCostConfigGroup.class);
                linkAttributes.putAttribute(parkingCostConfigGroup.getFirstHourParkingCostLinkAttributeName(), firstHourParkingCost);
                linkAttributes.putAttribute(parkingCostConfigGroup.getExtraHourParkingCostLinkAttributeName(), extraHourParkingCost);
                adaptedLinksCount++;
            }
        }
        log.info(adaptedLinksCount + " / " + networkLinksCount + " were complemented with parking information attribute.");
    }

    private Map<String, String> getLinkParkingCapacities() {
        Map<String, String> linkParkingCapacities = new HashMap<>();

        try(BufferedReader reader = new BufferedReader(new FileReader(inputParkingCapacities.toString()))) {
            String lineEntry;
            while((lineEntry = reader.readLine()) != null) {

                linkParkingCapacities.putIfAbsent(lineEntry.split("\t")[0], lineEntry.split("\t")[1]);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return linkParkingCapacities;
    }
}
