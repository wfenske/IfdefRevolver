---
layout: default
title: Online Appendix for "How Preprocessor Annotations (Do Not) Affect Maintainability" &ndash Estimation of Changes to Annotated Functions Depending on Snapshot Size
---

# Estimation of Changes to Annotated Functions Depending on Snapshot Size

Data to estimate the amount of changes to annotated functions from one
snapshot to the next, depending on snapshot size.

* Snapshot sizes: 50, 100, 200, 400

* System: openldap

## Commands Used

For instance:

```sh
[me@mymachine: ~/src/skunk/snapshotsize-050]
$ stat-function-changes-between-windows.sh --all openldap > \
>  results/openldap/changed*functions.csv
cd ../snapshotsize-100
[me@mymachine: ~/src/skunk/snapshotsize-100]
$ stat-function-changes-between-windows.sh --all openldap > \
>  results/openldap/changed*functions.csv
...
[me@mymachine: ~/src/skunk/snapshotsize-400]
$ stat-function-changes-between-windows.sh --all openldap > \
>  results/openldap/changed*functions.csv
cd ..
$ for d in snapshotsize-*; do
>   echo "*** $d ***";
>   csvstat $d/results/openldap/changed_functions.csv;
> done|g -F -e '***' -e FUNC -e Mean -e Median  |\
>   sed 's/^[[:space:]]*[0-9]\{1,2\}\./*/'

```

## Results for Annotated Functions

### Column Explanation

* `AB_BEFORE`: annotated functions existing in previous snapshot
* `AB_NOW`: annotated functions existing in current snapshot
* `AB_IDENTICAL`: annotated functions existing in both snapshots
* `AB_ADDED`: annotated functions added compared to previous snapshot
* `AB_REMOVED`: annotated functions removed compared to previous snapshot

Metrics about number of annotated functions existing in both snapshots
whose annotation metric changed:
   
* `AB_CH_NOFL`: `NOFL` changed
* `AB_CH_NOFC_NONDUP`: `NOFC_NONDUP` changed
* `AB_CH_NONEST`: `NONEST` changed

Changes expressed as percentages:
   
* `AB_PERC_ADD_REM`: Percentage of added or removed annotated
  functions, compared to `AB_BEFORE`

Percentage of annotated functions that ...

1. underwent at least one commit,
2. exist in both snapshots
3. whose annotation metric changed.
    
* `AB_PERC_CH_NOFL`: % of annotated, changed functions where `NOFL` changed
* `AB_PERC_CH_NOFC_NONDUP`: % of annotated, changed functions where `NOFC_NONDUP` changed
* `AB_PERC_CH_NONEST`: % of annotated, changed functions where `NONEST` changed


### 50 Commits

[Data in CSV format](changed_ab_estimate_openldap_050.csv)

* `AB_BEFORE`

  - Mean: 619.32
  - Median: 586.0
  
* `AB_NOW`

  - Mean: 620.90
  - Median: 586.5
  
* `AB_IDENTICAL`

  - Mean: 608.88
  - Median: 582.0
* `AB_ADDED`

  - Mean: 12.02
  - Median: 7.0
  
* `AB_REMOVED`

  - Mean: 10.43
  - Median: 4.0
  
* `AB_CH_NOFL`

  - Mean: 7.42
  - Median: 5.0
  
* `AB_CH_NOFC_NONDUP`

  - Mean: 5.49
  - Median: 3.0
  
* `AB_CH_NONEST`

  - Mean: 1.93
  - Median: 1.0
  
* `AB_PERC_ADD_REM`

  - Mean: 3.73
  - Median: 2.0
  
* `AB_PERC_CH_NOFL`

  - Mean: 13.19
  - Median: 11.0
  
* `AB_PERC_CH_NOFC_NONDUP`

  - Mean: 9.19
  - Median: 6.0
  
* `AB_PERC_CH_NONEST`

  - Mean: 2.99
  - Median: 1.0


### 100 Commits

[Data in CSV format](changed_ab_estimate_openldap_100.csv)

* `AB_BEFORE`

  - Mean: 618.65
  - Median: 585.5
  
* `AB_NOW`

  - Mean: 621.76
  - Median: 586.5
  
* `AB_IDENTICAL`

  - Mean: 598.56
  - Median: 574.5
  
* `AB_ADDED`

  - Mean: 23.20
  - Median: 16.0
  
* `AB_REMOVED`

  - Mean: 20.09
  - Median: 9.5
  
* `AB_CH_NOFL`

  - Mean: 13.35
  - Median: 10.0
  
* `AB_CH_NOFC_NONDUP`

  - Mean: 10.08
  - Median: 7.0
  
* `AB_CH_NONEST`

  - Mean: 3.60
  - Median: 2.0
  
* `AB_PERC_ADD_REM`

  - Mean: 7.70
  - Median: 4.5
  
* `AB_PERC_CH_NOFL`

  - Mean: 14.32
  - Median: 13.0
  
* `AB_PERC_CH_NOFC_NONDUP`

  - Mean: 10.32
  - Median: 9.0
  
* `AB_PERC_CH_NONEST`

  - Mean: 3.38
  - Median: 2.0


### 200 Commits

[Data in CSV format](changed_ab_estimate_openldap_200.csv)

* `AB_BEFORE`

  - Mean: 617.80
  - Median: 586
  
* `AB_NOW`

  - Mean: 624.10
  - Median: 588
  
* `AB_IDENTICAL`

  - Mean: 579.61
  - Median: 566
  
* `AB_ADDED`

  - Mean: 44.49
  - Median: 37
  
* `AB_REMOVED`

  - Mean: 38.20
  - Median: 22
  
* `AB_CH_NOFL`

  - Mean: 22.86
  - Median: 17
  
* `AB_CH_NOFC_NONDUP`

  - Mean: 17.62
  - Median: 13
  
* `AB_CH_NONEST`

  - Mean: 6.52
  - Median: 5
* `AB_PERC_ADD_REM`

  - Mean: 15.14
  - Median: 10
  
* `AB_PERC_CH_NOFL`

  - Mean: 15.68
  - Median: 15
  
* `AB_PERC_CH_NOFC_NONDUP`

  - Mean: 11.75
  - Median: 10
  
* `AB_PERC_CH_NONEST`

  - Mean: 3.99
  - Median: 3


### 400 Commits

[Data in CSV format](changed_ab_estimate_openldap_400.csv)

* `AB_BEFORE`

  - Mean: 613.66
  - Median: 583
  
* `AB_NOW`

  - Mean: 625.89
  - Median: 588
  
* `AB_IDENTICAL`

  - Mean: 543.57
  - Median: 548
* `AB_ADDED`

  - Mean: 82.31
  - Median: 64
* `AB_REMOVED`

  - Mean: 70.09
  - Median: 52
  
* `AB_CH_NOFL`

  - Mean: 37.29
  - Median: 27
  
* `AB_CH_NOFC_NONDUP`

  - Mean: 29.26
  - Median: 22
  
* `AB_CH_NONEST`

  - Mean: 10.54
  - Median: 7
  
* `AB_PERC_ADD_REM`

  - Mean: 28.71
  - Median: 20
  
* `AB_PERC_CH_NOFL`

  - Mean: 17.37
  - Median: 17
  
* `AB_PERC_CH_NOFC_NONDUP`

  - Mean: 13.20
  - Median: 12
  
* `AB_PERC_CH_NONEST`

  - Mean: 4.40
  - Median: 4


## Results for All Functions

* `FUNC_BEFORE`: functions existing in previous snapshot
* `FUNC_NOW`: functions existing in current snapshot
* `FUNC_IDENTICAL`: functions existing in both snapshots
* `FUNC_ADDED`: functions added compared to previous snapshot
* `FUNC_REMOVED`: functions removed compared to previous snapshot

Changes expressed as percentages:
   
* `FUNC_PERC_ADD_REM`: Percentage of added or removed functions, compared to `FUNC_BEFORE`

* `FUNC_MEDIAN_PERC_LOC_CHG`: Median value of the percentage by which
  the `LOC` metric of a function has changed, given that the function
  received at least one commit.


### 50 Commits

[Data in CSV format](changes_all_functions_openldap_050.csv)

* `FUNC_BEFORE`

  - Mean: 3337.66
  - Median: 3099.0
  
* `FUNC_NOW`

  - Mean: 3353.09
  - Median: 3104.0
  
* `FUNC_IDENTICAL`

  - Mean: 3308.76
  - Median: 3078.0
  
* `FUNC_ADDED`

  - Mean: 44.33
  - Median: 28.0
  
* `FUNC_REMOVED`

  - Mean: 28.90
  - Median: 12.0
  
* `FUNC_PERC_ADD_REM`

  - Mean: 2.42
  - Median: 1.0
  
* `FUNC_MEDIAN_PERC_LOC_CHG`

  - Mean: 2.18
  - Median: 1.72


### 100 Commits

[Data in CSV format](changes_all_functions_openldap_100.csv)

* `FUNC_BEFORE`

  - Mean: 3313.83
  - Median: 3067.0
  
* `FUNC_NOW`

  - Mean: 3343.97
  - Median: 3102.0
  
* `FUNC_IDENTICAL`

  - Mean: 3258.97
  - Median: 3027.5
  
* `FUNC_ADDED`

  - Mean: 85.01
  - Median: 66.0
  
* `FUNC_REMOVED`

  - Mean: 54.87
  - Median: 29.5
  
* `FUNC_PERC_ADD_REM`

  - Mean: 5.13
  - Median: 3.0
  
* `FUNC_MEDIAN_PERC_LOC_CHG`

  - Mean: 2.41
  - Median: 2.02


### 200 Commits

[Data in CSV format](changes_all_functions_openldap_200.csv)

* `FUNC_BEFORE`

  - Mean: 3272.01
  - Median: 3022
  
* `FUNC_NOW`

  - Mean: 3332.52
  - Median: 3097
  
* `FUNC_IDENTICAL`

  - Mean: 3166.76
  - Median: 2998
  
* `FUNC_ADDED`

  - Mean: 165.76
  - Median: 129
  
* `FUNC_REMOVED`

  - Mean: 105.25
  - Median: 69
  
* `FUNC_PERC_ADD_REM`

  - Mean: 10.34
  - Median: 6
  
* `FUNC_MEDIAN_PERC_LOC_CHG`

  - Mean: 3.08
  - Median: 2.41

### 400 Commits

[Data in CSV format](changes_all_functions_openldap_400.csv)

* `FUNC_BEFORE`

  - Mean: 3206.69
  - Median: 2996
  
* `FUNC_NOW`

  - Mean: 3326.86
  - Median: 3097
  
* `FUNC_IDENTICAL`

  - Mean: 3012.69
  - Median: 2949
  
* `FUNC_ADDED`

  - Mean: 314.17
  - Median: 295
  
* `FUNC_REMOVED`

  - Mean: 194.00
  - Median: 148
  
* `FUNC_PERC_ADD_REM`

  - Mean: 20.49
  - Median: 12
  
* `FUNC_MEDIAN_PERC_LOC_CHG`

  - Mean: 3.61
  - Median: 3.08
