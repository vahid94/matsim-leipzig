package org.matsim.run;

import org.matsim.api.core.v01.network.Link;
import org.matsim.core.network.filter.NetworkLinkFilter;

public class LinkAttributeNetworkLinkFilter implements NetworkLinkFilter {

	private final String attributeName;
	private final String attribute;

	LinkAttributeNetworkLinkFilter(String attributeName, String attribute) {

		this.attributeName = attributeName;
		this.attribute = attribute;
	}


	@Override
	public boolean judgeLink(Link l) {



		return false;
	}
}
