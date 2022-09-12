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
  
  labs(x = "Daily traffic volume from Count Stations", y = "Daily traffic volume from MATSim Data", color = "Road type:") +
  
  theme_bw() +
  
  theme(legend.position = "bottom", panel.background = element_rect(fill = "grey90"),
        panel.grid = element_line(colour = "white"))

savePlotAsJPG(name = "Traffic_Counts_Scatter_Plot")
rm(join.scatterplot)

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
  
  labs(x = "Daily traffic volume", y = "Share") +
  
  theme_bw() +
  
  theme(legend.position = "none", axis.text.x = element_text(angle = 90))

savePlotAsJPG(name = "Traffic_volume_distribution_by_road_type")

ggplot(filter(join.2, !type %in% c("residential", "unclassified")), aes(x = traffic_bin, y = share, fill = type)) +
  
  geom_col() +
  
  facet_grid(src ~ type) +
  
  labs(x = "Daily traffic volume", y = "Share") +
  
  theme_bw() +
  
  theme(legend.position = "none", axis.text.x = element_text(angle = 90))

savePlotAsJPG(name = "Traffic_volume_distribution_main_road_type")


#### create error plots ####

join.3 = join.2 %>%
  select(-share) %>% 
  pivot_wider(names_from = src, values_from = n, values_fill = 0.0) %>%
  filter(!is.na(traffic_bin)) %>%
  mutate(diff1 = v1.0_run039 - real,
         diff2 = v1.2_run007 - real,
         v1.0_run039 = diff1,
         v1.2_run007 = diff2) %>%
  select(-c(starts_with("diff"), real))

join.3.1 = pivot_longer(join.3, cols = c(v1.0_run039, v1.2_run007), names_to = "src", values_to = "diff") %>%
  filter(!type %in% c("residential", "unclassified"))

ggplot(join.3.1, aes(x = traffic_bin, y = diff, fill = type)) +
  
  geom_col() +
  
  facet_grid(src ~ type, scales = "free") +
  
  labs(x = "Difference to Count Stations", y = "Daily traffic volume") +
  
  coord_flip() +
  
  theme_bw() +
  
  theme(legend.position = "none")

savePlotAsJPG(name = "Difference_plot_main_road_type")

rm(join.3.1)

#### categorized data ####

cat.labels = c("grossly underestimated", "slightly underestimated", "well estimated", "slightly overestimated", "grossly overestimated")
cat.breaks = c(0, .6, .8, 1.2, 1.4, Inf)

join.4 = join.1 %>%
  mutate(rel_vol_v1.2_run007 = vol_car_v1.2_run007 / vol_car_real,
         rel_vol_v1.0_run039 = vol_car_v1.0_run039 / vol_car_real) %>%
  select(- starts_with("vol_car_")) %>%
  pivot_longer(cols = c(rel_vol_v1.2_run007, rel_vol_v1.0_run039), names_prefix = "rel_vol_", names_to = "src", values_to = "rel_vol") %>%
  mutate(quality = cut(rel_vol, breaks = cat.breaks, labels = cat.labels))

mean = mean(join.4$rel_vol, na.rm = T)
median = median(join.4$rel_vol, na.rm = T)

join.4.1 = join.4 %>%
  group_by(type, src, quality) %>%
  summarise(n = n()) %>%
  mutate(share = n / sum(n)) %>%
  filter(!type %in% c("residential", "unclassified"))

ggplot(join.4.1, aes(quality, share, fill = type)) +
  
  geom_col() +
  
  labs(y = "Share", x = "Quality category") +
  
  facet_grid(src ~ type) +
  
  theme_bw() +
  
  theme(legend.position = "none", axis.text.x = element_text(angle = 90))

savePlotAsJPG(name = "Estimation_quality_by_road_type")

n = join.1 %>%
  group_by(type) %>%
  summarise(n = n()) %>%
  mutate(share = n / sum(n)) %>%
  select(type, share)

summary = join.1 %>%
  mutate(diff_v1.2_run007 = abs(vol_car_v1.2_run007 - vol_car_real) / vol_car_real,
         diff_v1.0_run039= abs(vol_car_v1.0_run039 - vol_car_real) / vol_car_real) %>%
  group_by(type) %>%
  summarise_at(c("diff_v1.2_run007", "diff_v1.0_run039"), mean, na.rm = T) %>%
  left_join(n, by = "type")

score = summary %>%
  mutate(weightet_score_v1.2_run007 = diff_v1.2_run007 * share,
         weightet_score_v1.0_run039 = diff_v1.0_run039 * share) %>%
  select(type, starts_with("weightet_score_")) %>%
  pivot_longer(cols = -type, names_to = "runId", values_to = "score_road_type", names_prefix = "weightet_score_") %>%
  group_by(runId) %>%
  summarise(score = sum(score_road_type))

print(summary)
