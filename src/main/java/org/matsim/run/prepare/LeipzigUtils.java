package org.matsim.run.prepare;

import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import playground.vsp.simpleParkingCostHandler.ParkingCostConfigGroup;

public class LeipzigUtils{
	private LeipzigUtils(){} // do not instantiate

	public static boolean parkingIsRestricted( Link link ) {
		String result = (String) link.getAttributes().getAttribute( "parking" );
		if ( result == null ) {
			return false ;
		} else {
			return true;
		}
	}
	public static void setParkingToRestricted( Link link ){
		link.getAttributes().putAttribute( "parking", "restricted" );
	}
	// yy change the logic of the above to enums

	public static void setParkingToRestricted(Person person) {
		person.getAttributes().putAttribute("parkingType", "residentialParking");
	}

	public static void setParkingToNonRestricted(Person person) {
		person.getAttributes().putAttribute("parkingType", "nonResidentialParking");
	}

	//TODO put this into PrepareNetwork.prepareParkingCost after merge, lines 195-197
	//TODO also add in ParkingCapacityAttacher lines 74-79
	public static void setLinkParkingCostAttributes(Link link, String attributeName, double attributeValue) {
		link.getAttributes().putAttribute(attributeName, attributeValue);
	}

	//TODO i don´t like the name for this
	public static void setLinkToParkingForShopping (Link link) {
		link.getAttributes().putAttribute("parkingForShopping", "parkingLot");
	}

	public static boolean parkingAllowedForShopping(Link link) {
		String result = (String) link.getAttributes().getAttribute( "parkingForShopping" );
		if (result == null) {
			return false ;
		} else {
			return true;
		}
	}
}
