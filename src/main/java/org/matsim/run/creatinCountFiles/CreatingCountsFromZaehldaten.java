package org.matsim.run.creatinCountFiles;

import org.apache.log4j.Logger;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.geotools.referencing.CRS;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.CrsOptions;
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
import org.matsim.counts.Counts;
import org.matsim.counts.CountsWriter;
import org.matsim.vehicles.Vehicle;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

@CommandLine.Command(
        name = "createLeipzigCounts",
        description = "Create vehicle counts from Leipzig count data"
)

// EPSG:25832

public class CreatingCountsFromZaehldaten implements MATSimAppCommand {

    private static final Logger logger = Logger.getLogger(CreatingCountsFromZaehldaten.class);

    @CommandLine.Option(names = {"-e", "--excel"}, description = "Input count excel file", required = true)
    private String excel;

    @CommandLine.Option(names = {"-n", "--network"}, description = "Input network file", required = true)
    private String network;

    @CommandLine.Option(names = {"-o", "--output"}, description = "Output count file", required = true)
    private String count;

    public static void main(String[] args) {
        new CreatingCountsFromZaehldaten().execute(args);
    }

    @Override
    public Integer call() {

        excel = "D:/Code/shared-svn/projects/NaMAV/data/Zaehldaten/Zaehldaten.xlsx";
        network = "Input/leipzig-v1.0-network.xml.gz";
        count = "";

        Counts<Link> countsPkw = new Counts();
        countsPkw.setYear(2018);
        countsPkw.setDescription("data from leipzig to matsim counts");
        Counts<Link> countsLkw = new Counts();
        countsLkw.setYear(2018);
        countsLkw.setDescription("data from leipzig to matsim counts");

        List<leipzigCounts> leipzigCountsList = new ArrayList();

        try {
            XSSFWorkbook wb = new XSSFWorkbook(excel);
            Sheet sheet = wb.getSheetAt(0);
            handleSheet(sheet, leipzigCountsList);
        } catch (Exception e) {
            e.printStackTrace();
        }
        creatingCounts(leipzigCountsList, countsPkw, countsLkw);

        return null;
    }

    public static void handleSheet(Sheet sheet, List<leipzigCounts> list) {
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            try {
                leipzigCounts leipzigCounts = new leipzigCounts();
                leipzigCounts.setStartNodeID((int) sheet.getRow(i).getCell(0).getNumericCellValue());
                leipzigCounts.setEndNodeID((int) sheet.getRow(i).getCell(1).getNumericCellValue());
                leipzigCounts.setYear((int) sheet.getRow(i).getCell(5).getNumericCellValue());
                leipzigCounts.setKfz((int) sheet.getRow(i).getCell(6).getNumericCellValue());
                leipzigCounts.setLkw((int) sheet.getRow(i).getCell(7).getNumericCellValue());
                leipzigCounts.setRad((int) sheet.getRow(i).getCell(8).getNumericCellValue());
                leipzigCounts.setStartNodeCoordX(sheet.getRow(i).getCell(10).getNumericCellValue());
                leipzigCounts.setStartNodeCoordY(sheet.getRow(i).getCell(11).getNumericCellValue());
                leipzigCounts.setEndNodeCoordX(sheet.getRow(i).getCell(12).getNumericCellValue());
                leipzigCounts.setEndNodeCoordY(sheet.getRow(i).getCell(13).getNumericCellValue());
                if (leipzigCounts.getYear() > 2017) {
                    list.add(leipzigCounts);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void creatingCounts(List<leipzigCounts> leipzigCountsList, Counts<Link> countsPkw, Counts<Link> countsLkw) {
        Config config = ConfigUtils.createConfig();
        Scenario scenario = ScenarioUtils.createScenario(config);
        MatsimNetworkReader networkReader = new MatsimNetworkReader(scenario.getNetwork());
        networkReader.readFile(network);
        Network network = scenario.getNetwork();
        LeastCostPathCalculator router = generateRouter(network);

        CoordinateTransformation ct = TransformationFactory.getCoordinateTransformation("EPSG:31468", "EPSG:25832");
        ct.transform(new Coord());

        // TODO: 18.06.2021 extra datei zum schauen des "matching" (csv mit Link(node) coord)
        for(leipzigCounts leipzigCounts : leipzigCountsList) {
            Coord fromCoord = new Coord(leipzigCounts.getStartNodeCoordX(), leipzigCounts.getStartNodeCoordY());
            Coord toCoord = new Coord(leipzigCounts.getEndNodeCoordX(), leipzigCounts.getEndNodeCoordY());
            Node fromNode = NetworkUtils.getNearestNode(network, fromCoord);
            Node toNode = NetworkUtils.getNearestNode(network, toCoord);
            LeastCostPathCalculator.Path route = router.calcLeastCostPath(fromNode, toNode, 0.0, null, null);
            if (route.links.size() > 0) {
                Link countLink = null;
                for (Link link : route.links) {
                    if (countLink == null) {
                        countLink = link;
                    } else {
                        if (countLink.getFromNode().getOutLinks().size() + countLink.getToNode().getInLinks().size() > link.getFromNode().getOutLinks().size() + link.getToNode().getInLinks().size()) {
                            countLink = link;
                        }
                    }
                }
                cccccc(leipzigCounts, requireNonNull(countLink), countsPkw, countsLkw);
            }
        }

        CountsWriter writerPkw = new CountsWriter(countsPkw);
        CountsWriter writerLkw = new CountsWriter(countsLkw);
        writerPkw.write(count + "_Pkw.xml");
        writerLkw.write(count + "_Lkw.xml");
    }

    private void cccccc(leipzigCounts leipzigCounts, Link countLink, Counts<Link> countsPkw, Counts<Link> countsLkw) {
        countsPkw.createAndAddCount(countLink.getId(), leipzigCounts.getStartNodeID() + "_" + leipzigCounts.getEndNodeID());
//        countsPkw.getCount(Id.createLinkId(countLink.getId())).createVolume(1, );
        countsLkw.createAndAddCount(countLink.getId(), leipzigCounts.getStartNodeID() + "_" + leipzigCounts.getEndNodeID());
//        for (int i = 1; i < 25; i++) {
//            countsPkw.getCount(Id.createLinkId(berlinCounts.getLinkid())).createVolume(i, (berlinCounts.getDTVW_KFZ() * PERC_Q_PKW_TYPE[i - 1]));
//        }
    }

    private LeastCostPathCalculator generateRouter(Network network) {
        FreeSpeedTravelTime travelTime = new FreeSpeedTravelTime();
        LeastCostPathCalculatorFactory fastAStarLandmarksFactory = new SpeedyALTFactory();
        TravelDisutility travelDisutility = new TravelDisutility() {
            @Override
            public double getLinkTravelDisutility(Link link, double time, Person person, Vehicle vehicle) {
                return 0;
            }

            @Override
            public double getLinkMinimumTravelDisutility(Link link) {
                return 0;
            }
        };
        LeastCostPathCalculator router = fastAStarLandmarksFactory.createPathCalculator(network, travelDisutility,
                travelTime);
        return router;
    }
}

class leipzigCounts {
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

    public int getKfz() {
        return Kfz;
    }

    public int getLkw() {
        return Lkw;
    }

    public int getRad() {
        return Rad;
    }

    public int getStartNodeID() {
        return startNodeID;
    }

    public int getEndNodeID() {
        return endNodeID;
    }

    public int getYear() {
        return year;
    }

    public double getStartNodeCoordX() {
        return startNodeCoordX;
    }

    public double getStartNodeCoordY() {
        return startNodeCoordY;
    }

    public double getEndNodeCoordX() {
        return endNodeCoordX;
    }

    public double getEndNodeCoordY() {
        return endNodeCoordY;
    }

}