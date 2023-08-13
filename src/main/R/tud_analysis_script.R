##### TUD_analysis_script ####
print("TUD file is read")
## TUD Analysis list

x_population_seg_filter= 1
x_emissions_barchart = 1
x_average_and_total_travel_distance_by_mode_barchart = 1
x_average_walking_distance_by_mode_barchart = 1 
x_walking_distance_distribution_binchart = 1
x_walking_distance_distribution_linechart = 1
x_trips_number_barchart = 1
x_average_distance_by_mode_just_main_leg_barchart = 1
x_shifted_trips_average_distance_bar_chart = 1
## will be integrated with the next commit
X_winner_loser_analysis = 0 # Note: A more extensive analysis is performed by TUB.

## base data reading and filtering

# trips reading and filtering
base.trips.table <- readTripsTable(pathToMATSimOutputDirectory = base.run.path)

base.trips.region <- filterByRegion(base.trips.table,region.shape,crs=CRS,start.inshape = TRUE,end.inshape = TRUE)
base.trips.city <- filterByRegion(base.trips.table,city.shape,crs=CRS,start.inshape = TRUE,end.inshape = TRUE)
base.trips.carfree.area <- filterByRegion(base.trips.table, carfree.area.shape, crs=CRS, start.inshape = TRUE, end.inshape = TRUE)

# legs reading and filtering
base.legs.table <- read_delim(paste0(base.run.path,"/",list.files(path = base.run.path, pattern = "output_legs")), delim= ";")#, n_max = 3000)

base.legs.region <- filterByRegion(base.legs.table,region.shape,crs=CRS,start.inshape = TRUE,end.inshape = TRUE)
base.legs.city <- filterByRegion(base.legs.table,city.shape,crs=CRS,start.inshape = TRUE,end.inshape = TRUE)
base.legs.carfree.area <- filterByRegion(base.legs.table, carfree.area.shape, crs=CRS, start.inshape = TRUE, end.inshape = TRUE)

# emission reading
emission_base  <- read_delim(paste0(base.run.path,"/",list.files(path = base.run.path, pattern = "emission")), delim= ";")
emission_scenario <- read_delim(paste0(scenario.run.path,"/",list.files(path = scenario.run.path, pattern = "emission")), delim= ";")

## List of scenarios: Define the scenarios to be included in the same plot.
trips.list.region <- list(base = base.trips.region, policy = scenario.trips.region)
legs.list.region <- list(base = base.legs.region, policy = scenario.legs.region)

trips.list.city <- list(base = base.trips.city, policy = scenario.trips.city)
legs.list.city <- list(base = base.legs.region, policy = scenario.legs.region)

trips.list.carfree.area <- list(base = base.trips.carfree.area, policy = scenario.trips.carfree.area)
legs.list.carfree.area <- list(base = base.legs.region, policy = scenario.legs.region) # legs belong to large car free area might have some legs out of the area

print(" TUD data is read and filtered")
############### Analysis ###################

## Population segment filter  
if(x_population_seg_filter == 1){
  ## Concept for filtering residents: considering agents have home activity at the start or end of trip. Same approach for workers 
  population_filtering_function <- function(trips_table, population_type) {
    if (population_type == "resident") {
      filtered_trips <- filter(
        trips_table,
        grepl('home', start_activity_type) | grepl('home', end_activity_type)
      )
    } else if (population_type == "worker") {
      filtered_trips <- filter(
        trips_table,
        grepl('work', start_activity_type) | grepl('work', end_activity_type)
      )
    } else {
      stop("Invalid trip type. Please enter either 'resident' or 'worker'.")
    }
    relevant_trips <- trips_table %>% 
      filter(person %in% filtered_trips$person)
    return(relevant_trips)
  }
}

## emission bar chart
if (x_emissions_barchart == 1){
  # Load network 
  network_for_emission <- loadNetwork(network)
  links_network <- data.frame(network_for_emission[2])
  links_leipzig <- links_network %>% st_as_sf(coords = c("links.x.from", "links.y.from"), crs = CRS) %>% st_filter(region.shape)
  links_scenario <- links_network %>% st_as_sf(coords = c("links.x.from", "links.y.from"), crs = CRS) %>% st_filter(carfree.area.shape)
  
  # Renaming the column to match the 'Links Id' column in the other data frame
  colnames(links_leipzig)[1] <- "linkId"
  colnames(links_scenario)[1] <- "linkId"
  
  # Finding the corresponding emission information for the links
  links_emission_base <- merge(emission_base, links_scenario, by = 'linkId', all.x = FALSE)
  links_emission_scenario <- merge(emission_scenario, links_scenario, by = 'linkId', all.x = FALSE)
  
  # emission calculation
  emission_calc <- function(emission_type) {
    base_emission <- sum(links_emission_base[[paste0(emission_type, " [g/m]")]]*links_emission_base$links.length)
    scenario_emission <- sum(links_emission_scenario[[paste0(emission_type, " [g/m]")]]*links_emission_scenario$links.length)
    emission_df <- data.frame(emission_type = emission_type, base = base_emission, policy_90 = scenario_emission)
    
    write.csv(emission_df, file = paste0(outputDirectoryScenario, "/", "df." ,emission_type, "_emission_TUD.csv"), row.names = FALSE, quote = FALSE)
  }
  
  emission_calc("CO")
  emission_calc("CO2_TOTAL")
}

##average and total distance bar chart
if (x_average_and_total_travel_distance_by_mode_barchart == 1){
  
  total_and_average_distance_by_mode <- function(trips_list, output_filename_total, output_filename_average){
    
    calculation <- function(trips){
      trips %>% 
        group_by(main_mode) %>%
        summarize(total_distance = sum(traveled_distance / 1000), 
                  average_distance = mean(traveled_distance / 1000)) %>%
        filter(!is.na(main_mode) & main_mode != "drtNorth" & main_mode != "drtSoutheast")
    }
    
    combined_data_total <- tibble()
    combined_data_average <- tibble()
    
    for (i in seq_along(trips_list)){
      scenario_name <- names(trips_list)[i]
      distance_by_mode <- calculation(trips_list[[i]])
      
      combined_data_total <- if (i == 1) {
        distance_by_mode %>% select(main_mode, total_distance) %>%
          rename(!!scenario_name := total_distance)
      } else {
        left_join(combined_data_total, 
                  distance_by_mode %>% select(main_mode, total_distance) %>%
                    rename(!!scenario_name := total_distance), 
                  by = "main_mode")
      }
      
      combined_data_average <- if (i == 1) {
        distance_by_mode %>% select(main_mode, average_distance) %>%
          rename(!!scenario_name := average_distance)
      } else {
        left_join(combined_data_average, 
                  distance_by_mode %>% select(main_mode, average_distance) %>%
                    rename(!!scenario_name := average_distance), 
                  by = "main_mode")
      }
    }
    
    write.csv(combined_data_total, file = paste0(outputDirectoryScenario, "/", "df.", output_filename_total, ".TUD.csv"), row.names = FALSE, quote = FALSE)
    write.csv(combined_data_average, file = paste0(outputDirectoryScenario, "/", "df.", output_filename_average, ".TUD.csv"), row.names = FALSE, quote = FALSE)
  }
  
  total_and_average_distance_by_mode(trips.list.region, "total.distance.by.mode.region.csv", "average.distance.by.mode.region.csv")
  total_and_average_distance_by_mode(trips.list.city, "total.distance.by.mode.city.csv", "average.distance.by.mode.city.csv")
  total_and_average_distance_by_mode(trips.list.carfree.area, "total.distance.by.mode.carfree.area.csv", "average.distance.by.mode.carfree.area.csv")
}

if(x_average_distance_by_mode_just_main_leg_barchart == 1){
total_and_average_distance_by_mode_just_main_leg <- function(trips_list, legs_list, output_filename_total, output_filename_average){
  
  calculation <- function(trips, legs){
    legs_with_main_leg <- legs %>%
      left_join(select(trips, trip_id, main_mode), by = "trip_id") %>%
      filter(mode == main_mode) %>%  # Exclude the leg which has the same mode as the main_mode of the trip
      group_by(main_mode) %>%
      summarize(total_distance = sum(distance / 1000),
                average_distance = mean(distance / 1000)) %>%
      filter(main_mode != "drtNorth" & main_mode != "drtSoutheast")
    
    return(legs_with_main_leg) # Return the final result
  }
  
    combined_data_total <- tibble()
    combined_data_average <- tibble()
  
   for (i in seq_along(trips_list)){
     scenario_name <- names(trips_list)[i]
     distance_by_mode <- calculation(trips_list[[i]], legs_list[[i]])
    
       combined_data_total <- if (i == 1) {
        distance_by_mode %>% select(main_mode, total_distance) %>%
          rename(!!scenario_name := total_distance)
      } else {
       left_join(combined_data_total,
                 distance_by_mode %>% select(main_mode, total_distance) %>%
                   rename(!!scenario_name := total_distance),
                  by = "main_mode")
     }
    
     combined_data_average <- if (i == 1) {
        distance_by_mode %>% select(main_mode, average_distance) %>%
         rename(!!scenario_name := average_distance)
     } else {
       left_join(combined_data_average,
                 distance_by_mode %>% select(main_mode, average_distance) %>%
                 rename(!!scenario_name := average_distance),
                 by = "main_mode")
     }
    }
  
    write.csv(combined_data_total, file = paste0(outputDirectoryScenario, "/", "df.", output_filename_total, ".TUD.csv"), row.names = FALSE, quote = FALSE)
    write.csv(combined_data_average, file = paste0(outputDirectoryScenario, "/", "df.", output_filename_average, ".TUD.csv"), row.names = FALSE, quote = FALSE)
  }

  total_and_average_distance_by_mode_just_main_leg(trips.list.region, legs.list.region, "total.distance.by.mode.main.leg.region.csv", "average.distance.by.mode.main.leg.region.csv")
  total_and_average_distance_by_mode_just_main_leg(trips.list.city, legs.list.region, "total.distance.by.mode.main.leg.city.csv", "average.distance.by.mode.main.leg.city.csv")
  total_and_average_distance_by_mode_just_main_leg(trips.list.carfree.area, legs.list.region, "total.distance.by.mode.main.leg.carfree.area.csv", "average.distance.by.mode.main.leg.carfree.area.csv")
  
}

# average walking distance by mode bar chart
# Scenarios are passed to the function as lists of 'trips' and 'legs' data
if(x_average_walking_distance_by_mode_barchart == 1 ){
  average_walking_distance_by_mode <- function(trips_list, legs_list, output_filename) {
    
    add_main_mode <- function(legs, trips) {
      legs %>%
        left_join(select(trips, trip_id, main_mode), by = "trip_id") %>%
        filter(!is.na(main_mode) & main_mode != "drtNorth" & main_mode != "drtSoutheast")
    }
    
    calculation <- function(legs) {
      walk_legs <- legs %>% filter(mode == "walk")
      
      each_mode <- walk_legs %>%
        group_by(main_mode) %>%
        summarise(
          total_walk_distance = sum(distance),
          n_trip = n_distinct(trip_id),
          average_walk_distance = total_walk_distance / n_trip,
          .groups = "drop"
        )
      
      all_modes <- walk_legs %>%
        summarise(
          main_mode = "All modes",
          total_walk_distance = sum(distance),
          n_trip = n_distinct(trip_id),
          average_walk_distance = total_walk_distance / n_trip
        )
      rbind(each_mode, all_modes)
    }
    
    for (i in seq_along(trips_list)) {
      scenario_name <- names(trips_list)[i]
      legs.modified <- add_main_mode(legs_list[[scenario_name]], trips_list[[scenario_name]])
      average_walking_distance_each_scenario <- calculation(legs.modified)%>%
        select(main_mode, average_walk_distance) %>%
        rename(!!scenario_name := average_walk_distance)

      if (i == 1){
        average_walking_distance_csv_data <- average_walking_distance_each_scenario
      } else {
        average_walking_distance_csv_data <- left_join(average_walking_distance_csv_data, average_walking_distance_each_scenario, by = "main_mode")
      }
    }
    write.csv(average_walking_distance_csv_data, file = paste0(outputDirectoryScenario, "/", "df." ,output_filename, ".TUD.csv"), row.names = FALSE, quote = FALSE)
  }
  
  average_walking_distance_by_mode(trips.list.region, legs.list.region, "average.walking.distance.by.mode.region")
  average_walking_distance_by_mode(trips.list.city, legs.list.city, "average.walking.distance.by.mode.city")
  average_walking_distance_by_mode(trips.list.carfree.area, legs.list.carfree.area, " average.walking.distance.by.mode.carfree.area")
}

if (x_walking_distance_distribution_binchart == 1 | x_walking_distance_distribution_linechart == 1) {
  walking_distance_distribution_by_mode <- function(trips_list, legs_list, output_filename_prefix) {
    
    add_main_mode <- function(legs, trips) {
      legs %>%
        left_join(select(trips, trip_id, main_mode), by = "trip_id") %>%
        filter(!is.na(main_mode) & main_mode != "drtNorth" & main_mode != "drtSoutheast")
    }
    
    distribution_calculation <- function(legs, scenario_type) {
      
      walk_legs_summarised <- legs %>%
        filter(mode == "walk") %>%
        group_by(trip_id) %>%
        summarise(total_distance = sum(distance), main_mode = first(main_mode), .groups = "drop")

      #The default breaks_seq is suitable for 'car' mode (big change for this type of chart happen in car mode).
      #Adjustments can be made for other modes if needed.
      breaks_seq <- seq(0, 1400, by = 50)
      labels_seq <- paste(head(breaks_seq, -1), tail(breaks_seq, -1), sep = "-")
      
      walk_legs_summarised %>%
        mutate(interval = cut(total_distance, breaks = breaks_seq, include.lowest = TRUE, labels = labels_seq,  right = FALSE)) %>%
        group_by(main_mode, interval) %>%
        summarise(count = n(), .groups = "drop") %>%
        arrange(main_mode, interval) %>%
        mutate(scenario_type = scenario_type) # add a scenario_type column
    }
    
    combined_data_list <- list()
    for (i in seq_along(trips_list)) {
      scenario_name <- names(trips_list)[i]
      legs.modified <- add_main_mode(legs_list[[scenario_name]], trips_list[[scenario_name]])
      combined_data_list[[scenario_name]] <- distribution_calculation(legs.modified, scenario_name)
    }
    
    combined_data <- do.call(rbind, combined_data_list)
    unique_modes <- unique(combined_data$main_mode)
    for (mode in unique_modes) {
      mode_data <- subset(combined_data, main_mode == mode)
      
      mode_data_wide <- mode_data %>%
        select(-main_mode) %>%
        spread(key = scenario_type, value = count, fill = 0) 
      
      scenario_names <- unique(mode_data$scenario_type)
      new_colnames <- c("interval", scenario_names)
      colnames(mode_data_wide) <- new_colnames
      
      output_filename <- paste0(outputDirectoryScenario, "/", "df.", output_filename_prefix, ".", mode, ".TUD.csv")
      write.csv(mode_data_wide, file = output_filename, row.names = FALSE, quote = FALSE)
    }
  }
  
  walking_distance_distribution_by_mode(trips.list.region, legs.list.carfree.area, "walking.distance.distribution.by.mode.region")
  walking_distance_distribution_by_mode(trips.list.city, legs.list.carfree.area, "walking.distance.distribution.by.mode.city")
  walking_distance_distribution_by_mode(trips.list.carfree.area, legs.list.carfree.area, "walking.distance.distribution.by.mode.carfree.area")
}

if(x_trips_number_barchart == 1){
  trips_number_by_mode_barchart <- function(trips_list, output_filename){
    
    calculation <- function(trips){
      trips %>%
        group_by(main_mode) %>%
        summarise(trips_number = n())%>%
        filter(!is.na(main_mode) & main_mode != "drtNorth" & main_mode != "drtSoutheast")
    }
    
    for (i in seq_along(trips_list)) {
      scenario_name <- names(trips_list)[i]
      trips_number_by_mode <- calculation(trips_list[[i]]) %>%
        select(main_mode, trips_number) %>%
        rename(!!scenario_name := trips_number)
      
      if (i == 1) {
        combined_data <- trips_number_by_mode
      } else {
        combined_data <- left_join(combined_data, trips_number_by_mode, by = "main_mode")
      }
    }
    write.csv(combined_data, file = paste0(outputDirectoryScenario, "/", "df.", output_filename, ".TUD.csv"), row.names = FALSE, quote = FALSE)
  }

  trips_number_by_mode_barchart(trips.list.region, "trips.number.by.mode.region")
  trips_number_by_mode_barchart(trips.list.city, "trips.number.by.mode.city")
  trips_number_by_mode_barchart(trips.list.carfree.area, "trips.number.by.mode.carfree.area")
}  

# interested_mode is the mode that its trips main mode changed after policy implementation 
if(x_shifted_trips_average_distance_bar_chart == 1){
  shifted_trips_average_distance <- function(trip_lists, interested_mode, output_filename) {
    
    calculation <- function(base_trips, policy_trips, interested_mode) {
      joined_base_policy <- inner_join(base_trips, policy_trips, by="trip_id", suffix = c("_base", "_policy"))
      
      shifted_mode_trips <- joined_base_policy %>%
        filter(main_mode_base != main_mode_policy)
      
      specific_shifts <- shifted_mode_trips %>%
        filter(main_mode_base == interested_mode, main_mode_policy != interested_mode)
      
      average_distances_shifted_trip <- specific_shifts %>%
        group_by(main_mode_policy) %>%
        summarise(average_distance = mean(traveled_distance_policy, na.rm = TRUE)) %>%
        rename(main_mode = main_mode_policy)
      
      return(average_distances_shifted_trip)
    }
    
    base_trips <- trip_lists$base
    
    for (i in seq_along(trip_lists)) {
      if (names(trip_lists)[i] != "base") { # Skip the base, as it's not a "policy" to be compared.
        scenario_name <- names(trip_lists)[i]
        average_distances_shifted <- calculation(base_trips, trip_lists[[i]], interested_mode) %>%
          select(main_mode, average_distance) %>%
          rename(!!scenario_name := average_distance)
        
        if (!exists("combined_data")) {
          combined_data <- average_distances_shifted
        } else {
          combined_data <- left_join(combined_data, average_distances_shifted, by = "main_mode")
        }
      }
    }
    
    combined_data <- combined_data %>%
      filter(!is.na(main_mode) & main_mode != "drtNorth" & main_mode != "drtSoutheast")

    write.csv(combined_data, file = paste0(outputDirectoryScenario, "/", "df.",interested_mode,".", output_filename, ".TUD.csv"), row.names = FALSE, quote = FALSE)
  }
  
  shifted_trips_average_distance(trips.list.region,"car", "shifted.trips.average.distance.by.mode.region")
  shifted_trips_average_distance(trips.list.city,"car", "shifted.trips.average.distance.by.mode.city")
  shifted_trips_average_distance(trips.list.carfree.area,"car", "shifted.trips.average.distance.by.mode.carfree.area")
  ## for all the modes could be written.
}

print("End of TUD analysis")
