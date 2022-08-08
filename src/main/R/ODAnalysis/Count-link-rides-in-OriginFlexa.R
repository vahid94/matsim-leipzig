library(geosphere)
library(tidyverse)
library(dplyr)
library(terra)
library(lubridate)
######################
# the parameter, which you have to adapt
programPath <- "/Users/tomkelouisa/Documents/VSP/matsim-leipzig/src/main/R/ODAnalysis" # the function coundrivenstops.R should be there
filePath <- "/Users/tomkelouisa/Documents/VSP/Leipzig" # the outputfile is going to there and the source file should be there
originfilename <- "Flexa_Rides_2021.csv" # source file
#startdate <- "2021-07-02"
startdate <- -1 #when the whole source file should be operated, so write -1, otherwise write the  date you want to start with
tage <- 1 # and here the amount of days you want to have starting at the startdate
nameAnalyseFile <-"stop2stopridesFlexa.tsv" # filename outputfile
#csvfilename <- "stop2stopridesVIA.csv"
setwd(programPath)

source("countdrivenstops.R")



setwd(filePath)
#read source data
flexaDataframe <- read.csv(originfilename, stringsAsFactors = FALSE, header = TRUE, encoding = "UTF-8", sep= ";")

flexaDataframe <- flexaDataframe %>% mutate(actual_departure_time = ymd_hms(actual_departure_time))

if (startdate!=-1){
  nameAnalyseFile <- paste(startdate , "_stop2stopridesFlexa.tsv")
  d <- interval(ymd(startdate),ymd(startdate)+days(x=tage))
  flexaDataframe <- flexaDataframe %>% filter(actual_departure_time %within% d)
}



# counts the amount of stop-to-stop connections
countdrivenlinks(flexaDataframe, 'requested_origin_assigned_flexa_stop', 'requested_destination_assigned_flexa_stop', nameAnalyseFile)




