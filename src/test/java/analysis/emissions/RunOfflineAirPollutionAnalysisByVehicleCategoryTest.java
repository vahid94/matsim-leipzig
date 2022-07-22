package analysis.emissions;

import org.junit.jupiter.api.Test;

class RunOfflineAirPollutionAnalysisByVehicleCategoryTest {

//    @Rule
//    public MatsimTestUtils utils = new MatsimTestUtils() ;

    @Test
    void run() {
        try {
            // TODO local file usage
            String runDirectory = "/Users/rgraebe/IdeaProjects/matsim-leipzig/output/it-1pct/";
            String runId = "leipzig-25pct";
            String hbefaFileWarm = "/Users/rgraebe/shared-svn/projects/matsim-germany/hbefa/hbefa-files/v4.1/EFA_HOT_Vehcat_2020_Average.csv";
            String hbefaFileCold = "/Users/rgraebe/shared-svn/projects/matsim-germany/hbefa/hbefa-files/v4.1/EFA_ColdStart_Vehcat_2020_Average_withHGVetc.csv";
            String analysisOutputDirectory = "/Users/rgraebe/IdeaProjects/matsim-leipzig/src/test/java/analysis/emissions/emission-analysis-offline-test";
            RunOfflineAirPollutionAnalysisByVehicleCategory analysis = new RunOfflineAirPollutionAnalysisByVehicleCategory(runDirectory, runId, hbefaFileWarm, hbefaFileCold, analysisOutputDirectory);
            analysis.run();

        } catch ( Exception ee ) {
            throw new RuntimeException(ee) ;
        }
    }
}