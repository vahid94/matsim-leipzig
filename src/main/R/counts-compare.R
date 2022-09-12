library(tidyverse)
library(xml2)
library(readr)
library(matsim)

savePlotAsJPG <- function(name, plot = last_plot()){
  plotDir = "C:/Users/ACER/Desktop/Uni/VSP/NaMAV/data/Zaehldaten/Plots/"
  filename = paste0(plotDir, name, ".jpg")
  
  print(paste("Save plot to", filename))
  ggsave(filename = filename, plot = plot)
}

readMATSimLinkStats <- function(runId){
  
  OUTPUT_DIR = "C:/Users/ACER/Desktop/Uni/VSP/matsim-leipzig/output/"
  LINKSTATS = ".linkstats.tsv"
  
  split = str_split(runId, pattern = "_")
  runNr = split[[1]][2]
  nr = str_remove(runNr ,pattern = "run")
  
  PATH = paste0(OUTPUT_DIR, nr, "/", runId, LINKSTATS)
  
  linkstats = read_csv(PATH)
  
  linkstats.1 = linkstats %>%
    group_by(linkId) %>%
    summarise_at(c("vol_car", "vol_bike"), sum)
  
  names = colnames(linkstats.1)
  newNames = character()
  
  for(name in names){
    
    if(str_detect(name, pattern = "vol_")){
      name = paste0(name, "_", runId)
    }
    
    newNames[length(newNames) + 1] = name
  }
  
  colnames(linkstats.1) = newNames
  
  linkstats.1
}

LINKSTATS = "C:/Users/ACER/Desktop/Uni/VSP/matsim-leipzig/output/linkStats.tsv"
COUNTS = "output/leipzig-v1.1-counts_Pkw.xml"


#### IMPORT TRAFFIC COUNTS ####
counts.xml <- read_xml(COUNTS) 

station = xml_find_all(counts.xml, "//count") %>%
  xml2::xml_attrs() %>%
  purrr::map_df(~as.list(.)) %>%
  readr::type_convert()

volume = xml_find_all(counts.xml, "//volume") %>%
  xml2::xml_attrs() %>%
  purrr::map_df(~as.list(.)) %>%
  readr::type_convert()

counts.raw = bind_cols(station, volume)

rm(counts.xml, station, volume)


#### IMPORT LEIPZIG NETWORK ####

NETWORK = "C:/Users/ACER/Desktop/Uni/VSP/matsim-leipzig/output/leipzig-v1.1-network-with-pt-drt.xml"

network = loadNetwork(NETWORK)
links = network$links

rm(network)

links.1 = links %>%
  select(id, type) 

#### IMPORT MATSIM COUNTS AND MERGE WITH TRAFFIC COUNTS####

linkstats.007 = readMATSimLinkStats(runId = "v1.2_run007")
linkstats.039 = readMATSimLinkStats(runId = "v1.0_run039")

counts.1 = left_join(x = counts.raw, y = linkstats.007, by = c("loc_id" = "linkId")) %>%
  left_join(linkstats.039, by = c("loc_id" = "linkId")) %>%
  left_join(links.1, by = c("loc_id" = "id"))

write_csv(counts.1, file = "C:/Users/ACER/Desktop/Uni/VSP/matsim-leipzig/output/link-volumes-join.csv")

rm(counts.raw, counts.1, linkstats.1, linkstats.raw, links, links.1, linkstats.007, linkstats.039)


#### ANALYSIS  ####

join.raw = read_csv("C:/Users/ACER/Desktop/Uni/VSP/matsim-leipzig/output/link-volumes-join.csv")

join.na = join.raw %>%
  filter(is.na(type) | is.na(vol_car_v1.0_run039) | is.na(vol_car_v1.2_run007))

log = paste(nrow(join.na), "of", nrow(join.raw), "count stations are not correctly matched to matsim links")
print(log)

join.1 = join.raw %>%
  filter(!is.na(type)) %>%
  filter(val > 0) %>%
  select(loc_id, val, starts_with("vol_car_"),type) %>%
  mutate_at(c("vol_car_v1.2_run007", "vol_car_v1.0_run039"), function(x){ x * 4}) %>%
  mutate(type = str_remove(type, pattern = "highway."),
         type = factor(type, levels = c("motorway", "primary", "secondary", "tertiary", "residential", "unclassified", "motorway_link", "primary_link", "trunk_link"))) %>%
  rename("vol_car_real" = "val") %>%
  select(-starts_with("vol_bike"))

suspicious = filter(join.1, str_detect(type, pattern = "_link"))
log = paste(nrow(suspicious), "matsim network links don't seem to be matched correctly")
print(log)
rm(log, suspicious, join.na)


#### Scatter Plot coloured according to road type ####

join.scatterplot = join.1 %>%
  pivot_longer(cols = c(vol_car_v1.0_run039, vol_car_v1.2_run007), names_to = "runId", values_to = "vol_sim", names_prefix = "vol_car_")

line.size = 1.0

ggplot(join.scatterplot, aes(x = vol_car_real, y = vol_sim, color = type)) +
  
  geom_point() +
  
  geom_abline(size = line.size) +
  
  geom_abline(slope = 1.2, intercept = 0) +
  
  geom_abline(slope = 0.8, intercept = 0) +
  
  scale_x_log10() +
  
  scale_y_log10() +
  
  facet_wrap(.~ runId) +
  
  labs(x = "Traffic volume from Count Stations", y = "Traffic volume from MATSim Data", color = "Road type:") +
  
  theme_bw() +
  
  theme(legend.position = "bottom", panel.background = element_rect(fill = "grey90"),
        panel.grid = element_line(colour = "white"))

savePlotAsJPG(name = "Traffic_Counts_Scatter_Plot")

#ggplot(join.1, aes(y = rel_vol, fill = type)) +
  
#  geom_boxplot() +
  
#  geom_hline(aes(yintercept = median.rel), linetype = "dashed") +
  
#  coord_cartesian(ylim = c(0, 2)) +
  
#  labs(y = "Relative Traffic Volume in MATSim", fill = "Road type") +
  
#  theme_bw() +
  
#  theme(axis.text.x=element_blank())

#savePlotAsJPG(name = "Traffic_Count_Boxplot")

#### Counts in each bin
options(scipen=999)
breaks = seq(0, 40000, 5000)
labels = character()

for(i in 1:length(breaks) - 1){
  
  label = paste0(breaks[i] / 1000, "k < ", breaks[i + 1] / 1000, "k")
  labels[i] = label
  rm(label)
}

#### data frame in long format for plotting
join.2 = join.1 %>%
  pivot_longer(cols = starts_with("vol_car"), names_to = "src", names_prefix = "vol_car_", values_to = "traffic_vol") %>%
  mutate(src = str_remove(src, pattern = "_vol"),
         traffic_bin = cut(traffic_vol, labels = labels, breaks = breaks, right = T)) %>%
  group_by(type, src, traffic_bin) %>%
  summarise(n = n()) %>%
  group_by(type, src) %>%
  mutate(share = n / sum(n)) %>%
  filter(!str_detect(type, pattern = "_link"))

ggplot(join.2, aes(x = traffic_bin, y = share, fill = type)) +
  
  geom_col() +
  
  facet_grid(src ~ type) +
  
  labs(x = "Traffic volume", y = "Share") +
  
  theme_bw() +
  
  theme(legend.position = "none", axis.text.x = element_text(angle = 90))

savePlotAsJPG(name = "Traffic_volume_distribution_by_road_type")

ggplot(filter(join.2, !type %in% c("residential", "unclassified")), aes(x = traffic_bin, y = share, fill = type)) +
  
  geom_col() +
  
  facet_grid(src ~ type) +
  
  labs(x = "Traffic volume", y = "Share") +
  
  theme_bw() +
  
  theme(legend.position = "none", axis.text.x = element_text(angle = 90))

savePlotAsJPG(name = "Traffic_volume_distribution_main_road_type")
