package org.matsim.run;

import org.matsim.api.core.v01.network.Link;

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
	enum PersonParkingBehaviour {defaultLogic, parkingSearchLogicLeipzig, @Deprecated shopping}

	/**
	 * Defines the parkingType of a network link.
	 */
	private enum LinkParkingType{linkOutsideResidentialArea, linkInResidentialArea}

	/**
	 * Check if link is inside residential area or not (parking on link is restricted or not).
	 */
	public static boolean isLinkParkingTypeInsideResidentialArea(Link link ) {
		String result = (String) link.getAttributes().getAttribute( "linkParkingType" );
		boolean insideResidentialArea = true;

		if ( result == null ) {
			insideResidentialArea = false;
		}
		return insideResidentialArea;
	}

	static String getMode() {
		return mode;
	}

	static String getDailyParkingCostLinkAttributeName() {
		return dailyParkingCostLinkAttributeName;
	}

	static String getFirstHourParkingCostLinkAttributeName() {
		return firstHourParkingCostLinkAttributeName;
	}

	static String getExtraHourParkingCostLinkAttributeName() {
		return extraHourParkingCostLinkAttributeName;
	}

	static String getMaxDailyParkingCostLinkAttributeName() {
		return maxDailyParkingCostLinkAttributeName;
	}

	static String getMaxParkingDurationAttributeName() {
		return maxParkingDurationAttributeName;
	}

	static String getParkingPenaltyAttributeName() {
		return parkingPenaltyAttributeName;
	}

	static String getResidentialParkingFeeAttributeName() {
		return residentialParkingFeePerDay;
	}

	static String getActivityPrefixForDailyParkingCosts() {
		return activityPrefixForDailyParkingCosts;
	}

	static Set<String> getActivityPrefixesToBeExcludedFromParkingCost() {
		return activityPrefixToBeExcludedFromParkingCost;
	}

	public static void setLinkParkingTypeToInsideResidentialArea(Link link ){
		link.getAttributes().putAttribute( "linkParkingType", LinkParkingType.linkInResidentialArea.toString() );
	}

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



}
