#MASTERSKRIPT for car free areas (using output for dashboard)

####Libraries####
library(gridExtra)
library(tidyr)
library(tidyverse)
library(lubridate)
library(viridis)
library(ggsci)
library(sf)
library(dplyr)
library(ggplot2)
library(matsim)
library(purrr)
library(networkD3)
library(alluvial)

####File Paths####

#BASE CASE - path where to find the MATSim ouput

f <- "/Users/mkreuschnervsp/Desktop/VSP_projects/02_NaMaV/MATSim/25pct_base/20220811/"

#POLICY CASE 99 - path where to find the MATSim ouput
g <- "/Users/mkreuschnervsp/Desktop/VSP_projects/02_NaMaV/MATSim/25pct_scenario99/20220811/"

#POLICY CASE 95 - path where to find the MATSim ouput
h <- "/Users/mkreuschnervsp/Desktop/VSP_projects/02_NaMaV/MATSim/25pct_scenario95/20220811/"

#POLICY CASE 90 - path where to find the MATSim ouput
i <- "/Users/mkreuschnervsp/Desktop/VSP_projects/02_NaMaV/MATSim/25pct_scenario90/20220811/"

#Shape Files
region_shp_path <- "shapefiles/Leipzig_puffer.shp"
city_shp_path <- "shapefiles/Leipzig_stadt.shp"
area90_shp_path <- "shapefiles/Zonen90_update.shp"
area95_shp_path <-"shapefiles/Zonen95_update.shp"
area99_shp_path <- "shapefiles/Zonen99_update.shp"

#Trips Files
Base_trips_path <- "matsim_output/base/leipzig-flexa-25pct-scaledFleet-base_noDepot.output_trips.csv"
Area90_trips_path <- "matsim_output/carfree90pct/leipzig-flexa-25pct-scaledFleet-carfree90pct_noDepot.output_trips.csv.gz"
Area95_trips_path <- "matsim_output/carfree95pct/leipzig-flexa-25pct-scaledFleet-carfree95pct_noDepot.output_trips.csv.gz"
Area99_trips_path <- "matsim_output/carfree99pct/leipzig-flexa-25pct-scaledFleet-carfree99pct_noDepot.output_trips.csv.gz"

#Legs Files

Base_legs_path <- "matsim_output/base/leipzig-flexa-25pct-scaledFleet-base_noDepot.output_legs.csv.gz"
Area90_legs_path <- "matsim_output/carfree90pct/leipzig-flexa-25pct-scaledFleet-carfree90pct_noDepot.output_legs.csv.gz"
Area95_legs_path <- "matsim_output/carfree95pct/leipzig-flexa-25pct-scaledFleet-carfree95pct_noDepot.output_legs.csv.gz"
Area99_legs_path <- "matsim_output/carfree99pct/leipzig-flexa-25pct-scaledFleet-carfree99pct_noDepot.output_legs.csv.gz"


####Reading Files####
RegionShape <- st_read(region_shp_path, crs=25832)
CityShape <- st_read(city_shp_path, crs=25832) #city of Leipzig
AreaShape90 <- st_read(area90_shp_path, crs=25832) #area of 90%
AreaShape95 <- st_read(area95_shp_path, crs=25832) #area of 95%
AreaShape99 <- st_read(area99_shp_path, crs=25832) #area of 99%

#loading trips tables
BASEtripsTable <- read.csv2(Base_trips_path, dec = ".")
AREA90tripsTable <- readTripsTable(pathToMATSimOutputDirectory = Area90_trips_path)
AREA95tripsTable <- readTripsTable(pathToMATSimOutputDirectory = Area95_trips_path)
AREA99tripsTable <- readTripsTable(pathToMATSimOutputDirectory = Area99_trips_path)

#loading legs tables
BASELegsTable <- read_delim(Base_legs_path, delim= ";")
AREA90LegsTable <- read_delim(Area90_legs_path, delim= ";")
AREA95LegsTable <- read_delim(Area95_legs_path, delim= ";")
AREA99LegsTable <- read_delim(Area99_legs_path, delim= ";")

####Parameters####
#SIMULATION SCALE (25% -> 4)
sim_scale <- 4

#BREAKING DIFFERENT DISTANCES IN M
breaks = c(0, 1000, 2000, 5000, 10000, 20000, Inf)

## INPUT DEFINITION - files needed TRIPS, LEGS, PERSONS, NETWORK, SHAPEFILES ##

runID <- "Base_Region"
runID <- "Base_Leipzig"
runID <- "Base_Area90"
runID <- "Base_Area95"
runID <- "Base_Area99"


####TRIPS SPATIAL FILTER####
BaseTripsRegion <- filterByRegion(BASEtripsTable,RegionShape,crs=25832,start.inshape = TRUE,end.inshape = TRUE)
BaseTripsCity <- filterByRegion(BASEtripsTable,CityShape,crs=25832,start.inshape = TRUE,end.inshape = TRUE)
BaseTrips90 <- filterByRegion(BASEtripsTable,AreaShape90,crs=25832,start.inshape = TRUE,end.inshape = TRUE)
BaseTrips95 <- filterByRegion(BASEtripsTable,AreaShape95,crs=25832,start.inshape = TRUE,end.inshape = TRUE)
BaseTrips99 <- filterByRegion(BASEtripsTable,AreaShape99,crs=25832,start.inshape = TRUE,end.inshape = TRUE)
AREA90TripsRegion <- filterByRegion(AREA90tripsTable,RegionShape,crs=25832,start.inshape = TRUE,end.inshape = TRUE)
AREA90TripsCity <- filterByRegion(AREA90tripsTable,CityShape,crs=25832,start.inshape = TRUE,end.inshape = TRUE)
AREA90Trips90 <- filterByRegion(AREA90tripsTable,AreaShape90,crs=25832,start.inshape = TRUE,end.inshape = TRUE)
AREA90Trips95 <- filterByRegion(AREA90tripsTable,AreaShape95,crs=25832,start.inshape = TRUE,end.inshape = TRUE)
AREA90Trips99 <- filterByRegion(AREA90tripsTable,AreaShape99,crs=25832,start.inshape = TRUE,end.inshape = TRUE)
AREA95TripsRegion <- filterByRegion(AREA95tripsTable,RegionShape,crs=25832,start.inshape = TRUE,end.inshape = TRUE)
AREA95TripsCity <- filterByRegion(AREA95tripsTable,CityShape,crs=25832,start.inshape = TRUE,end.inshape = TRUE)
AREA95Trips90 <- filterByRegion(AREA95tripsTable,AreaShape90,crs=25832,start.inshape = TRUE,end.inshape = TRUE)
AREA95Trips95 <- filterByRegion(AREA95tripsTable,AreaShape95,crs=25832,start.inshape = TRUE,end.inshape = TRUE)
AREA95Trips99 <- filterByRegion(AREA95tripsTable,AreaShape99,crs=25832,start.inshape = TRUE,end.inshape = TRUE)
AREA99TripsRegion <- filterByRegion(AREA99tripsTable,RegionShape,crs=25832,start.inshape = TRUE,end.inshape = TRUE)
AREA99TripsCity <- filterByRegion(AREA99tripsTable,CityShape,crs=25832,start.inshape = TRUE,end.inshape = TRUE)
AREA99Trips90 <- filterByRegion(AREA99tripsTable,AreaShape90,crs=25832,start.inshape = TRUE,end.inshape = TRUE)
AREA99Trips95 <- filterByRegion(AREA99tripsTable,AreaShape95,crs=25832,start.inshape = TRUE,end.inshape = TRUE)
AREA99Trips99 <- filterByRegion(AREA99tripsTable,AreaShape99,crs=25832,start.inshape = TRUE,end.inshape = TRUE)

####TRIPS MORE FILTERS####

BaseTripsRegion <- BaseTripsRegion %>%
  filter(main_mode!="freight") %>%
  mutate(dist_group = cut(traveled_distance, breaks=breaks, labels= c("0-1000","1000-2000","2000-5000","5000-10000","10000-20000",">20000"))) %>%
  filter(!is.na(dist_group))

BaseTripsCity <- BaseTripsCity %>%
  filter(main_mode!="freight") %>%
  mutate(dist_group = cut(traveled_distance, breaks=breaks, labels= c("0-1000","1000-2000","2000-5000","5000-10000","10000-20000",">20000"))) %>%
  filter(!is.na(dist_group))

BaseTrips90 <- BaseTrips90 %>%
  filter(main_mode!="freight") %>%
  mutate(dist_group = cut(traveled_distance, breaks=breaks, labels= c("0-1000","1000-2000","2000-5000","5000-10000","10000-20000",">20000"))) %>%
  filter(!is.na(dist_group))

BaseTrips95 <- BaseTrips95 %>%
  filter(main_mode!="freight") %>%
  mutate(dist_group = cut(traveled_distance, breaks=breaks, labels= c("0-1000","1000-2000","2000-5000","5000-10000","10000-20000",">20000"))) %>%
  filter(!is.na(dist_group))

BaseTrips99 <- BaseTrips99 %>%
  filter(main_mode!="freight") %>%
  mutate(dist_group = cut(traveled_distance, breaks=breaks, labels= c("0-1000","1000-2000","2000-5000","5000-10000","10000-20000",">20000"))) %>%
  filter(!is.na(dist_group))

AREA90TripsRegion <- AREA90TripsRegion %>%
  filter(main_mode!="freight") %>%
  mutate(dist_group = cut(traveled_distance, breaks=breaks, labels= c("0-1000","1000-2000","2000-5000","5000-10000","10000-20000",">20000"))) %>%
  filter(!is.na(dist_group))

AREA90TripsCity <- AREA90TripsCity %>%
  filter(main_mode!="freight") %>%
  mutate(dist_group = cut(traveled_distance, breaks=breaks, labels= c("0-1000","1000-2000","2000-5000","5000-10000","10000-20000",">20000"))) %>%
  filter(!is.na(dist_group))

AREA90Trips90 <- AREA90Trips90 %>%
  filter(main_mode!="freight") %>%
  mutate(dist_group = cut(traveled_distance, breaks=breaks, labels= c("0-1000","1000-2000","2000-5000","5000-10000","10000-20000",">20000"))) %>%
  filter(!is.na(dist_group))

AREA90Trips95 <- AREA90Trips95 %>%
  filter(main_mode!="freight") %>%
  mutate(dist_group = cut(traveled_distance, breaks=breaks, labels= c("0-1000","1000-2000","2000-5000","5000-10000","10000-20000",">20000"))) %>%
  filter(!is.na(dist_group))

AREA90Trips99 <- AREA90Trips99 %>%
  filter(main_mode!="freight") %>%
  mutate(dist_group = cut(traveled_distance, breaks=breaks, labels= c("0-1000","1000-2000","2000-5000","5000-10000","10000-20000",">20000"))) %>%
  filter(!is.na(dist_group))

AREA95TripsRegion <- AREA95TripsRegion %>%
  filter(main_mode!="freight") %>%
  mutate(dist_group = cut(traveled_distance, breaks=breaks, labels= c("0-1000","1000-2000","2000-5000","5000-10000","10000-20000",">20000"))) %>%
  filter(!is.na(dist_group))

AREA95TripsCity <- AREA95TripsCity %>%
  filter(main_mode!="freight") %>%
  mutate(dist_group = cut(traveled_distance, breaks=breaks, labels= c("0-1000","1000-2000","2000-5000","5000-10000","10000-20000",">20000"))) %>%
  filter(!is.na(dist_group))

AREA95Trips90 <- AREA95Trips90 %>%
  filter(main_mode!="freight") %>%
  mutate(dist_group = cut(traveled_distance, breaks=breaks, labels= c("0-1000","1000-2000","2000-5000","5000-10000","10000-20000",">20000"))) %>%
  filter(!is.na(dist_group))

AREA95Trips95 <- AREA95Trips95 %>%
  filter(main_mode!="freight") %>%
  mutate(dist_group = cut(traveled_distance, breaks=breaks, labels= c("0-1000","1000-2000","2000-5000","5000-10000","10000-20000",">20000"))) %>%
  filter(!is.na(dist_group))

AREA95Trips99 <- AREA95Trips99 %>%
  filter(main_mode!="freight") %>%
  mutate(dist_group = cut(traveled_distance, breaks=breaks, labels= c("0-1000","1000-2000","2000-5000","5000-10000","10000-20000",">20000"))) %>%
  filter(!is.na(dist_group))

AREA99TripsRegion <- AREA99TripsRegion %>%
  filter(main_mode!="freight") %>%
  mutate(dist_group = cut(traveled_distance, breaks=breaks, labels= c("0-1000","1000-2000","2000-5000","5000-10000","10000-20000",">20000"))) %>%
  filter(!is.na(dist_group))

AREA99TripsCity <- AREA99TripsCity %>%
  filter(main_mode!="freight") %>%
  mutate(dist_group = cut(traveled_distance, breaks=breaks, labels= c("0-1000","1000-2000","2000-5000","5000-10000","10000-20000",">20000"))) %>%
  filter(!is.na(dist_group))

AREA99Trips90 <- AREA99Trips90 %>%
  filter(main_mode!="freight") %>%
  mutate(dist_group = cut(traveled_distance, breaks=breaks, labels= c("0-1000","1000-2000","2000-5000","5000-10000","10000-20000",">20000"))) %>%
  filter(!is.na(dist_group))

AREA99Trips95 <- AREA99Trips95 %>%
  filter(main_mode!="freight") %>%
  mutate(dist_group = cut(traveled_distance, breaks=breaks, labels= c("0-1000","1000-2000","2000-5000","5000-10000","10000-20000",">20000"))) %>%
  filter(!is.na(dist_group))

AREA99Trips99 <- AREA99Trips99 %>%
  filter(main_mode!="freight") %>%
  mutate(dist_group = cut(traveled_distance, breaks=breaks, labels= c("0-1000","1000-2000","2000-5000","5000-10000","10000-20000",">20000"))) %>%
  filter(!is.na(dist_group))





####LEGS SPATIAL FILTER ####

BaseLegsRegion <- filterByRegion(BASELegsTable,RegionShape,crs=25832,start.inshape = TRUE,end.inshape = TRUE)
BaseLegsCity <- filterByRegion(BASELegsTable,CityShape,crs=25832,start.inshape = TRUE,end.inshape = TRUE)
BaseLegs90 <- filterByRegion(BASELegsTable,AreaShape90,crs=25832,start.inshape = TRUE,end.inshape = TRUE)
BaseLegs95 <- filterByRegion(BASELegsTable,AreaShape95,crs=25832,start.inshape = TRUE,end.inshape = TRUE)
BaseLegs99 <- filterByRegion(BASELegsTable,AreaShape99,crs=25832,start.inshape = TRUE,end.inshape = TRUE)
AREA90LegsRegion <- filterByRegion(AREA90LegsTable,RegionShape,crs=25832,start.inshape = TRUE,end.inshape = TRUE)
AREA90LegsCity <- filterByRegion(AREA90LegsTable,CityShape,crs=25832,start.inshape = TRUE,end.inshape = TRUE)
AREA90Legs90 <- filterByRegion(AREA90LegsTable,AreaShape90,crs=25832,start.inshape = TRUE,end.inshape = TRUE)
AREA90Legs95 <- filterByRegion(AREA90LegsTable,AreaShape95,crs=25832,start.inshape = TRUE,end.inshape = TRUE)
AREA90Legs99 <- filterByRegion(AREA90LegsTable,AreaShape99,crs=25832,start.inshape = TRUE,end.inshape = TRUE)
AREA95LegsRegion <- filterByRegion(AREA95LegsTable,RegionShape,crs=25832,start.inshape = TRUE,end.inshape = TRUE)
AREA95LegsCity <- filterByRegion(AREA95LegsTable,CityShape,crs=25832,start.inshape = TRUE,end.inshape = TRUE)
AREA95Legs90 <- filterByRegion(AREA95LegsTable,AreaShape90,crs=25832,start.inshape = TRUE,end.inshape = TRUE)
AREA95Legs95 <- filterByRegion(AREA95LegsTable,AreaShape95,crs=25832,start.inshape = TRUE,end.inshape = TRUE)
AREA95Legs99 <- filterByRegion(AREA95LegsTable,AreaShape99,crs=25832,start.inshape = TRUE,end.inshape = TRUE)
AREA99LegsRegion <- filterByRegion(AREA99LegsTable,RegionShape,crs=25832,start.inshape = TRUE,end.inshape = TRUE)
AREA99LegsCity <- filterByRegion(AREA99LegsTable,CityShape,crs=25832,start.inshape = TRUE,end.inshape = TRUE)
AREA99Legs90 <- filterByRegion(AREA99LegsTable,AreaShape90,crs=25832,start.inshape = TRUE,end.inshape = TRUE)
AREA99Legs95 <- filterByRegion(AREA99LegsTable,AreaShape95,crs=25832,start.inshape = TRUE,end.inshape = TRUE)
AREA99Legs99 <- filterByRegion(AREA99LegsTable,AreaShape99,crs=25832,start.inshape = TRUE,end.inshape = TRUE)
####LEGS MORE FILTERS ####

#### #0 General Numbers ####

# Average Distance
average_distance_trips <- mean(BASEtripsTable$traveled_distance)
average_distance_legs <- mean(BASELegsTable$distance)

# Average Distance by Mode
average_distance_mode_trips <- function(x){
  x %>%
    group_by(main_mode) %>%
    summarise(mean(traveled_distance))

}
average_distance_mode_legs <- function(x){
  x %>%
    group_by(mode) %>%
    summarise(mean(distance))

}

average_distance_base_case_trips <- average_distance_mode_trips(BASEtripsTable)
average_distance_base_case_legs <- average_distance_mode_trips(BASElegsTable)

#### #1.1 ModalSplit - trips based - main mode (count) ####
# x) Functions

modal_split_trips_main_mode <- function(x){
  x %>%
    count(main_mode) %>%
    mutate(percent = 100*n/sum(n))
}

# a) Region

ms_main_mode_trips_BaseRegion <- modal_split_trips_main_mode(BaseTripsRegion)
ms_main_mode_trips_Area90Region <- modal_split_trips_main_mode(AREA90TripsRegion)
ms_main_mode_trips_Area95Region <- modal_split_trips_main_mode(AREA95TripsRegion)
ms_main_mode_trips_Area99Region <- modal_split_trips_main_mode(AREA99TripsRegion)

ms_main_mode_trips_Region <- bind_rows(ms_main_mode_trips_BaseRegion,
                                       ms_main_mode_trips_Area90Region,
                                       ms_main_mode_trips_Area95Region,
                                       ms_main_mode_trips_Area99Region, .id = "id")

ms_trips_Region_plot <- ms_main_mode_trips_Region %>%
  group_by(id) %>%
  ggplot(mapping= aes(x = main_mode, y= n, fill = id))+
  geom_col(position = "dodge")+
  ylab("Number of Trips")+
  labs(fill = "Case")+
  scale_fill_discrete(labels= c("Base Case", "90%","95%", "99%" ))
ms_trips_Region_plot

# b) City

ms_main_mode_trips_BaseCity <- modal_split_trips_main_mode(BaseTripsCity)
ms_main_mode_trips_Area90City <- modal_split_trips_main_mode(AREA90TripsCity)
ms_main_mode_trips_Area95City <- modal_split_trips_main_mode(AREA95TripsCity)
ms_main_mode_trips_Area99City <- modal_split_trips_main_mode(AREA99TripsCity)

ms_main_mode_trips_CITY <- bind_rows(ms_main_mode_BaseCity,
                                     ms_main_mode_trips_Area90City,
                                     ms_main_mode_trips_Area95City,
                                     ms_main_mode_trips_Area99City, .id = "id")

ms_trips_city_plot <- ms_main_mode_trips_CITY %>%
  group_by(id) %>%
  ggplot(mapping= aes(x = main_mode, y= n, fill = id))+
  geom_col(position = "dodge")+
  ylab("Number of Trips")+
  labs(fill = "Case")+
  scale_fill_discrete(labels= c("Base Case", "90%","95%", "99%" ))
ms_trips_city_plot


# c) area90

ms_main_mode_trips_Base_90 <- modal_split_trips_main_mode(BaseTrips90)
ms_main_mode_trips_Area90_90 <- modal_split_trips_main_mode(AREA90Trips90)
ms_main_mode_trips_Area95_90 <- modal_split_trips_main_mode(AREA95Trips90)
ms_main_mode_trips_Area99_90 <- modal_split_trips_main_mode(AREA99Trips90)

ms_main_mode_trips_90 <- bind_rows(ms_main_mode_trips_Base_90,
                                   ms_main_mode_trips_Area90_90,
                                   ms_main_mode_trips_Area95_90,
                                   ms_main_mode_trips_Area99_90, .id = "id")

ms_trips_90_plot <- ms_main_mode_trips_90 %>%
  group_by(id) %>%
  ggplot(mapping= aes(x = main_mode, y= n, fill = id))+
  geom_col(position = "dodge")+
  ylab("Number of Trips")+
  labs(fill = "Case")+
  scale_fill_discrete(labels= c("Base Case", "90%","95%", "99%" ))
ms_trips_90_plot

# d) area95

ms_main_mode_trips_Base_95 <- modal_split_trips_main_mode(BaseTrips95)
ms_main_mode_trips_Area90_95 <- modal_split_trips_main_mode(AREA90Trips95)
ms_main_mode_trips_Area95_95 <- modal_split_trips_main_mode(AREA95Trips95)
ms_main_mode_trips_Area99_95 <- modal_split_trips_main_mode(AREA99Trips95)

ms_main_mode_trips_95 <- bind_rows(ms_main_mode_trips_Base_95,
                                   ms_main_mode_trips_Area90_95,
                                   ms_main_mode_trips_Area95_95,
                                   ms_main_mode_trips_Area99_95, .id = "id")

ms_trips_95_plot <- ms_main_mode_trips_95 %>%
  group_by(id) %>%
  ggplot(mapping= aes(x = main_mode, y= n, fill = id))+
  geom_col(position = "dodge")+
  ylab("Number of Trips")+
  labs(fill = "Case")+
  scale_fill_discrete(labels= c("Base Case", "90%","95%", "99%" ))
ms_trips_90_plot

# e) area99

ms_main_mode_trips_Base_99 <- modal_split_trips_main_mode(BaseTrips99)
ms_main_mode_trips_Area90_99 <- modal_split_trips_main_mode(AREA90Trips99)
ms_main_mode_trips_Area95_99 <- modal_split_trips_main_mode(AREA95Trips99)
ms_main_mode_trips_Area99_99 <- modal_split_trips_main_mode(AREA99Trips99)

ms_main_mode_trips_99 <- bind_rows(ms_main_mode_trips_Base_99,
                                   ms_main_mode_trips_Area90_99,
                                   ms_main_mode_trips_Area95_99,
                                   ms_main_mode_trips_Area99_99, .id = "id")

ms_trips_99_plot <- ms_main_mode_trips_99 %>%
  group_by(id) %>%
  ggplot(mapping= aes(x = main_mode, y= n, fill = id))+
  geom_col(position = "dodge")+
  ylab("Number of Trips")+
  labs(fill = "Case")+
  scale_fill_discrete(labels= c("Base Case", "90%","95%", "99%" ))
ms_trips_99_plot



#### #1.2 Modal Split - trips based - distance ####

modal_split_trips_distance <- function(x){
  x %>%
    group_by(mode) %>%
    summarise(distance = sum(distance)) %>%
    mutate(percent = round(100*distance/sum(distance),2))
}

# a) Region

ms_distance_trips_BaseRegion <- modal_split_trips_distance(BaseTripsRegion)
ms_distance_trips_Area90Region <- modal_split_trips_distance(AREA90TripsRegion)
ms_distance_trips_Area95Region <- modal_split_trips_distance(AREA95TripsRegion)
ms_distance_trips_Area99Region <- modal_split_trips_distance(AREA99TripsRegion)

ms_distance_trips_Region <- bind_rows(ms_distance_trips_BaseRegion,
                                       ms_distance_trips_Area90Region,
                                       ms_distance_trips_Area95Region,
                                       ms_distance_trips_Area99Region, .id = "id")

ms_trips_Region_plot <- ms_distance_trips_Region %>%
  group_by(id) %>%
  ggplot(mapping= aes(x = distance, y= n, fill = id))+
  geom_col(position = "dodge")+
  ylab("Distance")+
  labs(fill = "Case")+
  scale_fill_discrete(labels= c("Base Case", "90%","95%", "99%" ))
ms_trips_Region_plot

# b) City

ms_distance_trips_BaseCity <- modal_split_trips_distance(BaseTripsCity)
ms_distance_trips_Area90City <- modal_split_trips_distance(AREA90TripsCity)
ms_distance_trips_Area95City <- modal_split_trips_distance(AREA95TripsCity)
ms_distance_trips_Area99City <- modal_split_trips_distance(AREA99TripsCity)

ms_distance_trips_CITY <- bind_rows(ms_distance_BaseCity,
                                     ms_distance_trips_Area90City,
                                     ms_distance_trips_Area95City,
                                     ms_distance_trips_Area99City, .id = "id")

ms_trips_city_plot <- ms_distance_trips_CITY %>%
  group_by(id) %>%
  ggplot(mapping= aes(x = distance, y= n, fill = id))+
  geom_col(position = "dodge")+
  ylab("Distance")+
  labs(fill = "Case")+
  scale_fill_discrete(labels= c("Base Case", "90%","95%", "99%" ))
ms_trips_city_plot


# c) area90

ms_distance_trips_Base_90 <- modal_split_trips_distance(BaseTrips90)
ms_distance_trips_Area90_90 <- modal_split_trips_distance(AREA90Trips90)
ms_distance_trips_Area95_90 <- modal_split_trips_distance(AREA95Trips90)
ms_distance_trips_Area99_90 <- modal_split_trips_distance(AREA99Trips90)

ms_distance_trips_90 <- bind_rows(ms_distance_trips_Base_90,
                                   ms_distance_trips_Area90_90,
                                   ms_distance_trips_Area95_90,
                                   ms_distance_trips_Area99_90, .id = "id")

ms_trips_90_plot <- ms_distance_trips_90 %>%
  group_by(id) %>%
  ggplot(mapping= aes(x = distance, y= n, fill = id))+
  geom_col(position = "dodge")+
  ylab("Distance")+
  labs(fill = "Case")+
  scale_fill_discrete(labels= c("Base Case", "90%","95%", "99%" ))
ms_trips_90_plot

# d) area95

ms_distance_trips_Base_95 <- modal_split_trips_distance(BaseTrips95)
ms_distance_trips_Area90_95 <- modal_split_trips_distance(AREA90Trips95)
ms_distance_trips_Area95_95 <- modal_split_trips_distance(AREA95Trips95)
ms_distance_trips_Area99_95 <- modal_split_trips_distance(AREA99Trips95)

ms_distance_trips_95 <- bind_rows(ms_distance_trips_Base_95,
                                   ms_distance_trips_Area90_95,
                                   ms_distance_trips_Area95_95,
                                   ms_distance_trips_Area99_95, .id = "id")

ms_trips_95_plot <- ms_distance_trips_95 %>%
  group_by(id) %>%
  ggplot(mapping= aes(x = distance, y= n, fill = id))+
  geom_col(position = "dodge")+
  ylab("Distance")+
  labs(fill = "Case")+
  scale_fill_discrete(labels= c("Base Case", "90%","95%", "99%" ))
ms_trips_90_plot

# e) area99

ms_distance_trips_Base_99 <- modal_split_trips_distance(BaseTrips99)
ms_distance_trips_Area90_99 <- modal_split_trips_distance(AREA90Trips99)
ms_distance_trips_Area95_99 <- modal_split_trips_distance(AREA95Trips99)
ms_distance_trips_Area99_99 <- modal_split_trips_distance(AREA99Trips99)

ms_distance_trips_99 <- bind_rows(ms_distance_trips_Base_99,
                                   ms_distance_trips_Area90_99,
                                   ms_distance_trips_Area95_99,
                                   ms_distance_trips_Area99_99, .id = "id")

ms_trips_99_plot <- ms_distance_trips_99 %>%
  group_by(id) %>%
  ggplot(mapping= aes(x = distance, y= n, fill = id))+
  geom_col(position = "dodge")+
  ylab("Distance")+
  labs(fill = "Case")+
  scale_fill_discrete(labels= c("Base Case", "90%","95%", "99%" ))
ms_trips_99_plot


#### #1.3 Modal Split - legs based - main mode (count) ####

modal_split_legs_mode <- function(x){
  ####

  x %>%
    count(mode) %>%
    mutate(percent = 100*n/sum(n))
}

# a) Region

ms_mode_legs_BaseRegion <- modal_split_legs_mode(BaseLegsRegion)
ms_mode_legs_Area90Region <- modal_split_legs_mode(AREA90LegsRegion)
ms_mode_legs_Area95Region <- modal_split_legs_mode(AREA95LegsRegion)
ms_mode_legs_Area99Region <- modal_split_legs_mode(AREA99LegsRegion)

ms_mode_legs_Region <- bind_rows(ms_mode_legs_BaseRegion,
                                       ms_mode_legs_Area90Region,
                                       ms_mode_legs_Area95Region,
                                       ms_mode_legs_Area99Region, .id = "id")

ms_legs_Region_plot <- ms_mode_legs_Region %>%
  group_by(id) %>%
  ggplot(mapping= aes(x = mode, y= n, fill = id))+
  geom_col(position = "dodge")+
  ylab("Number of legs")+
  labs(fill = "Case")+
  scale_fill_discrete(labels= c("Base Case", "90%","95%", "99%" ))
ms_legs_Region_plot

# b) City

ms_mode_legs_BaseCity <- modal_split_legs_mode(BaseLegsCity)
ms_mode_legs_Area90City <- modal_split_legs_mode(AREA90LegsCity)
ms_mode_legs_Area95City <- modal_split_legs_mode(AREA95LegsCity)
ms_mode_legs_Area99City <- modal_split_legs_mode(AREA99LegsCity)

ms_mode_legs_CITY <- bind_rows(ms_mode_legs_BaseCity,
                                     ms_mode_legs_Area90City,
                                     ms_mode_legs_Area95City,
                                     ms_mode_legs_Area99City, .id = "id")

ms_legs_city_plot <- ms_mode_legs_CITY %>%
  group_by(id) %>%
  ggplot(mapping= aes(x = mode, y= n, fill = id))+
  geom_col(position = "dodge")+
  ylab("Number of legs")+
  labs(fill = "Case")+
  scale_fill_discrete(labels= c("Base Case", "90%","95%", "99%" ))
ms_legs_city_plot


# c) area90

ms_mode_legs_Base_90 <- modal_split_legs_mode(BaseLegs90)
ms_mode_legs_Area90_90 <- modal_split_legs_mode(AREA90Legs90)
ms_mode_legs_Area95_90 <- modal_split_legs_mode(AREA95Legs90)
ms_mode_legs_Area99_90 <- modal_split_legs_mode(AREA99Legs90)

ms_mode_legs_90 <- bind_rows(ms_mode_legs_Base_90,
                                   ms_mode_legs_Area90_90,
                                   ms_mode_legs_Area95_90,
                                   ms_mode_legs_Area99_90, .id = "id")

ms_legs_90_plot <- ms_mode_legs_90 %>%
  group_by(id) %>%
  ggplot(mapping= aes(x = mode, y= n, fill = id))+
  geom_col(position = "dodge")+
  ylab("Number of legs")+
  labs(fill = "Case")+
  scale_fill_discrete(labels= c("Base Case", "90%","95%", "99%" ))
ms_legs_90_plot

# d) area95

ms_mode_legs_Base_95 <- modal_split_legs_mode(BaseLegs95)
ms_mode_legs_Area90_95 <- modal_split_legs_mode(AREA90Legs95)
ms_mode_legs_Area95_95 <- modal_split_legs_mode(AREA95Legs95)
ms_mode_legs_Area99_95 <- modal_split_legs_mode(AREA99Legs95)

ms_mode_legs_95 <- bind_rows(ms_mode_legs_Base_95,
                                   ms_mode_legs_Area90_95,
                                   ms_mode_legs_Area95_95,
                                   ms_mode_legs_Area99_95, .id = "id")

ms_legs_95_plot <- ms_mode_legs_95 %>%
  group_by(id) %>%
  ggplot(mapping= aes(x = mode, y= n, fill = id))+
  geom_col(position = "dodge")+
  ylab("Number of legs")+
  labs(fill = "Case")+
  scale_fill_discrete(labels= c("Base Case", "90%","95%", "99%" ))
ms_legs_90_plot

# e) area99

ms_mode_legs_Base_99 <- modal_split_legs_mode(BaseLegs99)
ms_mode_legs_Area90_99 <- modal_split_legs_mode(AREA90Legs99)
ms_mode_legs_Area95_99 <- modal_split_legs_mode(AREA95Legs99)
ms_mode_legs_Area99_99 <- modal_split_legs_mode(AREA99Legs99)

ms_mode_legs_99 <- bind_rows(ms_mode_legs_Base_99,
                                   ms_mode_legs_Area90_99,
                                   ms_mode_legs_Area95_99,
                                   ms_mode_legs_Area99_99, .id = "id")

ms_legs_99_plot <- ms_mode_legs_99 %>%
  group_by(id) %>%
  ggplot(mapping= aes(x = mode, y= n, fill = id))+
  geom_col(position = "dodge")+
  ylab("Number of legs")+
  labs(fill = "Case")+
  scale_fill_discrete(labels= c("Base Case", "90%","95%", "99%" ))
ms_trips_99_plot


#### #1.4 Modal Split - legs based - distance ####
modal_split_legs_distance <- function(x){
  x %>%
    group_by(mode) %>%
    summarise(distance = sum(distance)) %>%
    mutate(percent = round(100*distance/sum(distance),2))
}

# a) Region

ms_distance_legs_BaseRegion <- modal_split_legs_distance(BaseLegsRegion)
ms_distance_legs_Area90Region <- modal_split_legs_distance(AREA90LegsRegion)
ms_distance_legs_Area95Region <- modal_split_legs_distance(AREA95LegsRegion)
ms_distance_legs_Area99Region <- modal_split_legs_distance(AREA99LegsRegion)

ms_distance_legs_Region <- bind_rows(ms_distance_legs_BaseRegion,
                                      ms_distance_legs_Area90Region,
                                      ms_distance_legs_Area95Region,
                                      ms_distance_legs_Area99Region, .id = "id")

ms_legs_Region_plot <- ms_distance_legs_Region %>%
  group_by(id) %>%
  ggplot(mapping= aes(x = distance, y= n, fill = id))+
  geom_col(position = "dodge")+
  ylab("Distance")+
  labs(fill = "Case")+
  scale_fill_discrete(labels= c("Base Case", "90%","95%", "99%" ))
ms_legs_Region_plot

# b) City

ms_distance_legs_BaseCity <- modal_split_legs_distance(BaseLegsCity)
ms_distance_legs_Area90City <- modal_split_legs_distance(AREA90LegsCity)
ms_distance_legs_Area95City <- modal_split_legs_distance(AREA95LegsCity)
ms_distance_legs_Area99City <- modal_split_legs_distance(AREA99LegsCity)

ms_distance_legs_CITY <- bind_rows(ms_distance_BaseCity,
                                    ms_distance_legs_Area90City,
                                    ms_distance_legs_Area95City,
                                    ms_distance_legs_Area99City, .id = "id")

ms_legs_city_plot <- ms_distance_legs_CITY %>%
  group_by(id) %>%
  ggplot(mapping= aes(x = distance, y= n, fill = id))+
  geom_col(position = "dodge")+
  ylab("Distance")+
  labs(fill = "Case")+
  scale_fill_discrete(labels= c("Base Case", "90%","95%", "99%" ))
ms_legs_city_plot


# c) area90

ms_distance_legs_Base_90 <- modal_split_legs_distance(BaseLegs90)
ms_distance_legs_Area90_90 <- modal_split_legs_distance(AREA90Legs90)
ms_distance_legs_Area95_90 <- modal_split_legs_distance(AREA95Legs90)
ms_distance_legs_Area99_90 <- modal_split_legs_distance(AREA99Legs90)

ms_distance_legs_90 <- bind_rows(ms_distance_legs_Base_90,
                                  ms_distance_legs_Area90_90,
                                  ms_distance_legs_Area95_90,
                                  ms_distance_legs_Area99_90, .id = "id")

ms_legs_90_plot <- ms_distance_legs_90 %>%
  group_by(id) %>%
  ggplot(mapping= aes(x = distance, y= n, fill = id))+
  geom_col(position = "dodge")+
  ylab("Distance")+
  labs(fill = "Case")+
  scale_fill_discrete(labels= c("Base Case", "90%","95%", "99%" ))
ms_legs_90_plot

# d) area95

ms_distance_legs_Base_95 <- modal_split_legs_distance(BaseLegs95)
ms_distance_legs_Area90_95 <- modal_split_legs_distance(AREA90Legs95)
ms_distance_legs_Area95_95 <- modal_split_legs_distance(AREA95Legs95)
ms_distance_legs_Area99_95 <- modal_split_legs_distance(AREA99Legs95)

ms_distance_legs_95 <- bind_rows(ms_distance_legs_Base_95,
                                  ms_distance_legs_Area90_95,
                                  ms_distance_legs_Area95_95,
                                  ms_distance_legs_Area99_95, .id = "id")

ms_legs_95_plot <- ms_distance_legs_95 %>%
  group_by(id) %>%
  ggplot(mapping= aes(x = distance, y= n, fill = id))+
  geom_col(position = "dodge")+
  ylab("Distance")+
  labs(fill = "Case")+
  scale_fill_discrete(labels= c("Base Case", "90%","95%", "99%" ))
ms_legs_90_plot

# e) area99

ms_distance_legs_Base_99 <- modal_split_legs_distance(BaseLegs99)
ms_distance_legs_Area90_99 <- modal_split_legs_distance(AREA90Legs99)
ms_distance_legs_Area95_99 <- modal_split_legs_distance(AREA95Legs99)
ms_distance_legs_Area99_99 <- modal_split_legs_distance(AREA99Legs99)

ms_distance_legs_99 <- bind_rows(ms_distance_legs_Base_99,
                                  ms_distance_legs_Area90_99,
                                  ms_distance_legs_Area95_99,
                                  ms_distance_legs_Area99_99, .id = "id")

ms_legs_99_plot <- ms_distance_legs_99 %>%
  group_by(id) %>%
  ggplot(mapping= aes(x = distance, y= n, fill = id))+
  geom_col(position = "dodge")+
  ylab("Distance")+
  labs(fill = "Case")+
  scale_fill_discrete(labels= c("Base Case", "90%","95%", "99%" ))
ms_trips_99_plot





#### #2 ModalSplit - pkm and ph####



#### #3 ModalShift####

#Function
sankey_dataframe <- function(x, y){
  inner_join(x, y, by = "trip_id") %>%
    select(trip_id, main_mode.x, longest_distance_mode.x, main_mode.y, longest_distance_mode.y) %>%
    group_by(main_mode.x, main_mode.y) %>%
    summarise(Freq = n())
}

#Base Case > 90%
Base_case_city_to_Area90_city <- sankey_dataframe(BaseTripsCity, AREA90TripsCity)

sk_main_mode_base_area90_city <- alluvial(Base_case_city_to_Area90_city[1:2],
                                          freq= Base_case_city_to_Area90_city$Freq,
                                          border = NA,
                                          axis_labels = c("Base Case", "90%"))


#Base Case > 95%
Base_case_city_to_Area95_city <- sankey_dataframe(BaseTripsCity, AREA95TripsCity)

sk_main_mode_base_area95_city <- alluvial(Base_case_city_to_Area95_city[1:2],
                                          freq= Base_case_city_to_Area95_city$Freq,
                                          border = NA,
                                          axis_labels = c("Base Case", "95%"))


#Base Case > 99%

Base_case_city_to_Area99_city <- sankey_dataframe(BaseTripsCity, AREA99TripsCity)

sk_main_mode_base_area90_city <- alluvial(Base_case_city_to_Area99_city[1:2],
                                          freq= Base_case_city_to_Area99_city$Freq,
                                          border = NA,
                                          axis_labels = c("Base Case", "99%"))



#### #4 Traffic volumes####

# contains a set of links Ids which are to be considered.
shape <- st_read("/Users/mkreuschnervsp/Desktop/Projects/03_NaMaV/MATSim/shapefiles/Leipzig_puffer.shp", crs=25832)
#shape <- st_read("/Users/mkreuschnervsp/Desktop/Projects/03_NaMaV/MATSim/shapefiles/Leipzig_stadt.shp", crs=25832)
#shape <- st_read("/Users/mkreuschnervsp/Desktop/Projects/03_NaMaV/MATSim/shapefiles/Zonen90_update.shp", crs=25832)
#shape <- st_read("/Users/mkreuschnervsp/Desktop/Projects/03_NaMaV/MATSim/shapefiles/Zonen95_update.shp", crs=25832)
#shape <- st_read("/Users/mkreuschnervsp/Desktop/Projects/03_NaMaV/MATSim/shapefiles/Zonen99_update.shp", crs=25832)

linkList <- read_delim(list.files("/Users/mkreuschnervsp/Desktop/Projects/03_NaMaV/MATSim/networks/leipzig-flexa-25pct-scaledFleet-baseCase_noDepot.output_network.xml.gz", full.names = T, include.dirs = F) , delim = ";", trim_ws = T,
                       col_types = cols(
                         person = col_character(),
                         good_type = col_integer()
                       )) %>%
  st_as_sf(coords = c("x", "y"), crs = 25832) %>%
  st_filter(shape)




#Leipzig
links_filtered <- network %>%
  filter(links %in% linkList$link)

traffic_base <- read_table("/Users/mkreuschnervsp/Desktop/Projects/03_NaMaV/MATSim/simwrapper/dashboard/simwrapper/05_traffic/base/traffic_output_base.csv")
mean_base_cong = mean(traffic_base$congestion_index)
print(mean_base_cong)
median_base_cong = median(traffic_base$congestion_index)
print(median_base_cong)

mean_base_speed = mean(traffic_base$average_daily_speed)
print(mean_base_speed)
median_base_speed = median(traffic_base$average_daily_speed)
print(median_base_speed)

traffic_99 <- read_table("/Users/mkreuschnervsp/Desktop/Projects/03_NaMaV/MATSim/simwrapper/dashboard/simwrapper/05_traffic/99/traffic_output_99.csv")
mean_99_cong = mean(traffic_99$congestion_index)
print(mean_99_cong)
median_99_cong = median(traffic_99$congestion_index)
print(median_99_cong)

mean_99_speed = mean(traffic_99$average_daily_speed)
print(mean_99_speed)
median_99_speed = median(traffic_99$average_daily_speed)
print(median_99_speed)

traffic_95 <- read_table("/Users/mkreuschnervsp/Desktop/Projects/03_NaMaV/MATSim/simwrapper/dashboard/simwrapper/05_traffic/95/traffic_output_95.csv")
mean_95_cong = mean(traffic_95$congestion_index)
print(mean_95_cong)
median_95_cong = median(traffic_95$congestion_index)
print(median_95_cong)

mean_95_speed = mean(traffic_95$average_daily_speed)
print(mean_95_speed)
median_95_speed = median(traffic_95$average_daily_speed)
print(median_95_speed)

traffic_90 <- read_table("/Users/mkreuschnervsp/Desktop/Projects/03_NaMaV/MATSim/simwrapper/dashboard/simwrapper/05_traffic/90/traffic_output_90.csv")
mean_90_cong = mean(traffic_90$congestion_index)
print(mean_90_cong)
median_90_cong = median(traffic_90$congestion_index)
print(median_90_cong)

mean_90_speed = mean(traffic_90$average_daily_speed)
print(mean_90_speed)
median_90_speed = median(traffic_90$average_daily_speed)
print(median_90_speed)

traffic_base_u <- read_table("/Users/mkreuschnervsp/Desktop/Projects/03_NaMaV/MATSim/carfree_scenario/R_results/traffic/traffic_output_base_umland.csv")
mean_base_cong_u = mean(traffic_base_u$congestion_index)
print(mean_base_cong)
median_base_cong_u = median(traffic_base_u$congestion_index)
print(median_base_cong)

mean_base_speed_u = mean(traffic_base_u$average_daily_speed)
print(mean_base_speed_u)
median_base_speed_u = median(traffic_base_u$average_daily_speed)
print(median_base_speed_u)

#### #5 Emissions####

network <- loadNetwork("/Users/mkreuschnervsp/Desktop/Projects/03_NaMaV/MATSim/networks/leipzig-flexa-25pct-scaledFleet-baseCase_noDepot.output_network.xml.gz")

links_network <- data.frame(links_Leipzig[2])



shape_L <- st_read("/Users/mkreuschnervsp/Desktop/Projects/03_NaMaV/MATSim/shapefiles/Leipzig_stadt.shp", crs=25832)
shape_99 <-  st_read("/Users/mkreuschnervsp/Desktop/Projects/03_NaMaV/MATSim/shapefiles/Zonen99_update.shp", crs=25832)
shape_95 <-  st_read("/Users/mkreuschnervsp/Desktop/Projects/03_NaMaV/MATSim/shapefiles/Zonen95_update.shp", crs=25832)
shape_90 <-  st_read("/Users/mkreuschnervsp/Desktop/Projects/03_NaMaV/MATSim/shapefiles/Zonen90_update.shp", crs=25832)


#links in Leipzig_Stadt
links_Leipzig <- links_network %>% st_as_sf(coords = c("links.x.from", "links.y.from"), crs = 25832) %>% st_filter(shape_L)
#links in Zonen
links_Zonen99 <- links_network %>% st_as_sf(coords = c("links.x.from", "links.y.from"), crs = 25832) %>% st_filter(shape_99)
links_Zonen95 <- links_network %>% st_as_sf(coords = c("links.x.from", "links.y.from"), crs = 25832) %>% st_filter(shape_95)
links_Zonen90 <- links_network %>% st_as_sf(coords = c("links.x.from", "links.y.from"), crs = 25832) %>% st_filter(shape_90)

length(which(links_Leipzig$links.type == "highway.motorway"))
length(which(links_Leipzig$links.type == "highway.motorway_link"))
length(which(links_Leipzig$links.type == "highway.service"))
length(which(links_Leipzig$links.type == "highway.primary"))
length(which(links_Leipzig$links.type == "highway.primary_link"))
length(which(links_Leipzig$links.type == "highway.secondary"))
length(which(links_Leipzig$links.type == "highway.secondary_link"))
length(which(links_Leipzig$links.type == "highway.tertiary"))
length(which(links_Leipzig$links.type == "highway.residential"))
length(which(links_Leipzig$links.type == "highway.living_street"))
length(which(links_Leipzig$links.type == "highway.trunk"))
length(which(links_Leipzig$links.type == "highway.trunk_link"))
length(which(links_Leipzig$links.type == "highway.unclassified"))

Haupt_Leipzig <- filter(links_Leipzig, links.type == "highway.primary" | links.type =="highway.primary_link" | links.type == "highway.secondary" | links.type =="highway.secondary_link" | links.type =="highway.service" | links.type =="highway.motorway")
Neben_Leipzig <- filter(links_Leipzig, links.type == "highway.residential" | links.type == "highway.tertiary" | links.type == "highway.living_street" | links.type == "highway.unclassified")


emissions_file_base <- read_delim("/Users/...")
emissions_file_99 <- read_delim("/Users/...")
emissions_file_95 <- read_delim("/Users/...")
emissions_file_90 <- read_delim("/Users/...")
#Emissionen auf HauptstraÃenlinks in Leipzig
emissions_haupt_leipzig <- filter(emissions_file_base, )
mean()

# Transform our 'x' vector
x <- data.frame(x)

# Boxplot with vector
ggplot(data = x, aes(x = "", y = x)) +
  stat_boxplot(geom = "errorbar",      # Error bars
               width = 0.2) +
  geom_boxplot(fill = "#4271AE",       # Box color
               outlier.colour = "red", # Outliers color
               alpha = 0.9) +          # Box color transparency
  ggtitle("Boxplot with vector") + # Plot title
  xlab("") +   # X-axis label
  coord_flip() # Horizontal boxplot

#Emissionen auf NebenstraÃenlinks in Leipzig
emissions_neben_leipzig <-
  mean()

#Emissionen auf NebenstraÃenlinks innerhalb Zonen
emissions_neben_zonen99 <-
  mean()
emissions_neben_zonen95 <-
  mean()
emissions_neben_zonen90 <-
  mean()

#Verkehr auf HauptstraÃenlinks in Leipzig
traffic_haupt_leipzig <-
  mean()


#Verkehr auf NebenstraÃenlinks in Leipzig
traffic_neben_leipzig <-
  mean()


#Verkehr auf NebenstraÃenlinks innerhalb Zonen
traffic_neben_zonen99 <-
  mean()
traffic_neben_zonen95 <-
  mean()
traffic_neben_zonen90 <-
  mean()


####Sim - Trips####
RegionSim <- RegionTrips %>%
  group_by(dist_group, main_mode) %>%
  summarise(RegionTrips=n()) %>%
  mutate(mode = fct_relevel(main_mode, "walk", "bike", "pt", "ride", "car","freight")) %>%
  mutate(scaled_trips=sim_scale * RegionTrips) %>%
  mutate(source = "sim")

CitySim <- CityTrips %>%
  group_by(dist_group, main_mode) %>%
  summarise(CityTrips=n()) %>%
  mutate(mode = fct_relevel(main_mode, "walk", "bike", "pt", "ride", "car","freight")) %>%
  mutate(scaled_trips=sim_scale * CityTrips) %>%
  mutate(source = "sim")

AreaSim1 <- AreaTrips1 %>%
  group_by(dist_group, main_mode) %>%
  summarise(AreaTrips1=n()) %>%
  mutate(mode = fct_relevel(main_mode, "walk", "bike", "pt", "ride", "car","freight")) %>%
  mutate(scaled_trips=sim_scale * AreaTrips1) %>%
  mutate(source = "sim")

AreaSim2 <- AreaTrips2 %>%
  group_by(dist_group, main_mode) %>%
  summarise(AreaTrips2=n()) %>%
  mutate(mode = fct_relevel(main_mode, "walk", "bike", "pt", "ride", "car","freight")) %>%
  mutate(scaled_trips=sim_scale * AreaTrips2) %>%
  mutate(source = "sim")

AreaSim3 <- AreaTrips3 %>%
  group_by(dist_group, main_mode) %>%
  summarise(AreaTrips3=n()) %>%
  mutate(mode = fct_relevel(main_mode, "walk", "bike", "pt", "ride", "car","freight")) %>%
  mutate(scaled_trips=sim_scale * AreaTrips3) %>%
  mutate(source = "sim")

RegionSim <- RegionSim %>%
  mutate(share=RegionTrips/sum(RegionSim$RegionTrips))
CitySim <- CitySim %>%
  mutate(share=CityTrips/sum(CitySim$CityTrips))
AreaSim1 <- AreaSim1 %>%
  mutate(share=AreaTrips1/sum(AreaSim1$AreaTrips1))
AreaSim2 <- AreaSim2 %>%
  mutate(share=AreaTrips2/sum(AreaSim2$AreaTrips2))
AreaSim3 <- AreaSim3 %>%
  mutate(share=AreaTrips3/sum(AreaSim3$AreaTrips3))

#PATH where to write output
write_csv(RegionSim, "")
write_csv(CitySim, "")
write_csv(AreaSim1, "")
write_csv(AreaSim2, "")
write_csv(AreaSim3, "")



####OUTPUT path where to put the R ouput e.g. for simwrapper####
