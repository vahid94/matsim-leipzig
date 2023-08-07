##### TUD_analysis_script ####
print("TUD file is read")
## TUD Analysis list

## Current idea: presenting base and scenario case in one plot

x_population_seg_filter= 1
x_emissions_barchart = 1
x_average_and_total_travel_distance_by_mode_barchart = 1
## will be integrated with the next commit
x_average_distance_by_mode_just_main_leg_barchart = 0
x_average_walking_distance_by_mode_barchart = 0 
x_walking_distance_distribution_binchart = 0
x_walking_distance_distribution_linechart = 0
x_shifted_trips_average_distance_bar_chart = 0
x_trips_number_barchart = 0
winner_loser_analysis = 0 # Note: A more extensive analysis is performed by TUB.


## base data reading and filtering

# trips reading and filtering
base.trips.table <- readTripsTable(pathToMATSimOutputDirectory = base.run.path)

base.trips.region <- filterByRegion(base.trips.table,region.shape,crs=CRS,start.inshape = TRUE,end.inshape = TRUE)
base.trips.city <- filterByRegion(base.trips.table,city.shape,crs=CRS,start.inshape = TRUE,end.inshape = TRUE)
base.trips.carfree.area <- filterByRegion(base.trips.table, carfree.area.shape, crs=CRS, start.inshape = TRUE, end.inshape = TRUE)

# legs reading and filtering
base.legs.table <- read_delim(paste0(base.run.path,"/",list.files(path = base.run.path, pattern = "output_legs")), delim= ";")#, n_max = 3000)

scenario.legs.region <- filterByRegion(base.legs.table,region.shape,crs=CRS,start.inshape = TRUE,end.inshape = TRUE)
scenario.legs.city <- filterByRegion(base.legs.table,city.shape,crs=CRS,start.inshape = TRUE,end.inshape = TRUE)
scenario.legs.carfree.area <- filterByRegion(base.legs.table, carfree.area.shape, crs=CRS, start.inshape = TRUE, end.inshape = TRUE)

# emission reading
emission_base  <- read_delim(paste0(base.run.path,"/",list.files(path = base.run.path, pattern = "emission")), delim= ";")
emission_scenario <- read_delim(paste0(scenario.run.path,"/",list.files(path = scenario.run.path, pattern = "emission")), delim= ";")

print(" TUD data read and filtered")
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
  total_and_average_distance_by_mode <- function(base_trips, policy_trips, output_filename_total, output_filename_average) {
    
    calculation <- function(trips) {
      trips %>% 
        group_by(main_mode) %>%
        summarize(total_distance = sum(traveled_distance / 1000), # Per KM
                  average_distance = mean(traveled_distance / 1000))
    }
    
    base_distance_by_mode <- calculation(base_trips)
    policy_distance_by_mode <- calculation(policy_trips)
    
    write_csv <- function(base_data, policy_data, columns_to_select, output_filename) {
      merge_df <- merge(base_data, policy_data, by = "main_mode", suffixes = c("_base", "_policy")) %>%
        filter(!(main_mode %in% c('drtNorth', 'drtSoutheast'))) %>%
        select(main_mode, columns_to_select) %>%
        rename(base = all_of(columns_to_select[1]), policy_90 = all_of(columns_to_select[2]))
      write.csv(merge_df, file = paste0(outputDirectoryScenario, "/", "df." ,output_filename, ".TUD.csv"), row.names = FALSE, quote = FALSE)
    }
    
    write_csv(base_distance_by_mode, policy_distance_by_mode, c("total_distance_base", "total_distance_policy"), output_filename_total)
    write_csv(base_distance_by_mode, policy_distance_by_mode, c("average_distance_base", "average_distance_policy"), output_filename_average)
  }
  
  total_and_average_distance_by_mode(base.trips.region, scenario.trips.region, "total.distance.by.mode.region.csv", "average.distance.by.mode.region.csv" )
  total_and_average_distance_by_mode(base.trips.city, scenario.trips.city, "total.distance.by.mode.city.csv", "average.distance.by.mode.city.csv")
  total_and_average_distance_by_mode(base.trips.carfree.area, scenario.trips.carfree.area, "total.distance.by.mode.carfree.area.csv","average.distance.by.mode.carfree.area.csv")
}

print("End of TUD analysis")
