library(tidyverse)
library(matsim)
library(tmap)
library(tmaptools)
library(OpenStreetMap)
library(sf)

NETWORK = "C:/Users/ACER/Desktop/Uni/VSP/matsim-leipzig/output/leipzig-v1.1-network-with-pt-drt.xml"
TILES = "https://services.arcgisonline.com/arcgis/rest/services/Canvas/World_Light_Gray_Base/MapServer/tile/{z}/{y}/{x}"
DILUTION_AREA = "C:/Users/ACER/Desktop/Uni/VSP/NaMAV/matsim-input-files/senozon/20210520_leipzig/dilutionArea.shp"

#### IMPORT OUTPUT NETWORK AND LINKS ####
network = loadNetwork(NETWORK)
links = network$links
rm(network)

sf <- read_sf(DILUTION_AREA, crs = 25832)

link.coords <- links %>%
  select(id, ends_with(".to"), ends_with(".from"), type)

link.filtered <- links %>%
  st_as_sf(coords = c("x.to", "y.to"), crs = 25832) %>%
  st_filter(sf) %>%
  st_as_sf(coords = c("y.from", "y.to"), crs = 25832) %>%
  st_filter(sf)

link.geom <- data.frame(id = link.filtered$id) %>%
  left_join(link.coords, by = "id") %>%
  mutate(geom = sprintf("LINESTRING(%s %s, %s %s)", x.from, y.from, x.to, y.to)) %>%
  st_as_sf(crs = 25832, wkt = "geom") %>%
  select(id, type, geom)
  
tm_shape(shp = link.geom) +
  tm_lines(col = "type", style = "cont", midpoint = 0, breaks = c(-2000, 2000), lwd = 3.5)

#### OPEN STREET MAP SETUP ####
bbox <- bb(c(6.693, 51.175, 6.875, 51.278))
osm = read_osm(bbox, zoom = 13, type = TILES)

tm_shape(osm) +
  tm_rgb() +
  tm_shape(link.geom) +
  tm_lines()
