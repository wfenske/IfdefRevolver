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

  Mean: 619.3172413793103

  Median: 586.0
* `AB_NOW`

  Mean: 620.903448275862

  Median: 586.5
* `AB_IDENTICAL`

  Mean: 608.8827586206896

  Median: 582.0
* `AB_ADDED`
  Mean: 12.020689655172413

  Median: 7.0
* `AB_REMOVED`

  Mean: 10.434482758620689

  Median: 4.0
* `AB_CH_NOFL`

  Mean: 7.417241379310345

  Median: 5.0
* `AB_CH_NOFC_NONDUP`

  Mean: 5.4862068965517246

  Median: 3.0
* `AB_CH_NONEST`

  Mean: 1.9310344827586208

  Median: 1.0
* `AB_PERC_ADD_REM`

  Mean: 3.7344827586206897

  Median: 2.0
* `AB_PERC_CH_NOFL`

  Mean: 13.1931034483

  Median: 11.0
* `AB_PERC_CH_NOFC_NONDUP`

  Mean: 9.18620689655

  Median: 6.0
* `AB_PERC_CH_NONEST`

  Mean: 2.99310344828

  Median: 1.0


### 100 Commits

[Data in CSV format](changed_ab_estimate_openldap_100.csv)

* `AB_BEFORE`

  Mean: 618.6458333333334

  Median: 585.5
* `AB_NOW`

  Mean: 621.7569444444445

  Median: 586.5
* `AB_IDENTICAL`

  Mean: 598.5555555555555

  Median: 574.5
* `AB_ADDED`

  Mean: 23.20138888888889

  Median: 16.0
* `AB_REMOVED`

  Mean: 20.09027777777778

  Median: 9.5
* `AB_CH_NOFL`

  Mean: 13.347222222222221

  Median: 10.0
* `AB_CH_NOFC_NONDUP`

  Mean: 10.07638888888889

  Median: 7.0
* `AB_CH_NONEST`

  Mean: 3.5972222222222223

  Median: 2.0
* `AB_PERC_ADD_REM`

  Mean: 7.701388888888889

  Median: 4.5
* `AB_PERC_CH_NOFL`

  Mean: 14.3194444444

  Median: 13.0
* `AB_PERC_CH_NOFC_NONDUP`

  Mean: 10.3194444444

  Median: 9.0
* `AB_PERC_CH_NONEST`

  Mean: 3.375

  Median: 2.0


### 200 Commits

[Data in CSV format](changed_ab_estimate_openldap_200.csv)

* `AB_BEFORE`

  Mean: 617.8028169014085

  Median: 586
* `AB_NOW`

  Mean: 624.0985915492957

  Median: 588
* `AB_IDENTICAL`

  Mean: 579.6056338028169

  Median: 566
* `AB_ADDED`

  Mean: 44.49295774647887

  Median: 37
* `AB_REMOVED`

  Mean: 38.19718309859155

  Median: 22
* `AB_CH_NOFL`

  Mean: 22.859154929577464

  Median: 17
* `AB_CH_NOFC_NONDUP`

  Mean: 17.619718309859156

  Median: 13
* `AB_CH_NONEST`

  Mean: 6.52112676056338

  Median: 5
* `AB_PERC_ADD_REM`

  Mean: 15.140845070422536

  Median: 10
* `AB_PERC_CH_NOFL`

  Mean: 15.676056338

  Median: 15
* `AB_PERC_CH_NOFC_NONDUP`

  Mean: 11.7464788732

  Median: 10
* `AB_PERC_CH_NONEST`

  Mean: 3.98591549296

  Median: 3


### 400 Commits

[Data in CSV format](changed_ab_estimate_openldap_400.csv)

* `AB_BEFORE`

  Mean: 613.6571428571428

  Median: 583
* `AB_NOW`

  Mean: 625.8857142857142

  Median: 588
* `AB_IDENTICAL`

  Mean: 543.5714285714286

  Median: 548
* `AB_ADDED`

  Mean: 82.31428571428572

  Median: 64
* `AB_REMOVED`

  Mean: 70.08571428571429

  Median: 52
* `AB_CH_NOFL`

  Mean: 37.285714285714285

  Median: 27
* `AB_CH_NOFC_NONDUP`

  Mean: 29.257142857142856

  Median: 22
* `AB_CH_NONEST`

  Mean: 10.542857142857143

  Median: 7
* `AB_PERC_ADD_REM`

  Mean: 28.714285714285715

  Median: 20
* `AB_PERC_CH_NOFL`

  Mean: 17.3714285714

  Median: 17
* `AB_PERC_CH_NOFC_NONDUP`

  Mean: 13.2

  Median: 12
* `AB_PERC_CH_NONEST`

  Mean: 4.4

  Median: 4


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

  Mean: 3337.6620689655174

  Median: 3099.0
* `FUNC_NOW`

  Mean: 3353.093103448276

  Median: 3104.0
* `FUNC_IDENTICAL`

  Mean: 3308.7620689655173

  Median: 3078.0
* `FUNC_ADDED`

  Mean: 44.33103448275862

  Median: 28.0
* `FUNC_REMOVED`

  Mean: 28.9

  Median: 12.0
* `FUNC_PERC_ADD_REM`

  Mean: 2.420689655172414

  Median: 1.0
* `FUNC_MEDIAN_PERC_LOC_CHG`

  Mean: 2.17968965517

  Median: 1.72


### 100 Commits

[Data in CSV format](changes_all_functions_openldap_100.csv)

* `FUNC_BEFORE`

  Mean: 3313.8333333333335

  Median: 3067.0
* `FUNC_NOW`

  Mean: 3343.972222222222

  Median: 3102.0
* `FUNC_IDENTICAL`

  Mean: 3258.965277777778

  Median: 3027.5
* `FUNC_ADDED`

  Mean: 85.00694444444444

  Median: 66.0
* `FUNC_REMOVED`

  Mean: 54.86805555555556

  Median: 29.5
* `FUNC_PERC_ADD_REM`

  Mean: 5.131944444444445

  Median: 3.0
* `FUNC_MEDIAN_PERC_LOC_CHG`

  Mean: 2.40854166667

  Median: 2.02


### 200 Commits

[Data in CSV format](changes_all_functions_openldap_200.csv)

* `FUNC_BEFORE`

  Mean: 3272.0140845070423

  Median: 3022
* `FUNC_NOW`

  Mean: 3332.521126760563

  Median: 3097
* `FUNC_IDENTICAL`

  Mean: 3166.760563380282

  Median: 2998
* `FUNC_ADDED`

  Mean: 165.7605633802817

  Median: 129
* `FUNC_REMOVED`

  Mean: 105.25352112676056

  Median: 69
* `FUNC_PERC_ADD_REM`

  Mean: 10.338028169014084

  Median: 6
* `FUNC_MEDIAN_PERC_LOC_CHG`

  Mean: 3.07605633803

  Median: 2.41

### 400 Commits

[Data in CSV format](changes_all_functions_openldap_400.csv)

* `FUNC_BEFORE`

  Mean: 3206.6857142857143

  Median: 2996
* `FUNC_NOW`

  Mean: 3326.8571428571427

  Median: 3097
* `FUNC_IDENTICAL`

  Mean: 3012.6857142857143

  Median: 2949
* `FUNC_ADDED`

  Mean: 314.1714285714286

  Median: 295
* `FUNC_REMOVED`

  Mean: 194.0

  Median: 148
* `FUNC_PERC_ADD_REM`

  Mean: 20.485714285714284

  Median: 12
* `FUNC_MEDIAN_PERC_LOC_CHG`

  Mean: 3.60857142857

  Median: 3.08
