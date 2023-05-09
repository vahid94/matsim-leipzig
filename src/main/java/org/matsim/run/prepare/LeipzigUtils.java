package org.matsim.run.prepare;

import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;

import java.util.HashSet;
import java.util.Set;

/**
 * Utils class to adapt scenario-related person / link attributes.
 */
public final class LeipzigUtils{

	private static final String mode = "car";
	private static final String dailyParkingCostLinkAttributeName = "dailyPCost";
	private static final String firstHourParkingCostLinkAttributeName = "oneHourPCost";
	private static final String extraHourParkingCostLinkAttributeName = "extraHourPCost";
	private static final String maxDailyParkingCostLinkAttributeName = "maxDailyPCost";
	private static final String maxParkingDurationAttributeName = "maxPDuration";
	private static final String parkingPenaltyAttributeName = "penalty";
	private static final String residentialParkingFeePerDay = "residentialPFee";
	private static final String activityPrefixForDailyParkingCosts = "home";
	private static final Set<String> activityPrefixToBeExcludedFromParkingCost = new HashSet<>();

	/**
	 * Defines if agent is allowed to park on the closest link to the destination activity (closestToActivity)
	 * = has residential parking card
	 * or if the agent does not possess a residential parking card and therefore has to search for
	 * a parking link on a reduced network (restrictedForNonResidents)
	 */
	public enum PersonParkingType {
		closestToActivity,
		restrictedForNonResidents,
		shopping
	}

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

	public static String getMode() {
		return mode;
	}

	public static String getDailyParkingCostLinkAttributeName() {
		return dailyParkingCostLinkAttributeName;
	}

	public static String getFirstHourParkingCostLinkAttributeName() {
		return firstHourParkingCostLinkAttributeName;
	}

	public static String getExtraHourParkingCostLinkAttributeName() {
		return extraHourParkingCostLinkAttributeName;
	}

	public static String getMaxDailyParkingCostLinkAttributeName() {
		return maxDailyParkingCostLinkAttributeName;
	}

	public static String getMaxParkingDurationAttributeName() {
		return maxParkingDurationAttributeName;
	}

	public static String getParkingPenaltyAttributeName() {
		return parkingPenaltyAttributeName;
	}

	public static String getResidentialParkingFeeAttributeName() {
		return residentialParkingFeePerDay;
	}

	public static String getActivityPrefixForDailyParkingCosts() {
		return activityPrefixForDailyParkingCosts;
	}

	public static Set<String> getActivityPrefixesToBeExcludedFromParkingCost() {
		return activityPrefixToBeExcludedFromParkingCost;
	}

	public static void setLinkParkingToNotInResidentialArea(Link link ){
		link.getAttributes().putAttribute( "parking",  "notInsideResidentialArea");
	}
	// yy change the logic of the above to enums

	public static void setLinkToResidentialArea( Link link ) {
		link.getAttributes().putAttribute("parkingType", "");

	}

	public static void setPersonParkingType(Person person, PersonParkingType parkingType) {
		person.getAttributes().putAttribute("parkingType", parkingType.toString());
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
