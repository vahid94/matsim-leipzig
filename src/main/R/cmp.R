
library(tidyverse)
library(lubridate)

# setwd("C:/Users/chris/Development/matsim-scenarios/matsim-leipzig/src/main/R")

f <- "C:/Users/chris/Development/matsim-scenarios/matsim-leipzig/output/output-leipzig-25pct/analysis-v3.2/mode-statistics/a.csv"

calib <- read_delim(f, delim = ";", trim_ws = T) %>%
  pivot_longer(cols=c("pt", "walk", "car", "bike", "ride"),
               names_to="mode",
               values_to="trips") %>%
  mutate(mode = fct_relevel(mode, "walk", "bike", "pt", "ride", "car")) %>%
  mutate(dist_group=sprintf("%g - %g", `distance - from [m]`, `distance to [m]`))

calib <- calib %>%
      mutate(dist_group=case_when(
        `distance to [m]`== max(calib$`distance to [m]`) ~ sprintf("%g+", `distance - from [m]`),
        TRUE ~ `dist_group`
    ))


dist_order <- factor(calib$dist_group, level = c("0 - 1000", "1000 - 3000", "3000 - 5000", "5000 - 10000", 
                                                 "10000 - 20000"))
# Maps left overgroups
dist_order <- fct_explicit_na(dist_order, "20000+")

calib_sum <- sum(calib$trips)
calib_aggr <- calib %>%
  group_by(dist_group) %>%
  summarise(share=sum(trips) / calib_sum)

# Needed share of trips
tripShare <- 0.2418
shortDistance <- sum(filter(calib, dist_group=="0 - 1000")$trips)
numTrips = (shortDistance - calib_sum * tripShare) / (tripShare - 1)

ggplot(calib, aes(fill=mode, y=trips, x=dist_order)) +
  labs(subtitle = "Calibrated scenario", x="distance [m]") +
  geom_bar(position="stack", stat="identity")
