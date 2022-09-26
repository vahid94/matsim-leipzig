library(xml2)
library(tidyverse)
library(matsim)

readCounts <- function(file){
  
  message = paste("Read counts file from", file)
  print(message)
  
  counts.xml <- read_xml(file) 
  
  station = xml_find_all(counts.xml, "//count") %>%
    xml2::xml_attrs() %>%
    purrr::map_df(~as.list(.)) %>%
    readr::type_convert()
  
  volume = xml_find_all(counts.xml, "//volume") %>%
    xml2::xml_attrs() %>%
    purrr::map_df(~as.list(.)) %>%
    readr::type_convert()
  
  bind_cols(station, volume)
}

readLinkStats <- function(runId, filepath, sampleSize = NA){
  
  message <- paste("Read in link stats from run", runId, ". Loading data from", filepath )
  print(message)
  
  linkstats <- readr::read_csv(file = filepath)
  
  linkstats.1 <- linkstats %>%
    group_by(linkId) %>%
    summarise_at(c("vol_car", "vol_bike"), sum)
  
  if(!is.na(sampleSize)){
    linkstats.1 = linkstats.1 %>%
      mutate_at(c("vol_car", "vol_bike"), function(x){ x * (1 / sampleSize)})
  }
  
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

mergeCountsAndLinks <- function(countsFilePath, networkFilePath, linkStatsList, sampleSize = NA, outputFilePath = NA){
  
  if(!is.list(linkStatsList)){
    message <- "linkStatsList needs to be a list, like list(runId = filepath)"
    warning(message)
    
    return(NA)
  }
  
  counts <- readCounts(file = countsFilePath)
  
  network <- loadNetwork(filename = networkFilePath)
  links <- network$links %>%
    select(id, type)
  
  rm(network)
  
  join <- left_join(x = counts, y = links, by = c("loc_id" = "id"))
  rm(counts, links)
  
  #Load linkstats
  names <- names(linkStatsList)
  
  for(n in names){
    runId <- n
    filepath <- linkStatsList[[n]]
    
    linkStats <- readLinkStats(runId = runId, filepath = filepath, sampleSize = sampleSize)
    join <- left_join(x = join, y = linkStats, by = c("loc_id" = "linkId"))
  }
  
  if(!is.na(outputFilePath)) readr::write_csv(join, file = outputFilePath)
  
  join %>%
    rename("vol_car_count_station" = "val")
}

plotCountsAgainstLinkStats <- function(joinedFrame, intervall = c(0.8, 1.2)){
  
  if(!is.data.frame(joinedFrame)){
    
    message <- "joinedFrame needs to be a data frame, created from method mergeCountsAndLinks!"
    warning(message)
    return(NA)
  }
  
  join.1 = joinedFrame %>%
    filter(!is.na(type)) %>%
    filter(vol_car_count_station > 0) %>%
    select(loc_id, vol_car_count_station, starts_with("vol_car_"),type) %>%
    mutate(type = str_remove(type, pattern = "highway."),
           type = factor(type, levels = c("motorway", "primary", "secondary", "tertiary", "residential", "unclassified", "motorway_link", "primary_link", "trunk_link"))) %>%
    select(-starts_with("vol_bike")) %>%
    pivot_longer(cols = c(vol_car_v1.0_run039, vol_car_v1.2_run007), names_to = "runId", values_to = "vol_sim", names_prefix = "vol_car_")
  
  line.size = 1.0
  
  ggplot(join.1, aes(x = vol_car_count_station, y = vol_sim, color = type)) +
    
    geom_point() +
    
    geom_abline(size = line.size) +
    
    geom_abline(slope = intervall[1], intercept = 0) +
    
    geom_abline(slope = intervall[2], intercept = 0) +
    
    scale_x_log10() +
    
    scale_y_log10() +
    
    facet_wrap(.~ runId) +
    
    labs(x = "Daily traffic volume from Count Stations", y = "Daily traffic volume from MATSim Data", color = "Road type:") +
    
    theme_bw() +
    
    theme(legend.position = "bottom", panel.background = element_rect(fill = "grey90"),
          panel.grid = element_line(colour = "white"))
}

plotDTVDistribution <- function(joinedFrame){
  
  if(!is.data.frame(joinedFrame)){
    
    message <- "joinedFrame needs to be a data frame, created from method mergeCountsAndLinks!"
    warning(message)
    return(NA)
  }
  
  #### Counts in each bin
  options(scipen=999)
  breaks = seq(0, 40000, 5000)
  labels = character()
  
  for(i in 1:length(breaks) - 1){
    
    label = paste0(breaks[i] / 1000, "k < ", breaks[i + 1] / 1000, "k")
    labels[i] = label
    rm(label)
  }
  
  join.1 = joinedFrame %>%
    filter(!is.na(type)) %>%
    filter(vol_car_count_station > 0) %>%
    select(loc_id, vol_car_count_station, starts_with("vol_car_"),type) %>%
    mutate(type = str_remove(type, pattern = "highway."),
           type = factor(type, levels = c("motorway", "primary", "secondary", "tertiary", "residential", "unclassified", "motorway_link", "primary_link", "trunk_link"))) %>%
    select(-starts_with("vol_bike")) %>%
    pivot_longer(cols = starts_with("vol_car"), names_to = "src", names_prefix = "vol_car_", values_to = "traffic_vol") %>%
    mutate(src = str_remove(src, pattern = "_vol"),
           traffic_bin = cut(traffic_vol, labels = labels, breaks = breaks, right = T)) %>%
    group_by(type, src, traffic_bin) %>%
    summarise(n = n()) %>%
    group_by(type, src) %>%
    mutate(share = n / sum(n)) %>%
    filter(!str_detect(type, pattern = "_link")) %>%
    filter(!type %in% c("residential", "unclassified"))
  
  ggplot(join.1, aes(x = traffic_bin, y = share, fill = type)) +
    
    geom_col() +
    
    facet_grid(src ~ type) +
    
    labs(x = "Daily traffic volume", y = "Share") +
    
    theme_bw() +
    
    theme(legend.position = "none", axis.text.x = element_text(angle = 90))
}

plotDTVQuality <- function(joinedFrame){
  
  join.1 = joinedFrame %>%
    filter(!is.na(type)) %>%
    filter(vol_car_count_station > 0) %>%
    select(loc_id, vol_car_count_station, starts_with("vol_car_"),type) %>%
    mutate(type = str_remove(type, pattern = "highway."),
           type = factor(type, levels = c("motorway", "primary", "secondary", "tertiary", "residential", "unclassified", "motorway_link", "primary_link", "trunk_link"))) %>%
    select(-starts_with("vol_bike"))
  
  names <- colnames(join.1)
  cs_col <- names[str_detect(names, "count_station")][1]
  names = names[str_detect(names, pattern = "vol_car")]
  names = names[!str_detect(names, pattern = "count_station")]
  
  for(n in names){
    
    n_fixed = str_remove(n, pattern = "vol_car_")
    join.1[paste0("rel_vol_", n_fixed)] = join.1[n] / join.1[cs_col]
  }
  
  pv_longer_cols <- colnames(join.1)
  pv_longer_cols = pv_longer_cols[str_detect(pv_longer_cols, pattern = "rel_vol")]
  
  est.breaks = c(-Inf, 0.8, 1.2, Inf)
  est.labels = c("less", "exact", "more")
    
  join.2 <- join.1 %>%
    select(- starts_with("vol_car_")) %>%
    pivot_longer(cols = pv_longer_cols, names_prefix = "rel_vol_", names_to = "src", values_to = "rel_vol") %>%
    mutate(quality = cut(rel_vol, breaks = est.breaks, labels = est.labels)) %>%
    filter(! type %in% c("residential", "unclassified")) %>%
    mutate(rel_vol_round = round(rel_vol, 2),
           estimation = cut(rel_vol_round, breaks = est.breaks, labels = est.labels)) %>%
    group_by(src, type, estimation) %>%
    summarise(n = n()) %>%
    mutate(share = n / sum(n)) %>%
    ungroup()
  
  plt <- ggplot(join.2, aes(estimation, share, fill = type)) +
    
    geom_col() +
    
    labs(y = "Share", x = "Quality category") +
    
    facet_grid(src ~ type) +
    
    theme_bw() +
    
    theme(legend.position = "none", axis.text.x = element_text(angle = 90))
  
  list("plot" = plt, "df" = join.2)
}
