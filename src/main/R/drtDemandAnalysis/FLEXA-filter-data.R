library(lubridate)
library(tidyverse)
library(dplyr)

# set working directory
setwd("C:/Users/Simon/Documents/shared-svn/projects/NaMAV/data/Flexa/")

# read data
FlexaData2021 <- read.csv2("Nachfragedaten_LVB_2021/Nachfragedaten_Flexa2021_anonymisiert/anonymisierteDatenNeu.csv", stringsAsFactors = FALSE, header = TRUE, sep=",",encoding = "UTF-8")

#filter
FlexaData2021 <- FlexaData2021 %>%
  select(request_time, actual_departure_time, actual_arrival_time, requested_origin_lat, requested_origin_lon, requested_destination_lat,
         requested_destination_lon, requested_origin_assigned_flexa_stop, requested_destination_assigned_flexa_stop, requested_origin_flexa_area,
         requested_destination_flexa_area, number_of_passengers)

write.csv2(FlexaData2021, "Nachfragedaten_LVB_2021/Nachfragedaten_Flexa_2021_anonymisiert_filtered.csv", quote = FALSE)

#filter only realized rides
#there are some 43 rides, which have an arrival time but do not have a departure time
#better filter them out
completedRides <- FlexaData2021 %>%
  filter(actual_arrival_time > 0) %>%
  filter(actual_departure_time > 0)

#watch out for the right time zone handling here!
saturday_rides <- completedRides %>%
  mutate(actual_departure_time = ymd_hms(actual_departure_time, tz="Europe/Berlin")) %>%
  mutate(weekday_ride = wday(actual_departure_time, label = TRUE)) %>%
  filter(weekday_ride == "Sa")

sunday_rides <- completedRides %>%
  mutate(actual_departure_time = ymd_hms(actual_departure_time, tz="Europe/Berlin")) %>%
  mutate(weekday_ride = wday(actual_departure_time, label = TRUE)) %>%
  filter(weekday_ride == "So")

#dump output
write.csv2(completedRides, "Flexa_Rides_2021.csv", quote = FALSE)
write.csv2(saturday_rides, "Flexa_Rides_Saturdays_2021.csv", quote = FALSE)
write.csv2(sunday_rides, "Flexa_Rides_Sundays_2021.csv", quote = FALSE)