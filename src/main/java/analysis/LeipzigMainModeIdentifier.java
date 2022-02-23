package analysis;

import com.google.inject.Inject;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.router.AnalysisMainModeIdentifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class LeipzigMainModeIdentifier implements AnalysisMainModeIdentifier {
    private final List<String> modeHierarchy = new ArrayList();
    private final List<String> drtModes = Arrays.asList("drtNorth","drtSoutheast","drt", "av", "drt_teleportation");

    @Inject
    public LeipzigMainModeIdentifier() {
        this.modeHierarchy.add(TransportMode.transit_walk);
        this.modeHierarchy.add(TransportMode.walk);
        this.modeHierarchy.add("bike");
        this.modeHierarchy.add("bicycle");
        this.modeHierarchy.add(TransportMode.ride);
        this.modeHierarchy.add(TransportMode.car);
        Iterator var1 = this.drtModes.iterator();

        while(var1.hasNext()) {
            String drtMode = (String)var1.next();
            this.modeHierarchy.add(drtMode);
        }

        this.modeHierarchy.add(TransportMode.pt);
        this.modeHierarchy.add("freight");
    }

    public String identifyMainMode(List<? extends PlanElement> planElements) {
        int mainModeIndex = -1;
        Iterator var3 = planElements.iterator();

        while(true) {
            String mode;
            do {
                do {
                    do {
                        PlanElement pe;
                        do {
                            if (!var3.hasNext()) {
                                if (mainModeIndex == -1) {
                                    throw new RuntimeException("no main mode found for trip " + planElements);
                                }

                                return (String)this.modeHierarchy.get(mainModeIndex);
                            }

                            pe = (PlanElement)var3.next();
                        } while(!(pe instanceof Leg));

                        Leg leg = (Leg)pe;
                        mode = leg.getMode();
                    } while(mode.equals("non_network_walk"));
                } while(mode.equals("access_walk"));
            } while(mode.equals("egress_walk"));

            if (mode.equals("transit_walk")) {
                mode = "walk";
            } else {
                Iterator var9 = this.drtModes.iterator();

                while(var9.hasNext()) {
                    String drtMode = (String)var9.next();
                    if (mode.equals(drtMode + "_fallback")) {
                        mode = "walk";
                    }
                }
            }

            int index = this.modeHierarchy.indexOf(mode);
            if (index < 0) {
                throw new RuntimeException("unknown mode=" + mode);
            }

            if (index > mainModeIndex) {
                mainModeIndex = index;
            }
        }
    }
}
