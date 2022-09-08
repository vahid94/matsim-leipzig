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


LINKSTATS = "C:/Users/ACER/Desktop/Uni/VSP/matsim-leipzig/output/linkStats.tsv"
COUNTS = "C:/Users/ACER/Desktop/Uni/VSP/NaMAV/matsim-input-files/counts/counts_Pkw.xml"


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

linkstats.raw = read_csv(LINKSTATS)

linkstats.1 = linkstats.raw %>%
  group_by(linkId) %>%
  summarise_at(c("vol_car", "vol_bike"), sum)

write_csv(linkstats.1, file = "C:/Users/ACER/Desktop/Uni/VSP/matsim-leipzig/output/output-link-volumes.csv")

counts.1 = left_join(x = counts.raw, y = linkstats.1, by = c("loc_id" = "linkId")) %>%
  left_join(links.1, by = c("loc_id" = "id"))

write_csv(counts.1, file = "C:/Users/ACER/Desktop/Uni/VSP/matsim-leipzig/output/link-volumes-join.csv")

rm(counts.raw, counts.1, linkstats.1, linkstats.raw, links, links.1)


#### ANALYSIS  ####

join.raw = read_csv("C:/Users/ACER/Desktop/Uni/VSP/matsim-leipzig/output/link-volumes-join.csv")

join.na = join.raw %>%
  filter(is.na(vol_car) | is.na(vol_bike))

log = paste(nrow(join.na), "of", nrow(join.raw), "count stations are not correctly matched to matsim links")
print(log)

join.1 = join.raw %>%
  filter(!is.na(vol_car)) %>%
  select(loc_id, val, vol_car,type) %>%
  mutate(sim_vol = vol_car * 4) %>%
  rename("real_vol" = "val") %>%
  select(-vol_car) %>%
  mutate(error = real_vol - sim_vol,
         rel_vol = sim_vol / real_vol) %>% # contain single entry with real_vol = 0 <- error?
  filter(rel_vol != Inf)

suspicious = filter(join.1, str_detect(type, pattern = "_link"))
log = paste(nrow(suspicious), "matsim network links don't seem to be matched correctly")
print(log)

mean.rel = mean(join.1$rel_vol)
median.rel = median(join.1$rel_vol)

ggplot(join.1, aes(y = rel_vol, fill = type)) +
  
  geom_boxplot() +
  
  geom_hline(aes(yintercept = median.rel), linetype = "dashed") +
  
  coord_cartesian(ylim = c(0, 2)) +
  
  labs(y = "Relative Traffic Volume in MATSim", fill = "Road type") +
  
  theme_bw() +
  
  theme(axis.text.x=element_blank())

savePlotAsJPG(name = "Traffic_Count_Boxplot")

options(scipen=999)
breaks = seq(0, 40000, 5000)
labels = character()

for(i in 1:length(breaks) - 1){
  
  label = paste0(breaks[i] / 1000, "k < ", breaks[i + 1] / 1000, "k")
  labels[i] = label
  rm(label)
}

#### Scatter Plot coloured according to road type ####

error = 20

ggplot(join.1, aes(real_vol, sim_vol, color = type)) +
  
  geom_point() +
  
  scale_x_log10() +
  
  scale_y_log10() +
  
  coord_cartesian(xlim = c(150, 36000)) +
  
  labs(x = "Traffic volume from Count Stations", y = "Traffic volume in MATSim", color = "Road type") +
  
  theme_bw() +
  
  theme(panel.background = element_rect(fill = "grey90"), panel.grid = element_line(colour = "white"))

savePlotAsJPG(name = "Traffic_Counts_Scatter_Plot")
