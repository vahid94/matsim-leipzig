package org.matsim.run.prepare;

import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;

/**
 * Utils class to adapt scenario-related person / link attributes.
 */
public final class LeipzigUtils{

	public static final String FIRST_HOUR_PARKING_COST_LINK_ATTRIBUTE_NAME = "firstHourParkingCostLinkAttributeName";
	public static final String EXTRA_HOUR_PARKING_COST_LINK_ATTRIBUTE_NAME = "extraHourParkingCostLinkAttributeName";
	public static final String RESIDENTIAL_PARKING_FEE_PER_DAY = "residentialParkingFeePerDay";

	// do not instantiate
	private LeipzigUtils(){}

	/**
	 * Check of parking on link is restricted or not.
	 */
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

	public static void setLinkAttribute(Link link, String attributeName, double attributeValue) {
		link.getAttributes().putAttribute(attributeName, attributeValue);
	}

	//TODO i don´t like the name for this
	//Bbetter?
	public static void setParkingToShoppingCenter(Link link) {
		link.getAttributes().putAttribute("parkingForShopping", "shoppingCenter");
	}

	/**
	 * check if parking for activity type shopping is allowed on a given link.
	 */
	public static boolean parkingAllowedForShopping(Link link) {
		String result = (String) link.getAttributes().getAttribute( "parkingForShopping" );
		if (result == null) {
			return false ;
		} else {
			return true;
		}
	}
}
