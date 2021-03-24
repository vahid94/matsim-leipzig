
JAR := matsim-leipzig-*.jar
V := v1.0

export SUMO_HOME := $(abspath ../../sumo-1.8.0/)
osmosis := osmosis\bin\osmosis

.PHONY: prepare

$(JAR):
	mvn package

# Required files
scenarios/input/network.osm.pbf:
	curl https://download.geofabrik.de/europe/germany-210101.osm.pbf\
	  -o scenarios/input/network.osm.pbf

scenarios/input/gtfs-lvb.zip:
	curl https://opendata.leipzig.de/dataset/8803f612-2ce1-4643-82d1-213434889200/resource/b38955c4-431c-4e8b-a4ef-9964a3a2c95d/download/gtfsmdvlvb.zip\
	  -o $@

scenarios/input/network.osm: scenarios/input/network.osm.pbf

	$(osmosis) --rb file=$<\
	 --tf accept-ways highway=motorway,motorway_link,trunk,trunk_link,primary,primary_link,secondary_link,secondary,tertiary,motorway_junction,residential,unclassified,living_street\
	 --bounding-box top=51.457 left=12.137 bottom=51.168 right=12.703\
	 --used-node --wb network-detailed.osm.pbf

	$(osmosis) --rb file=$<\
	 --tf accept-ways highway=motorway,motorway_link,trunk,trunk_link,primary,primary_link,secondary_link,secondary,tertiary,motorway_junction\
	 --bounding-box top=51.92 left=11.45 bottom=50.83 right=13.36\
	 --used-node --wb network-coarse.osm.pbf

	$(osmosis) --rb file=$<\
	 --tf accept-ways highway=motorway,motorway_link,motorway_junction,trunk,trunk_link,primary,primary_link\
	 --used-node --wb network-germany.osm.pbf

	$(osmosis) --rb file=network-germany.osm.pbf --rb file=network-coarse.osm.pbf --rb file=network-detailed.osm.pbf\
  	 --merge --merge --wx $@

	rm network-detailed.osm.pbf
	rm network-coarse.osm.pbf
	rm network-germany.osm.pbf


scenarios/input/sumo.net.xml: scenarios/input/network.osm

	$(SUMO_HOME)/bin/netconvert --geometry.remove --ramps.guess --ramps.no-split\
	 --type-files $(SUMO_HOME)/data/typemap/osmNetconvert.typ.xml,$(SUMO_HOME)/data/typemap/osmNetconvertUrbanDe.typ.xml\
	 --tls.guess-signals true --tls.discard-simple --tls.join --tls.default-type actuated\
	 --junctions.join --junctions.corner-detail 5\
	 --roundabouts.guess --remove-edges.isolated\
	 --no-internal-links --keep-edges.by-vclass passenger,bicycle --remove-edges.by-type highway.track,highway.services,highway.unsurfaced\
	 --remove-edges.by-vclass hov,tram,rail,rail_urban,rail_fast,pedestrian\
	 --output.original-names --output.street-names\
	 --proj "+proj=utm +zone=32 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs"\
	 --osm-files $< -o=$@


scenarios/input/leipzig-$V-network.xml.gz: scenarios/input/sumo.net.xml
	java -jar $(JAR) prepare network-from-sumo $<\
	 --output $@

scenarios/input/leipzig-$V-network-with-pt.xml.gz: scenarios/input/leipzig-$V-network.xml.gz scenarios/input/gtfs-lvb.zip
	java -jar $(JAR) prepare transit-from-gtfs --network $< $(filter-out $<,$^)\
	 --name leipzig-$V --date "2019-06-05"

scenarios/input/leipzig-$V-25pct.plans.xml.gz:
	java -jar $(JAR) prepare trajectory-to-plans\
	 --name leipzig-$V --sample-size 0.25\
	 --population ../../shared-svn/NaMAV/matsim-input-files/senozon/20210309_leipzig/optimizedPopulation_filtered.xml.gz\
	 --attributes  ../../shared-svn/NaMAV/matsim-input-files/senozon/20210309_leipzig/personAttributes.xml.gz


# TODO: resolve grid

# TODO: freight


# Aggregated target
prepare: scenarios/input/leipzig-$V-25pct.plans.xml.gz scenarios/input/leipzig-$V-network-with-pt.xml.gz
	echo "Done"