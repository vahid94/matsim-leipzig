package org.matsim.run.prepare;

import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.application.options.ShpOptions;
import org.matsim.core.utils.geometry.geotools.MGC;
import playground.vsp.openberlinscenario.cemdap.output.ActivityTypes;

final class AssignParkingAttributes {

	private AssignParkingAttributes() {}

	static void addParkingAttributesToPopulation(Population population, ShpOptions shp) {

		Geometry restrictedParkingArea = shp.getGeometry();
		boolean isInsideRestrictedParkingArea;

		for (Person person : population.getPersons().values()) {
			for (PlanElement element : person.getSelectedPlan().getPlanElements()) {
				if (element instanceof Activity) {

					Activity activity = (Activity) element;

					if (!activity.getType().contains(ActivityTypes.HOME)) {
						continue;
					}

					isInsideRestrictedParkingArea = MGC.coord2Point(activity.getCoord()).within(restrictedParkingArea);

					if (isInsideRestrictedParkingArea) {
						LeipzigUtils.setPersonParkingType(person, LeipzigUtils.PersonParkingType.closestToActivity);
					} else {
						LeipzigUtils.setPersonParkingType(person, LeipzigUtils.PersonParkingType.restrictedForNonResidents);
					}

					break;
				}
			}
		}
	}
}
