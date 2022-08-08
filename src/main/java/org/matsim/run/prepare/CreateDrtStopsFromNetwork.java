package org.matsim.run.prepare;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.ShpOptions;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import picocli.CommandLine;

import java.io.FileWriter;
import java.io.IOException;

@CommandLine.Command(
        name = "create-drt-stops",
        description = "create drt stops based on matsim network nodes inside of service area"
)
public class CreateDrtStopsFromNetwork implements MATSimAppCommand {
    @CommandLine.Mixin
    private ShpOptions shp = new ShpOptions();

    @CommandLine.Option(names = "--network", description = "network file", required = true)
    private String network;

    @CommandLine.Option(names = "--mode", description = "mode of the drt", required = true)
    private String mode;
    // mode = "drt", "av" or other specific drt operator mode

    @CommandLine.Option(names = "--output-folder", description = "path to output folder", required = true)
    private String outputFolder;

    private static final Logger log = LogManager.getLogger(CreateDrtStopsFromNetwork.class);

    public static void main(String[] args) throws IOException {
        new CreateDrtStopsFromNetwork().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        Config config = ConfigUtils.createConfig();
        config.network().setInputFile(network);
        Scenario scenario = ScenarioUtils.loadScenario(config);
        Network network = scenario.getNetwork();

        String stopsData = shp.getShapeFile().toString() + "_" + mode + "_stops.csv";
        Geometry drtServiceArea = null;

        if(shp.getShapeFile()!=null) {
            drtServiceArea = shp.getGeometry();
        } else {
            log.error("The input shp file is empty or does not exist.");
            return 2;
        }

        FileWriter csvWriter = new FileWriter(stopsData);
        csvWriter.append("name");
        csvWriter.append(";");
        csvWriter.append("ort");
        csvWriter.append(";");
        csvWriter.append("x");
        csvWriter.append(";");
        csvWriter.append("y");

        for(Node node : network.getNodes().values()) {
            //we dont want pt nodes included as pt has a separate network + no dead ends
            if(MGC.coord2Point(node.getCoord()).within(drtServiceArea) && (node.getInLinks().size() + node.getOutLinks().size() > 2)
            && !node.getId().toString().contains("pt_")) {
                csvWriter.append("\n");
                csvWriter.append(node.getId().toString());
                csvWriter.append(";");
                csvWriter.append("matsimNetworkNode");
                csvWriter.append(";");
                csvWriter.append(Double.toString(node.getCoord().getX()));
                csvWriter.append(";");
                csvWriter.append(Double.toString(node.getCoord().getY()));
            }
        }
        csvWriter.close();

        MATSimAppCommand prepareDrtStops = new PrepareDrtStops();
        prepareDrtStops.execute("--stops-data", stopsData, "--network", config.network().getInputFile(), "--mode", mode,
                "--shp", shp.getShapeFile().toString(), "--output-folder", outputFolder);

        return 0;
    }
}