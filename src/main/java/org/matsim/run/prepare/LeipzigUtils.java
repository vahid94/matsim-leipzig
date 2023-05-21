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

	// do not instantiate
	private LeipzigUtils(){}

	/**
	 * Defines the parkingBehaviour of a person.
	 */
	public enum PersonParkingBehaviour {defaultLogic, parkingSearchLogicLeipzig, @Deprecated shopping}

	/**
	 * Defines the parkingType of a network link.
	 */
	private enum LinkParkingType{linkOutsideResidentialArea, linkInResidentialArea}

	/**
	 * Check if link is inside residential area or not (parking on link is restricted or not).
	 */
	public static boolean isLinkParkingTypeInsideResidentialArea(Link link ) {
		String result = (String) link.getAttributes().getAttribute( "linkParkingType" );
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

	public static void setLinkParkingTypeToInsideResidentialArea(Link link ){
		link.getAttributes().putAttribute( "linkParkingType", LinkParkingType.linkInResidentialArea.toString() );
	}

//	public static void setParkingToRestricted(Person person) {
//		person.getAttributes().putAttribute("parkingType", "residentialParking");
//	}
//
//	public static void setParkingToNonRestricted(Person person) {
//		person.getAttributes().putAttribute("parkingType", "nonResidentialParking");
//	}

	public static void setFirstHourParkingCost(Link link, double parkingCost) {
		link.getAttributes().putAttribute(getFirstHourParkingCostLinkAttributeName(), parkingCost);
	}

	public static void setExtraHourParkingCost(Link link, double parkingCost) {
		link.getAttributes().putAttribute(getExtraHourParkingCostLinkAttributeName(), parkingCost);
	}

	public static void setResidentialParkingCost(Link link, double parkingCost) {
		link.getAttributes().putAttribute(getResidentialParkingFeeAttributeName(), parkingCost);
	}

	public static void setParkingCapacity(Link link, double parkingCapacity) {
		link.getAttributes().putAttribute("parkingCapacity", parkingCapacity);
	}

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
