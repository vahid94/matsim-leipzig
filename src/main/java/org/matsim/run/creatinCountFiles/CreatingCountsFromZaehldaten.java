package org.matsim.run.creatinCountFiles;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.application.MATSimAppCommand;
import org.matsim.contrib.dvrp.router.TimeAsTravelDisutility;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.router.speedy.SpeedyALTFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.LeastCostPathCalculatorFactory;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.trafficmonitoring.FreeSpeedTravelTime;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.counts.Counts;
import org.matsim.counts.CountsWriter;
import org.matsim.counts.Volume;
import picocli.CommandLine;

import java.io.BufferedWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

@CommandLine.Command(
        name = "createLeipzigCounts",
        description = "Create vehicle counts from Leipzig count data"
)

// EPSG:25832

public class CreatingCountsFromZaehldaten implements MATSimAppCommand {

    private static final Logger logger = LogManager.getLogger(CreatingCountsFromZaehldaten.class);

    private static final CoordinateTransformation CT = TransformationFactory.getCoordinateTransformation("EPSG:31468", "EPSG:25832");

    @CommandLine.Option(names = {"-e", "--excel"}, description = "Input count excel file", required = true)
    private Path excel;

    @CommandLine.Option(names = {"-n", "--network"}, description = "Input network file", required = true)
    private Path network;

    @CommandLine.Option(names = {"-o", "--output"}, description = "Output count file", required = true)
    private Path count;

    public static void main(String[] args) {
        new CreatingCountsFromZaehldaten().execute(args);
//        CreatingCountsFromZaehldaten creatingCountsFromZaehldaten = new CreatingCountsFromZaehldaten();
//        creatingCountsFromZaehldaten.call();
    }

    @Override
    public Integer call() {

        Counts<Link> countsPkw = new Counts<>();
        countsPkw.setYear(2018);
        countsPkw.setDescription("data from leipzig to matsim counts");
        Counts<Link> countsLkw = new Counts<>();
        countsLkw.setYear(2018);
        countsLkw.setDescription("data from leipzig to matsim counts");

        List<LeipzigCounts> leipzigCountsList = new ArrayList<>();

        try {
            XSSFWorkbook wb = new XSSFWorkbook(excel.toFile());
            Sheet sheet = wb.getSheetAt(0);
            handleSheet(sheet, leipzigCountsList);
        } catch (Exception e) {
            e.printStackTrace();
        }
        creatingCounts(leipzigCountsList, countsPkw, countsLkw);

        return null;
    }

    /**
     * @param sheet
     * @param list
     */
    private static void handleSheet(Sheet sheet, List<LeipzigCounts> list) {
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            try {
                LeipzigCounts leipzigCounts = new LeipzigCounts();
                leipzigCounts.setStreckenNr((int) sheet.getRow(i).getCell(0).getNumericCellValue());
                leipzigCounts.setStartNodeID((int) sheet.getRow(i).getCell(1).getNumericCellValue());
                leipzigCounts.setEndNodeID((int) sheet.getRow(i).getCell(2).getNumericCellValue());
                if (sheet.getRow(i).getCell(3) != null && sheet.getRow(i).getCell(4) != null) {
                    leipzigCounts.setStartName(sheet.getRow(i).getCell(3).getStringCellValue());
                    leipzigCounts.setEndName(sheet.getRow(i).getCell(4).getStringCellValue());
                }
                leipzigCounts.setYear((int) sheet.getRow(i).getCell(5).getNumericCellValue());
                leipzigCounts.setKfz((int) sheet.getRow(i).getCell(6).getNumericCellValue());
                leipzigCounts.setLkw((int) sheet.getRow(i).getCell(7).getNumericCellValue());
                leipzigCounts.setRad((int) sheet.getRow(i).getCell(8).getNumericCellValue());
                leipzigCounts.setStartNodeCoordX(sheet.getRow(i).getCell(10).getNumericCellValue());
                leipzigCounts.setStartNodeCoordY(sheet.getRow(i).getCell(11).getNumericCellValue());
                leipzigCounts.setEndNodeCoordX(sheet.getRow(i).getCell(12).getNumericCellValue());
                leipzigCounts.setEndNodeCoordY(sheet.getRow(i).getCell(13).getNumericCellValue());
                if (leipzigCounts.year > 2017) {
                    list.add(leipzigCounts);
                }
            } catch (Exception e) {
                logger.error("Error when reading excel data", e);
            }
        }
    }

    private void creatingCounts(List<LeipzigCounts> leipzigCountsList, Counts<Link> countsPkw, Counts<Link> countsLkw) {
        Config config = ConfigUtils.createConfig();
        Scenario scenario = ScenarioUtils.createScenario(config);
        MatsimNetworkReader networkReader = new MatsimNetworkReader(scenario.getNetwork());
        networkReader.readFile(network.toString());
        Network network = scenario.getNetwork();
        LeastCostPathCalculator router = generateRouter(network);
        HashMap<String, Link> map = new HashMap<>();
        ArrayList<LeipzigCounts> countList = new ArrayList<>();

        for (LeipzigCounts leipzigCounts : leipzigCountsList) {
            Coord fromCoordOldSys = new Coord(leipzigCounts.getStartNodeCoordX(), leipzigCounts.getStartNodeCoordY());
            Coord toCoordOldSys = new Coord(leipzigCounts.getEndNodeCoordX(), leipzigCounts.getEndNodeCoordY());
            Coord fromCoord = CT.transform(fromCoordOldSys);
            Coord toCoord = CT.transform(toCoordOldSys);
            Node fromNode = NetworkUtils.getNearestNode(network, fromCoord);
            Node toNode = NetworkUtils.getNearestNode(network, toCoord);
            LeastCostPathCalculator.Path route = router.calcLeastCostPath(fromNode, toNode, 0.0, null, null);
            if (route.links.size() > 0) {
                Link countLink = null;
                for (Link link : route.links) {
                    if (countLink == null) {
                        countLink = link;
                    } else {
                        if (countLink.getLength() < link.getLength()) {
                            countLink = link;
                        }
                    }
                }
                if (leipzigCounts.getEndName() == null || leipzigCounts.getStartName() == null) {
                    if (calculateAngle(leipzigCounts, countLink)) {
                        countList.add(leipzigCounts);
                        map.put(leipzigCounts.getStreckenNr() + "_" + leipzigCounts.getStartNodeID() + "_" + leipzigCounts.getEndNodeID(), countLink);
                        fillingCounts(leipzigCounts, requireNonNull(countLink), countsPkw, countsLkw);
                    }
                } else {
                    countList.add(leipzigCounts);
                    map.put(leipzigCounts.getStreckenNr() + "_" + leipzigCounts.getStartNodeID() + "_" + leipzigCounts.getEndNodeID(), countLink);
                    fillingCounts(leipzigCounts, requireNonNull(countLink), countsPkw, countsLkw);
                }
            }
        }
        writeSafetyFile(countList, network, map);

        CountsWriter writerPkw = new CountsWriter(countsPkw);
        CountsWriter writerLkw = new CountsWriter(countsLkw);
        writerPkw.write(count.toString() + "_Pkw.xml");
        writerLkw.write(count.toString() + "_Lkw.xml");
    }

    private boolean calculateAngle(LeipzigCounts leipzigCounts, Link countlink) {

        Coord fromCoordOldSys = new Coord(leipzigCounts.getStartNodeCoordX(), leipzigCounts.getStartNodeCoordY());
        Coord toCoordOldSys = new Coord(leipzigCounts.getEndNodeCoordX(), leipzigCounts.getEndNodeCoordY());
        Coord fromCoord = CT.transform(fromCoordOldSys);
        Coord toCoord = CT.transform(toCoordOldSys);

        double[] v1 = new double[2];
        double[] v2 = new double[2];

        v1[0] = toCoord.getX() - fromCoord.getX();
        v1[1] = toCoord.getY() - fromCoord.getY();

        v2[0] = countlink.getToNode().getCoord().getX() - countlink.getFromNode().getCoord().getX();
        v2[1] = countlink.getFromNode().getCoord().getY() - countlink.getFromNode().getCoord().getY();

        double skalarP = v1[0] * v2[0] - v1[1] * v2[1];
        double betragV1 = Math.sqrt(v1[0] * v1[0] + v1[1] * v1[1]);
        double betragV2 = Math.sqrt(v2[0] * v2[0] + v2[1] * v2[1]);

        double angel = Math.acos(skalarP / (betragV1 * betragV2));

        return angel < Math.PI / 6; // 30°

    }

    private void writeSafetyFile(List<LeipzigCounts> leipzigCountsList, Network network, Map<String, Link> map) {
        CSVPrinter csvPrinter = new CSVPrinter("", CSVFormat.DEFAULT);
        csvPrinter.printRecords();
        try (BufferedWriter newwriter = IOUtils.getBufferedWriter("allPoints.txt")) {
            newwriter.write("originalId;oCoordStartX;oCoordStartY;oCoordEndX;oCoordEndY;nCoordStartX;nCoordStartY;nCoordEndX;nCoordEndY");
            newwriter.newLine();
            for (LeipzigCounts lc : leipzigCountsList) {
                Coord originalFrom = CT.transform(new Coord(lc.startNodeCoordX, lc.startNodeCoordY));
                Coord originalTo = CT.transform(new Coord(lc.endNodeCoordX, lc.endNodeCoordY));
                newwriter.write(lc.getStreckenNr() + "_" + lc.getStartNodeID() + "_" + lc.getEndNodeID());
                newwriter.write(";" + originalFrom.getX() + ";" + originalFrom.getY() + ";" + originalTo.getX() + ";" + originalTo.getY());
                Node fromNode = NetworkUtils.getNearestNode(network, originalFrom);
                Node toNode = NetworkUtils.getNearestNode(network, originalTo);
                newwriter.write(";" + fromNode.getCoord().getX() + ";" + fromNode.getCoord().getY() + ";" + toNode.getCoord().getX() + ";" + toNode.getCoord().getY());
                newwriter.newLine();
                newwriter.flush();
            }
        } catch (Exception e) {
            logger.error("Error when writing points", e);
        }

        try (BufferedWriter writer = IOUtils.getBufferedWriter("lines.txt")) {
            writer.write("id;line");
            writer.newLine();
            for (Map.Entry<String, Link> entry : map.entrySet()) {
//                writer2.write(entry.getKey() + "; LINESTRING (" + entry.getValue().getFromNode().getCoord().getX() + " " + entry.getValue().getFromNode().getCoord().getY() + ", " + entry.getValue().getToNode().getCoord().getX() + " " + entry.getValue().getToNode().getCoord().getY());
                writer.write(entry.getKey() + "; LINESTRING (" + entry.getValue().getFromNode().getCoord().getX() + " " + entry.getValue().getFromNode().getCoord().getY() + ", " + entry.getValue().getToNode().getCoord().getX() + " " + entry.getValue().getToNode().getCoord().getY() + ")");
                writer.newLine();
                writer.flush();
            }
        } catch (Exception e) {
            logger.error("Error when writing link ids", e);
        }
    }

    private void fillingCounts(LeipzigCounts leipzigCounts, Link countLink, Counts<Link> countsPkw, Counts<Link> countsLkw) {
        if (countsPkw.getCount(countLink.getId()) != null) {
            Volume x = countsPkw.getCount(countLink.getId()).getVolume(1);
            double c = (x.getValue() + leipzigCounts.getKfz()) / 2;
            countsPkw.getCount(Id.createLinkId(countLink.getId())).createVolume(1, c);
            Volume y = countsLkw.getCount(countLink.getId()).getVolume(1);
            double l = (y.getValue() + leipzigCounts.getLkw()) / 2;
            countsLkw.getCount(Id.createLinkId(countLink.getId())).createVolume(1, l);
            return;
        }
        countsPkw.createAndAddCount(countLink.getId(), leipzigCounts.getStartNodeID() + "_" + leipzigCounts.getEndNodeID());
        countsPkw.getCount(Id.createLinkId(countLink.getId())).createVolume(1, leipzigCounts.getKfz());
        countsLkw.createAndAddCount(countLink.getId(), leipzigCounts.getStartNodeID() + "_" + leipzigCounts.getEndNodeID());
        countsLkw.getCount(Id.createLinkId(countLink.getId())).createVolume(1, leipzigCounts.getLkw());
//        for (int i = 1; i < 25; i++) {
//            countsPkw.getCount(Id.createLinkId(berlinCounts.getLinkid())).createVolume(i, (berlinCounts.getDTVW_KFZ() * PERC_Q_PKW_TYPE[i - 1]));
//        }
    }

    private LeastCostPathCalculator generateRouter(Network network) {
        FreeSpeedTravelTime travelTime = new FreeSpeedTravelTime();
        LeastCostPathCalculatorFactory fastAStarLandmarksFactory = new SpeedyALTFactory();
        TravelDisutility travelDisutility = new TimeAsTravelDisutility(travelTime);
        LeastCostPathCalculator router = fastAStarLandmarksFactory.createPathCalculator(network, travelDisutility,
                travelTime);
        return router;
    }

    private static  final class LeipzigCounts {
        private int streckenNr;
        private int Kfz;
        private int Lkw;
        private int Rad;
        private int startNodeID;
        private int endNodeID;
        private int year;
        private double startNodeCoordX;
        private double startNodeCoordY;
        private double endNodeCoordX;
        private double endNodeCoordY;
        private String startName = null;
        private String endName = null;

        public String getStartName() {
            return startName;
        }

        public void setStartName(String startName) {
            this.startName = startName;
        }

        public String getEndName() {
            return endName;
        }

        public void setEndName(String endName) {
            this.endName = endName;
        }

        public int getStreckenNr() {
            return streckenNr;
        }

        public void setStreckenNr(int streckenNr) {
            this.streckenNr = streckenNr;
        }

        public void setKfz(int kfz) {
            Kfz = kfz;
        }

        public void setLkw(int lkw) {
            Lkw = lkw;
        }

        public void setRad(int rad) {
            Rad = rad;
        }

        public void setStartNodeID(int startNodeID) {
            this.startNodeID = startNodeID;
        }

        public void setEndNodeID(int endNodeID) {
            this.endNodeID = endNodeID;
        }

        public void setYear(int year) {
            this.year = year;
        }

        public void setStartNodeCoordX(double startNodeCoordX) {
            this.startNodeCoordX = startNodeCoordX;
        }

        public void setStartNodeCoordY(double startNodeCoordY) {
            this.startNodeCoordY = startNodeCoordY;
        }

        public void setEndNodeCoordX(double endNodeCoordX) {
            this.endNodeCoordX = endNodeCoordX;
        }

        public void setEndNodeCoordY(double endNodeCoordY) {
            this.endNodeCoordY = endNodeCoordY;
        }


    }
}