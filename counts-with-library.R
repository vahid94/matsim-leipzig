##### Same procedure as in counts-compare.R but with the functions from matsim-r
devtools::install_github("matsim-vsp/matsim-r",ref="counts")

library(matsim)
library(tidyverse)
library(scales)
library(geomtextpath)

symlog_trans <- function(base = 10, thr = 1, scale = 1){
  trans <- function(x)
    ifelse(abs(x) < thr, x, sign(x) * 
             (thr + scale * suppressWarnings(log(sign(x) * x / thr, base))))
  
  inv <- function(x)
    ifelse(abs(x) < thr, x, sign(x) * 
             base^((sign(x) * x - thr) / scale) * thr)
  
  breaks <- function(x){
    sgn <- sign(x[which.max(abs(x))])
    if(all(abs(x) < thr))
      pretty_breaks()(x)
    else if(prod(x) >= 0){
      if(min(abs(x)) < thr)
        sgn * unique(c(pretty_breaks()(c(min(abs(x)), thr)),
                       log_breaks(base)(c(max(abs(x)), thr))))
      else
        sgn * log_breaks(base)(sgn * x)
    } else {
      if(min(abs(x)) < thr)
        unique(c(sgn * log_breaks()(c(max(abs(x)), thr)),
                 pretty_breaks()(c(sgn * thr, x[which.min(abs(x))]))))
      else
        unique(c(-log_breaks(base)(c(thr, -x[1])),
                 pretty_breaks()(c(-thr, thr)),
                 log_breaks(base)(c(thr, x[2]))))
    }
  }
  trans_new(paste("symlog", thr, base, scale, sep = "-"), trans, inv, breaks)
}

COUNTS <- "output/leipzig-v1.1-counts_Pkw.xml"
NETWORK <- "C:/Users/ACER/Desktop/Uni/VSP/matsim-leipzig/output/leipzig-v1.1-network-with-pt-drt.xml"

linkStats = list(readLinkStats(runId = "v1.0-run039", file = "C:/Users/ACER/Desktop/Uni/VSP/matsim-leipzig/output/039/v1.0_run039.linkstats.tsv"),
                 readLinkStats(runId = "v1.2-run007", file = "C:/Users/ACER/Desktop/Uni/VSP/matsim-leipzig/output/007/v1.2_run007.linkstats.tsv"))

counts <- readCounts(COUNTS)
network <- loadNetwork(NETWORK)

join <- mergeCountsAndLinks(counts = counts, linkStats = linkStats, network = network,
                            networkModes = c("car"))

#### VIA-styled scatterplot ####
line.size <- 0.7

threshold <- 100

x <- seq(threshold, round(max(join$count), -2), 10)

upper.limit <- data.frame(x = x,
                          y = 1.2 * x)

lower.limit <- data.frame(x = x,
                          y = 0.8 * x)

middle.line <- data.frame(x = x,
                          y = x)

ggplot(join, aes(x = count, y = volume, color = type)) +
  
  geom_point() +
  
  geom_line(data = middle.line, mapping = aes(x, y), size = line.size, linetype = "dashed", color = "grey60") +
  
  geom_line(data = upper.limit, mapping = aes(x, y), color = "black", size = line.size + 0.1) +
  
  geom_line(data = lower.limit, mapping = aes(x, y), color = "black", size = line.size + 0.1) +
  
  geom_vline(xintercept = threshold, linetype = "dashed") +
  
  geom_textvline(xintercept = threshold, label = "x = 100", linetype = "dashed", vjust = -0.3) +
  
  scale_x_continuous(trans = symlog_trans(thr = 100, scale = 1000), breaks = c(0, 300, 1000, 3000, 10000, 30000, 100000)) + 
  
  scale_y_continuous(trans = "log10", breaks = c(3, 10, 30, 100, 300, 1000, 3000, 10000, 30000)) + 
  
  facet_wrap(.~ src) +
  
  labs(x = "Daily traffic volume from Count Stations", y = "Daily traffic volume from MATSim Data", color = "Road type:") +
  
  theme_bw() +
  
  theme(legend.position = "bottom", panel.background = element_rect(fill = "grey90"),
        panel.grid = element_line(colour = "white"))

rm(join.scatterplot, lower.limit, upper.limit, middle.line)


#### Analysis of DTV distribution ####
join.dtv.distribution <- processLinkStatsDtvDistribution(joinedFrame = join)

ggplot(join.dtv.distribution, aes(x = traffic_bin, y = share, fill = type)) +
  
  geom_col() +
  
  facet_grid(src ~ type) +
  
  labs(x = "Daily traffic volume", y = "Share") +
  
  theme_bw() +
  
  theme(legend.position = "none", axis.text.x = element_text(angle = 90))


#### Analysis of Estimation Quality ####
