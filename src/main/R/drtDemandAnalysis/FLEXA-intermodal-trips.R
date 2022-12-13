library(tidyverse)
library(readr)
library(sf)
library(ssh)

FILES_DIR = "C:/Users/ACER/Desktop/Uni/VSP/Leipzig/FLEXA/"


####### DOWNLOAD FILES FROM CLUSTER #######
REMOTE_DIR = "zoerner@cluster-a.math.tu-berlin.de"

PROJECT_DIR <- "/net/ils/matsim-leipzig/run-drt/namav-output/runsScaledFleet3-2/"

pattern.trips <- ".output_trips.csv.gz"
pattern.legs <- ".output_legs.csv.gz"

#create ssh connection to math cluster
connection = ssh_connect(host = REMOTE_DIR)

files = capture.output(
  ssh_exec_wait(connection, 
                command = paste0(
                  "ls ", PROJECT_DIR))
)

files = files[str_detect(files, pattern = "Namav")]
print(files)

for(file in files){
  target.dir <- capture.output(
    ssh_exec_wait(connection, 
                  command = paste0(
                    "ls ", PROJECT_DIR, "/", file))
  )
  
  target.files <- target.dir[str_detect(target.dir, pattern = pattern.trips) | str_detect(target.dir, pattern = pattern.legs)]
  
  for(t in target.files) scp_download(connection, files = paste0(PROJECT_DIR, "/", file, "/", t), to = FILES_DIR)
}

ssh_disconnect(connection)
