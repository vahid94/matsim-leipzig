##### Same procedure as in counts-compare.R but with the functions from matsim-r
devtools::install_github("matsim-vsp/matsim-r",ref="counts")

library(matsim)
library(tidyverse)
library(scales)
library(geomtextpath)

COUNTS <- "output/leipzig-v1.1-counts_Pkw.xml"
NETWORK <- "C:/Users/ACER/Desktop/Uni/VSP/matsim-leipzig/output/leipzig-v1.1-network-with-pt-drt.xml"

linkStats = list(readLinkStats(runId = "v1.0-run039", file = "C:/Users/ACER/Desktop/Uni/VSP/matsim-leipzig/output/039/v1.0_run039.linkstats.tsv"),
                 readLinkStats(runId = "v1.2-run007", file = "C:/Users/ACER/Desktop/Uni/VSP/matsim-leipzig/output/007/v1.2_run007.linkstats.tsv"))

counts <- readCounts(COUNTS)
network <- loadNetwork(NETWORK)

join <- mergeCountsAndLinks(counts = counts, linkStats  = linkStats, network = network,
                            networkModes = c("car"))

#### VIA-styled scatterplot ####

createCountScatterPlot(joinedFrame = join)

#### Analysis of DTV distribution ####
join.dtv.distribution <- processLinkStatsDtvDistribution(joinedFrame = join)

ggplot(join.dtv.distribution, aes(x = traffic_bin, y = share, fill = type)) +
  
  geom_col() +
  
  facet_grid(src ~ type) +
  
  labs(x = "Daily traffic volume", y = "Share") +
  
  theme_bw() +
  
  theme(legend.position = "none", axis.text.x = element_text(angle = 90))

rm(join.dtv.distribution)


#### Analysis of Estimation Quality ####

join.est.quality <- processDtvEstimationQuality(joinedFrame = join, aggr = T) %>%
  filter(!type %in% c("residential", "unclassified", NA))

ggplot(join.est.quality, aes(estimation, share, fill = type)) +
  
  geom_col() +
  
  labs(y = "Share", x = "Quality category") +
  
  facet_grid(src ~ type) +
  
  theme_bw() +
  
  theme(legend.position = "none", axis.text.x = element_text(angle = 90))