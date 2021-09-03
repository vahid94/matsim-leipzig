
library(tidyverse)
library(lubridate)
library(sf)

# setwd("C:/Users/chris/Development/matsim-scenarios/matsim-leipzig/src/main/R")

f <- "\\\\sshfs.kr\\rakow@cluster.math.tu-berlin.de\\net\\ils4\\matsim-leipzig\\calibration\\runs\\002"
sim_scale <- 10

# breaks in meter
breaks = c(0, 1000, 2000, 5000, 10000, 20000, Inf)

shape <- st_read("../../../../../shared-svn/NaMAV/data/leipzig-utm32n/leipzig-utm32n.shp", crs=25832)

persons <- read_delim(list.files(f, pattern = "*.output_persons.csv.gz", full.names = T, include.dirs = F) , delim = ";", trim_ws = T, 
                      col_types = cols(
                        person = col_character(),
                        good_type = col_integer()
                      )) %>%
  st_as_sf(coords = c("first_act_x", "first_act_y"), crs = 25832) %>%
  st_filter(shape)


trips <- read_delim(list.files(f, "*.output_trips.csv.gz", full.names = T, include.dirs = F) , delim = ";", trim_ws = T, 
                    col_types = cols(
                      person = col_character()
                    )) %>%
  mutate(main_mode=longest_distance_mode) %>%  # TODO: can be removed later
  filter(main_mode!="freight") %>%
  semi_join(persons) %>%
  mutate(dist_group = cut(traveled_distance, breaks=breaks, labels=levels)) %>%
  filter(!is.na(dist_group))


#########

b <- seq(0, 86400, by = 15*60)

labels <- seconds_to_period(b[0:96]) 
labels <- sprintf('%02d:%02d', labels@hour, minute(labels))

df <- trips %>%
        mutate(time = cut(as.numeric(dep_time), breaks = b, labels = labels)) %>%
        filter(time!="23:45") %>%
        mutate(time = as_datetime(hm(time), tz="UTC")) %>%
        mutate(activity=substr(end_activity_type, 0, stringi::stri_locate_first_fixed(end_activity_type, "_")[,1] - 1)) %>%
        group_by(activity, time) %>%
        summarise(n=n())


ggplot(df, aes(x=time, y=`n`, group=activity, fill=activity, color=activity)) +
  labs(title="Activities over time of day") +
  ylim(0, 1500) +
  geom_line(size=1) +
  theme_minimal() +
  scale_x_datetime(date_labels = "%H:%M")
  
