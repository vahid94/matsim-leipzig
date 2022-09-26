library(tidyverse)
library(matsim)
library(tmap)
library(tmaptools)
library(OpenStreetMap)
library(sf)

NETWORK = "C:/Users/ACER/Desktop/Uni/VSP/matsim-leipzig/output/leipzig-v1.1-network-with-pt-drt.xml"
TILES = "https://services.arcgisonline.com/arcgis/rest/services/Canvas/World_Light_Gray_Base/MapServer/tile/{z}/{y}/{x}"
DILUTION_AREA = "C:/Users/ACER/Desktop/Uni/VSP/NaMAV/matsim-input-files/senozon/20210520_leipzig/dilutionArea.shp"
LINKSTATS.V1.2 = "C:/Users/ACER/Desktop/Uni/VSP/matsim-leipzig/output/007/v1.2_run007.linkstats.tsv"
LINKSTATS.V1.0 = "C:/Users/ACER/Desktop/Uni/VSP/matsim-leipzig/output/039/v1.0_run039.linkstats.tsv"

#### IMPORT OUTPUT NETWORK AND LINKS ####
network = loadNetwork(NETWORK)
links = network$links
rm(network)

sf <- read_sf(DILUTION_AREA, crs = 25832)

link.coords <- links %>%
  select(id, ends_with(".to"), ends_with(".from"), type)

link.filtered <- links %>%
  filter(!str_detect(id, pattern = "pt")) %>%
  st_as_sf(coords = c("x.to", "y.to"), crs = 25832) %>%
  st_filter(sf) %>%
  st_as_sf(coords = c("y.from", "y.to"), crs = 25832) %>%
  st_filter(sf)

data.frame(id = link.filtered$id) %>%
  readr::write_csv(file = "C:/Users/ACER/Desktop/Uni/VSP/matsim-leipzig/output/linkIdsFiltered.csv")

join <- mergeCountsAndLinks(linkStatsList = list("v1.0_runId_037" = LINKSTATS.V1.0, "v1.2_runId_009" = LINKSTATS.V1.2), networkFilePath = NETWORK, sampleSize = 0.25, countsFilePath = "output/leipzig-v1.1-counts_Pkw.xml")

est.breaks = c(-Inf, 0.8, 1.2, Inf)
est.labels = c("less", "exact", "more")

join.1 <- join %>%
  select(loc_id, type, starts_with("vol_car")) %>%
  pivot_longer(cols = c(vol_car_v1.0_runId_037, vol_car_v1.2_runId_009), names_to = "src", names_prefix = "vol_car_", values_to = "vol_sim") %>%
  mutate(rel_vol = round(vol_sim / vol_car_count_station, 2)) %>%
  select(-starts_with("vol_")) %>%
  pivot_wider(names_from = "src", values_from = "rel_vol")
  
  
link.geom <- readr::read_csv(file = "C:/Users/ACER/Desktop/Uni/VSP/matsim-leipzig/output/linkIdsFiltered.csv") %>%
  left_join(link.coords, by = "id") %>%
  left_join(join.1, by = c("id" = "loc_id")) %>%
  filter(!is.na(type.y)) %>%
  select(-type.y) %>%
  mutate(geom = sprintf("LINESTRING(%s %s, %s %s)", x.from, y.from, x.to, y.to)) %>%
  st_as_sf(crs = 25832, wkt = "geom") %>%
  select(id, type.x, starts_with("v1"), geom) %>%
  filter(v1.0_runId_037 != Inf)
  
tmap_mode("view")

tm_shape(shp = link.geom) +
  tm_lines(col = "v1.0_runId_037", style = "cont", midpoint = 0, lwd = 3.5, breaks = c(0, 2), palette = c("red", "green", "blue"))