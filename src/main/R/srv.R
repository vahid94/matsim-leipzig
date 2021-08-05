
library(tidyverse)
library(lubridate)
library(sf)


# setwd("C:/Users/chris/Development/matsim-scenarios/matsim-leipzig/src/main/R")

# Person data from srv
############################

persons <- read_delim("../../../../../shared-svn/NaMAV/data/SrV_2018/SrV2018_Einzeldaten_Leipzig_LE_SciUse_P2018.csv", delim = ";", 
                      locale = locale(decimal_mark = ",")) %>%
  filter(ST_CODE_NAME=="Leipzig") %>%
  filter(STICHTAG_WTAG <= 5) %>%
  filter(E_ANZ_WEGE >= 0)

# Avg. number of trips per person per day
per_day <- weighted.mean(persons$E_ANZ_WEGE, persons$GEWICHT_P)

# number of total tripos, based on residents
tt <- per_day * 600000



# Trip data from srV
#############################

trips <- read_delim("../../../../../shared-svn/NaMAV/data/SrV_2018/SrV2018_Einzeldaten_Leipzig_LE_SciUse_W2018.csv", delim = ";", 
                    col_types = cols(
                      V_ZIEL_LAND = col_character(),
                      GIS_LAENGE = col_double(),
                      E_HVM = col_number()
                    ), locale = locale(decimal_mark = ","))


# categories as defined in SrV mapped to matsim mode
lookup <- tibble(category = c(1, 2, 18, 19, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17), 
                 mode = c("walk", "bike", "bike", "bike", "car", "car", "car", "car", "ride", "ride", "ride", "pt", "pt", "pt", "pt", "pt", "pt", "pt", "pt"))


# trip distance groups
levels = c("0 - 1000", "1000 - 3000", "3000 - 5000", "5000 - 10000", "10000 - 20000", "20000+")
breaks = c(0, 1, 3, 5, 10, 20, Inf)


# Filter invalid modes and trip distances, also filter for weekdays
relevant <- trips %>%
  filter(ST_CODE_NAME=="Leipzig") %>%
  filter(E_HVM < 70) %>%
  filter(V_VM_LAENG < 70) %>%
  filter(GIS_LAENGE >= 0 & GIS_LAENGE_GUELTIG == -1) %>%
  filter(STICHTAG_WTAG <= 5) %>%
  mutate(dist_group = cut(GIS_LAENGE, breaks=breaks, labels=levels))

matched <- relevant %>% left_join(lookup, by=c("E_HVM"="category"))


srv <- matched %>%
  group_by(dist_group, mode) %>%
  summarise(trips=sum(GEWICHT_W)) %>%
  mutate(mode = fct_relevel(mode, "walk", "bike", "pt", "ride", "car")) %>%
  mutate(source = "srv")

# Trips in survey
st <- sum(srv$trips)

srv_aggr <- srv %>% group_by(mode) %>%
  summarise(trips=sum(trips) / st)


srv <- srv %>%
  mutate(scaled_trips=trips*(tt/st))

p1 <- ggplot(srv, aes(fill=mode, y=scaled_trips, x=dist_group)) +
  labs(subtitle = "Survey data", x="distance [m]") +
  geom_bar(position="stack", stat="identity")

# agents in city 115209, younger population is missing
# scale factor 5.2 instead of 4

# Read from trips and persons directly

f <- "\\\\sshfs.kr\\rakow@cluster.math.tu-berlin.de\\net\\ils4\\matsim-leipzig\\calibration\\runs\\003"
sim_scale <- 10

# breaks in meter
breaks = c(0, 1000, 2000, 5000, 10000, 20000, Inf)

shape <- st_read("../../../../../shared-svn/NaMAV/data/leipzig-utm32n/leipzig-utm32n.shp", crs=25832)

persons <- read_delim(Sys.glob(file.path(f, "*.output_persons.csv.gz")) , delim = ";", trim_ws = T, 
                      col_types = cols(
                        person = col_character(),
                        good_type = col_integer()
                      )) %>%
  st_as_sf(coords = c("first_act_x", "first_act_y"), crs = 25832) %>%
  st_filter(shape)


trips <- read_delim(Sys.glob(file.path(f, "*.output_trips.csv.gz")) , delim = ";", trim_ws = T, 
                    col_types = cols(
                      person = col_character()
                    )) %>%
  mutate(main_mode=longest_distance_mode) %>%  # TODO: can be removed later
  filter(main_mode!="freight") %>%
  semi_join(persons) %>%
  mutate(dist_group = cut(traveled_distance, breaks=breaks, labels=levels)) %>%
  filter(!is.na(dist_group))

sim <- trips %>%
  group_by(dist_group, main_mode) %>%
  summarise(trips=n()) %>%
  mutate(mode = fct_relevel(main_mode, "walk", "bike", "pt", "ride", "car")) %>%
  mutate(scaled_trips=sim_scale * trips) %>%
  mutate(source = "sim")

#######################################

# Read from matsim analysis

f <- "002.csv"
sim_scale <- 5.2

sim <- read_delim(f, delim = ";", trim_ws = T) %>%
  pivot_longer(cols=c("pt", "walk", "car", "bike", "ride"),
               names_to="mode",
               values_to="trips") %>%
  mutate(mode = fct_relevel(mode, "walk", "bike", "pt", "ride", "car")) %>%
  mutate(dist_group=sprintf("%g - %g", `distance - from [m]`, `distance to [m]`)) %>%
  mutate(dist_group=case_when(
    `distance to [m]`== max(`distance to [m]`) ~ sprintf("%g+", `distance - from [m]`),
    TRUE ~ `dist_group`
  )) %>%
  mutate(scaled_trips=trips*sim_scale) %>%
  mutate(source="sim")

#########################################



p2 <- ggplot(calib, aes(fill=mode, y=trips, x=dist_order)) +
  labs(subtitle = "Calibrated scenario", x="distance [m]") +
  geom_bar(position="stack", stat="identity")

g <- arrangeGrob(p1, p2, ncol = 2)
ggsave(filename = "modal-split.png", path = ".", g,
       width = 15, height = 5, device='png', dpi=300)



# Combined plot

total <- bind_rows(srv, sim)

dist_order <- factor(total$dist_group, level = c("0 - 1000", "1000 - 3000", "3000 - 5000", "5000 - 10000", 
                                                 "10000 - 20000"))
# Maps left overgroups
dist_order <- fct_explicit_na(dist_order, "20000+")

ggplot(total, aes(fill=mode, y=scaled_trips, x=source)) +
  labs(subtitle = paste("Leipzig scenario", f), x="distance [m]") +
  geom_bar(position="stack", stat="identity", width = 0.5) +
  facet_wrap(dist_order, nrow = 1)


# Needed for adding short distance trips

calib_sum <- sum(calib$trips)
calib_aggr <- calib %>%
  group_by(dist_group) %>%
  summarise(share=sum(trips) / calib_sum)

# Needed share of trips
tripShare <- 0.2418
shortDistance <- sum(filter(calib, dist_group=="0 - 1000")$trips)
numTrips = (shortDistance - calib_sum * tripShare) / (tripShare - 1)



