
JAR := matsim-leipzig-*.jar
V := v1.0

export SUMO_HOME := $(abspath ../../sumo-1.8.0/)
osmosis := osmosis\bin\osmosis

.PHONY: prepare

$(JAR):
	mvn package

# Required files
scenarios/input/network.osm.pbf:
	curl https://download.geofabrik.de/europe/germany-201201.osm.pbf\
	  -o scenarios/input/network.osm.pbf

scenarios/input/gtfs-lvb.zip:
	curl https://opendata.leipzig.de/dataset/8803f612-2ce1-4643-82d1-213434889200/resource/b38955c4-431c-4e8b-a4ef-9964a3a2c95d/download/gtfsmdvlvb.zip\
	  -o $@

scenarios/input/network.osm: scenarios/input/network.osm.pbf

	$(osmosis) --rb file=$<\
	 --tf accept-ways highway=motorway,motorway_link,trunk,trunk_link,primary,primary_link,secondary_link,secondary,tertiary,motorway_junction,residential,unclassified,living_street\
	 --bounding-box top=51.65 left=6.00 bottom=50.60 right=7.56\
	 --used-node --wb network-detaied.osm.pbf

	$(osmosis) --rb file=$<\
	--tf accept-ways highway=motorway,motorway_link,trunk,trunk_link,primary,primary_link,secondary_link,secondary,tertiary,motorway_junction\
	--bounding-box top=51.46 left=6.60 bottom=50.98 right=7.03\
	--used-node --wb network-coarse.osm.pbf

	$(osmosis) --rb file=$<\
	--tf accept-ways highway=motorway,motorway_link,trunk,trunk_link,primary,primary_link,secondary_link,secondary,motorway_junction\
	--used-node --wb network-germany.osm.pbf

	$(osmosis) --rb file=network-germany.osm.pbf --rb file=network-coarse.osm.pbf --rb file=network-detailed.osm.pbf\
  	--merge --wx $@

	rm network-detailed.osm.pbf
	rm network-coarse.osm.pbf
	rm network-germany.osm.pbf


scenarios/input/sumo.net.xml: scenarios/input/network.osm

	$(SUMO_HOME)/bin/netconvert --geometry.remove --ramps.guess\
	 --type-files $(SUMO_HOME)/data/typemap/osmNetconvert.typ.xml,$(SUMO_HOME)/data/typemap/osmNetconvertUrbanDe.typ.xml\
	 --tls.guess-signals true --tls.discard-simple --tls.join --tls.default-type actuated\
	 --junctions.join --junctions.corner-detail 5\
	 --roundabouts.guess --remove-edges.isolated\
	 --no-internal-links --keep-edges.by-vclass passenger --remove-edges.by-type highway.track,highway.services,highway.unsurfaced\
	 --remove-edges.by-vclass hov,tram,rail,rail_urban,rail_fast,pedestrian\
	 --output.original-names --output.street-names\
	 --proj "+proj=utm +zone=32 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs"\
	 --osm-files $< -o=$@


scenarios/input/leipzig-$V-network.xml.gz: scenarios/input/sumo.net.xml
	java -jar $(JAR) prepare network $<
	 --output $@

scenarios/input/leipzig-$V-network-with-pt.xml.gz: scenarios/input/leipzig-$V-network.xml.gz scenarios/input/gtfs-lvb.zip
	java -jar $(JAR) prepare transit --network $< $(filter-out $<,$^)

scenarios/input/leipzig-$V-25pct.plans.xml.gz:
	java -jar $(JAR) prepare population\
	 --population ../../shared-svn/komodnext/matsim-input-files/leipzig-senozon/optimizedPopulation_filtered.xml.gz\
	 --attributes  ../../shared-svn/komodnext/matsim-input-files/leipzig-senozon/personAttributes.xml.gz


# Aggregated target
prepare: scenarios/input/leipzig-$V-25pct.plans.xml.gz scenarios/input/leipzig-$V-network-with-pt.xml.gz
	echo "Done"