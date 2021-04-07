
library(tidyverse)
library(lubridate)

# setwd("C:/Users/chris/Development/matsim-scenarios/matsim-leipzig/src/main/R")

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
  summarise(trips=sum(GEWICHT_W))

# Trips in survey
sum(srv$trips)

ggplot(srv, aes(fill=mode, y=trips, x=dist_group)) +
  labs(subtitle = "Survey data", x="distance [m]") +
  geom_bar(position="stack", stat="identity")


