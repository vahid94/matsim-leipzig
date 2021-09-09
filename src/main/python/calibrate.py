#!/usr/bin/env python
# -*- coding: utf-8 -*-

import os

import pandas as pd
import geopandas as gpd
import numpy as np

from  matsim import calibration

#%%

def x():
    srv = pd.read_csv("srv.csv")
    sim = pd.read_csv("sim.csv")
    
    _, adj = calibration.calc_adjusted_mode_share(sim, srv)
    
    print(adj.groupby("mode").sum())


#%%

modes = ["walk", "car", "ride", "pt", "bike"]
fixed_mode = "walk"
initial = {
    "bike": -1.9,
    "pt": -0.7,
    "car": -1.4,
    "ride": -4
}

# Original target
target = {
    "walk": 0.243,
    "bike": 0.206,
    "pt": 0.162,
    "car": 0.301,
    "ride": 0.086    
}

# Adjusted for distance distribution
target = {
    "bike": 0.205680,
    "car":  0.321617,
    "pt":   0.186261,
    "ride": 0.093713,
    "walk": 0.192729    
}

city = gpd.read_file("../scenarios/input/leipzig-utm32n/leipzig-utm32n.shp")

def f(persons):    
    df = gpd.sjoin(persons.set_crs("EPSG:25832"), city, how="inner", op="intersects")    
    return df

def filter_freight(df):
    return df[df.main_mode != "freight"]

study, obj = calibration.create_mode_share_study("calib", "matsim-leipzig-1.0-SNAPSHOT.jar", 
                                        "../scenarios/input/leipzig-v1.0-25pct.config.xml", 
                                        modes, target,
                                        initial_asc=initial,
                                        args="--10pct",
                                        jvm_args="-Xmx46G -Xms46G -XX:+AlwaysPreTouch",
                                        person_filter=f, map_trips=filter_freight)


#%%

study.optimize(obj, 10)
