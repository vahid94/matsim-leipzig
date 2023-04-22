package org.matsim.run.prepare;

import org.matsim.api.core.v01.network.Link;

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
}
