#!/usr/bin/env python
# -*- coding: utf-8 -*-

from matsim.scenariogen.data import run_create_ref_data


def person_filter(df):
    """ Default person filter for reference data. """
    return df[df.present_on_day & (df.reporting_day <= 4)]


if __name__ == "__main__":
    person, trips, share = run_create_ref_data.create("../../../../shared-svn/projects/NaMAV/data/SrV_2018",
                                                      person_filter)

    print(share)
