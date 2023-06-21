#!/usr/bin/env python
# -*- coding: utf-8 -*-

import os

import pandas as pd
import geopandas as gpd
import numpy as np

import calibration

#%%

modes = ["walk", "car", "ride", "pt", "bike"]
fixed_mode = "walk"
initial = {
    "bike": -0.56,
    "pt": 0.01,
    "car": -0.799,
    "ride": -5.30
}

# Mode share target
target = {
    "walk": 0.227812,
    "bike": 0.217869,
    "pt": 0.166917,
    "car": 0.296633,
    "ride": 0.090769
}


city = gpd.read_file("../scenarios/input/leipzig-utm32n/leipzig-utm32n.shp")

def f(persons):
    persons = gpd.GeoDataFrame(persons, geometry=gpd.points_from_xy(persons.home_x, persons.home_y))

    df = gpd.sjoin(persons.set_crs("EPSG:25832"), city, how="inner", op="intersects")

    print("Filtered %s persons" % len(df))

    return df

def filter_modes(df):
    return df[df.main_mode.isin(modes)]

study, obj = calibration.create_mode_share_study("calib", "matsim-leipzig-1.2-SNAPSHOT-8ad10fc.jar",
                                                 "../input/v1.2/leipzig-v1.2-25pct.config.xml",
                                                 modes, target,
                                                 initial_asc=initial,
                                                 args="--10pct",
                                                 jvm_args="-Xmx46G -Xms46G -XX:+AlwaysPreTouch",
                                                 transform_persons=f, transform_trips=filter_modes,
                                                 lr=calibration.linear_lr_scheduler(start=0.3, interval=6),
                                                 chain_runs=calibration.default_chain_scheduler)


#%%

study.optimize(obj, 4)