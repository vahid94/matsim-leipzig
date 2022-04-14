library(lubridate)
library(tidyverse)
library(dplyr)
library(ggplot2)
library(plotly)
library(hrbrthemes)

#####################################################################
####################################################
### INPUT DEFINITIONS ###

# set working directory
setwd("C:/Users/Simon/Documents/shared-svn/projects/NaMAV/data/Flexa/")

# read data
allData <- read.csv2("Nachfragedaten_LVB_2021/Nachfragedaten_Flexa_2021_anonymisiert_filtered.csv", stringsAsFactors = FALSE, header = TRUE, encoding = "UTF-8")

# convert time columns + determine weekday for every request
allData <- allData %>%
  mutate(request_time = ymd_hms(request_time, tz="Europe/Berlin"),
         actual_departure_time = ymd_hms(actual_departure_time, tz="Europe/Berlin"),
         actual_arrival_time = ymd_hms(actual_arrival_time, tz="Europe/Berlin"),
  ) %>%
  mutate(weekday_request = wday(request_time, label = TRUE))


# if TRUE, then interactive plots are produced. if FALSE, plots are dumped out as pngs to 'plots' folder
interactiveMode = TRUE

#####################################################################
####################################################
### SCRIPT ###

####################################################
## PLOT ALL REQUESTS ##

requests <- allData %>% 
  select(request_time) %>%
  mutate(date_request = date(request_time))

reqProTag <- requests %>% 
  group_by( date_request = date(request_time)) %>%
  tally()

###########
# plot time line
p <- reqProTag %>%
  ggplot( aes(x=date_request, y=n)) +
  ggtitle("Zeitverlauf der FLEXA-Anfragen pro Tag") +
  geom_area(fill="#69b3a2", alpha=0.5) +
  geom_line(color="#69b3a2") +
  ylab("Requests") +
  xlab("Tag") + 
  theme_ipsum()

##would put this behind an if or else condition but does not work for me :/
plotFile = "plots/FLEXA_2021_requests.png"
paste("printing plot to ", plotFile)
png(plotFile, width = 1200, height = 800)  
p
dev.off()
if(interactiveMode){
  ggplotly(p)  
}

############  
#group by weekday
reqProWochentag <- reqProTag %>% 
  group_by( weekday_request = wday(date_request, label = TRUE )) %>%
  filter(!is.na(weekday_request)) %>%
  summarise(avg = mean(n))

#plot avg nr of requests per weekday
p <- reqProWochentag %>%
  ggplot( aes(x=weekday_request, y=avg)) +
  ggtitle("Durchschn. Anzahl FLEXA-Requests pro Wochentag") +
  geom_bar(color="#69b3a2", stat = "identity") + 
  ylab("Durchschn. Anzahl Requests") +
  xlab("Wochentag") + 
  theme_ipsum()

#would put this behind an if or else condition but does not work for me :/
plotFile = "plots/FLEXA_2021_requests_weekdays.png"
paste("printing plot to ", plotFile)
png(plotFile, width = 1200, height = 800)  
p
dev.off()
if(interactiveMode){
  ggplotly(p)  
}

# 5min intervals
requestsPerInterval <- requests %>% 
  mutate (interval = floor( (minute(request_time) + hour(request_time) * 60 ) /5) )  %>%
  filter(!is.na(interval)) %>% 
  group_by(interval) %>% 
  tally()

p <- requestsPerInterval %>%
  ggplot( aes(x=interval * 5/60, y=n)) +
  ggtitle("FLEXA-Requests pro 5 Minuten-Intervall") +
  geom_area(fill="#69b3a2", alpha=0.5) +
  geom_line(color="#69b3a2") +
  ylab("Anzahl Requests pro Intervall") +
  xlab("Stunde") + 
  theme_ipsum()

##would put this behind an if or else condition but does not work for me :/
plotFile = "plots/FLEXA_2021_requests_daily.png"
paste("printing plot to ", plotFile)
png(plotFile, width = 1200, height = 800)  
p
dev.off()
if(interactiveMode){
  ggplotly(p)  
} 


####################################################
## PLOT COMPLETED RIDES ##

#filter only realized rides
#there are some 43 rides, which have an arrival time but do not have a departure time
#better filter them out
completedRides <- allData %>%
  filter(actual_arrival_time > 0) %>%
  filter(actual_departure_time > 0)

#group per day
ridesProTag <- completedRides %>% 
  group_by(date_ride = date(actual_departure_time)) %>%
  tally()
  
# plot time line
p <- ridesProTag %>%
  ggplot( aes(x=date_ride, y=n)) +
  geom_area(fill="#69b3a2", alpha=0.5) +
  geom_line(color="#69b3a2") +
  ylab("Fahrten") +
  xlab("Tag") + 
  ggtitle("Zeitverlauf der FLEXA-Fahrten pro Tag") +
  theme_ipsum()

#would put this behind an if or else condition but does not work for me :/
plotFile = "plots/FLEXA_2021_rides.png"
paste("printing plot to ", plotFile)
png(plotFile, width = 1200, height = 800)  
p
dev.off()
if(interactiveMode){
  ggplotly(p)  
} 


################
#group by weekday
ridesProWochentag <- ridesProTag %>% 
  group_by( weekday_ride = wday(date_ride, label = TRUE) ) %>%
  filter(!is.na(weekday_ride)) %>%
  summarise(avg = mean(n))
#plot avg nr of requests per weekday
p <- ridesProWochentag %>%
  ggplot( aes(x=weekday_ride, y=avg)) +
  geom_bar(color="#69b3a2", stat = "identity") + 
  ylab("Durchschn. Anzahl Fahrten") +
  xlab("Tag") + 
  ggtitle("Durchschn. Anzahl FLEXA-Fahrten pro Wochentag") +
  theme_ipsum()

##would put this behind an if or else condition but does not work for me :/
plotFile = "plots/FLEXA_2021_rides_weekdays.png"
paste("printing plot to ", plotFile)
png(plotFile, width = 1200, height = 800)  
p
dev.off()
if(interactiveMode){
  ggplotly(p)  
} 

################
# PLOT Saturday RIDES
#why is saturday 7 and not 6????
saturdays <- completedRides %>% 
  mutate(weekday_ride = wday(actual_departure_time)) %>%
  filter(weekday_ride == 7) %>%
  group_by(date_ride = date(actual_departure_time)) %>%
  tally()

p <- saturdays %>%
  ggplot( aes(x=date_ride, y=n)) +
  ggtitle("Saturdays") +
  geom_area(fill="#69b3a2", alpha=0.5) +
  geom_line(color="#69b3a2") +
  ylab("Fahrten") +
  xlab("Tag") + 
  ggtitle("Zeitverlauf der FLEXA-Fahrten pro Samstag") +
  theme_ipsum()

#would put this behind an if or else condition but does not work for me :/
plotFile = "plots/FLEXA_2021_rides_saturdays.png"
paste("printing plot to ", plotFile)
png(plotFile, width = 1200, height = 800)  
p
dev.off()
if(interactiveMode){
  ggplotly(p)  
}

# PLOT Sunday RIDES
sundays <- completedRides %>%
  mutate(weekday_ride = wday(actual_departure_time)) %>%
  filter(weekday_ride == 1) %>%
  group_by(date_ride = date(actual_departure_time)) %>%
  tally()

p <- sundays %>%
  ggplot( aes(x=date_ride, y=n)) +
  ggtitle("Saturdays") +
  geom_area(fill="#69b3a2", alpha=0.5) +
  geom_line(color="#69b3a2") +
  ylab("Fahrten") +
  xlab("Tag") +
  ggtitle("Zeitverlauf der FLEXA-Fahrten pro Sonntag") +
  theme_ipsum()

#would put this behind an if or else condition but does not work for me :/
plotFile = "plots/FLEXA_2021_rides_sundays.png"
paste("printing plot to ", plotFile)
png(plotFile, width = 1200, height = 800)
p
dev.off()
if(interactiveMode){
  ggplotly(p)
}

ridesPerInterval <- completedRides %>% 
  mutate (interval = floor( (minute(actual_departure_time) + hour(actual_departure_time) * 60) / 5)  )  %>%
  group_by(interval) %>% 
  tally()

p <- ridesPerInterval %>%
  ggplot( aes(x=interval*5/60, y=n)) +
  ggtitle("FLEXA-Fahrten pro 5-Minuten-Intervall") +
  geom_area(fill="#69b3a2", alpha=0.5) +
  geom_line(color="#69b3a2") +
  ylab("Anzahl Fahrten") +
  xlab("Stunde") +
  theme_ipsum()

#would put this behind an if or else condition but does not work for me :/
plotFile = "plots/FLEXA_2021_rides_daily.png"
paste("printing plot to ", plotFile)
png(plotFile, width = 1200, height = 800)  
p
dev.off()
if(interactiveMode){
  ggplotly(p)  
} 



#plot day time of saturdays
saturdays_day <- completedRides %>% 
  mutate(weekday_ride = wday(actual_departure_time), interval = floor( (minute(actual_departure_time) + hour(actual_departure_time) * 60) / 5) ) %>%
  filter(weekday_ride == 7) %>%
  group_by(interval) %>% 
  tally()

p <- saturdays_day %>%
  ggplot( aes(x=interval*5/60, y=n)) +
  ggtitle("SA: FLEXA-Fahrten pro 5-Minuten-Intervall") +
  geom_area(fill="#69b3a2", alpha=0.5) +
  geom_line(color="#69b3a2") +
  ylab("Anzahl Fahrten") +
  xlab("Stunde") +
  theme_ipsum()


##would put this behind an if or else condition but does not work for me :/
plotFile = "plots/FLEXA_2021_rides_saturdays_daily.png"
paste("printing plot to ", plotFile)
png(plotFile, width = 1200, height = 800)  
p
dev.off()
if(interactiveMode){
  ggplotly(p)  
}

#plot day time of sundays
sundays_day <- completedRides %>%
  mutate(weekday_ride = wday(actual_departure_time), interval = floor( (minute(actual_departure_time) + hour(actual_departure_time) * 60) / 5) ) %>%
  filter(weekday_ride == 1) %>%
  group_by(interval) %>%
  tally()

p <- sundays_day %>%
  ggplot( aes(x=interval*5/60, y=n)) +
  ggtitle("SO: FLEXA-Fahrten pro 5-Minuten-Intervall") +
  geom_area(fill="#69b3a2", alpha=0.5) +
  geom_line(color="#69b3a2") +
  ylab("Anzahl Fahrten") +
  xlab("Stunde") +
  theme_ipsum()


##would put this behind an if or else condition but does not work for me :/
plotFile = "plots/FLEXA_2021_rides_sundays_daily.png"
paste("printing plot to ", plotFile)
png(plotFile, width = 1200, height = 800)
p
dev.off()
if(interactiveMode){
  ggplotly(p)
}

#################################################################

angefragtPerInterval <- allData %>% 
  select(request_time) %>%
  mutate (interval = floor( (minute(request_time) + hour(request_time) * 60) / 5)  )  %>%
  group_by(interval) %>% 
  tally() %>% 
  rename(nAngefragt = n)


#joined <- full_join(requestsPerInterval, ridesPerInterval, by="interval", suffix = c("Requests", "Abfahrt")) %>%
joined <- full_join(requestsPerInterval, ridesPerInterval, by="interval", suffix = c("Requests", "Abfahrt")) %>% 
  full_join(.,angefragtPerInterval, by = "interval") %>% 
  filter(!is.na(interval)) %>% 
  mutate(interval = as.numeric(interval)) %>% 
  replace_na(list(interval = -1000,nRequests = 0, nRides=0))

gathered <- joined %>% 
  gather(key = "variable", value = "value", -interval)

p <- gathered %>%
  ggplot( aes(x=interval * 5/60, y=value)) +
  #ggtitle("Requests pro 5 Minuten-Intervall") + 
  #geom_area(fill="#69b3a2", alpha=0.5) +
  geom_line(aes(color = variable)) +
  ylab("Anzahl pro Intervall") +
  xlab("Stunde") + 
  theme_ipsum() +
  scale_color_manual(values = c("darkgreen" , "darkred", "steelblue"))
ggplotly(p)


#the following does not apply to the analyzed data set as we only have one column for request_time
#I'll leave it here though, maybe it can be of use in the future
# requests_timeDiffs <- allData %>%
#   select(request_time, Requested.time) %>%
#   mutate(diff = seconds(Requested.time - Ride.request.time)) %>%
#   filter(!is.na(diff))
#
# mean(requests_timeDiffs$diff) /3600
#
# hist(requests_timeDiffs$diff/3600)
#
# rides_timeDiffs <- allData %>%
#   filter(Status == "Completed") %>%
#   select(Ride.request.time, Requested.time) %>%
#   mutate(diff = seconds(Requested.time - Ride.request.time)) %>%
#   filter(!is.na(diff))
#
# mean(rides_timeDiffs$diff) /3600
#
# hist(rides_timeDiffs$diff/3600)
#
# p <- requests_timeDiffs %>%
#   ggplot( aes(x=Ride.request.time, y=Requested.time)) +
#     #ggtitle("Requests pro 5 Minuten-Intervall") +
#     #geom_area(fill="#69b3a2", alpha=0.5) +
#     geom_line()
#
# ggplotly(p)



  







