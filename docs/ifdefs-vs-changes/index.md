---
layout: default
title: Online Appendix for "How Preprocessor Annotations (Do Not) Affect Maintainability" (GPCE 2017)
---
# Online Appendix for "How Preprocessor Annotations (Do Not) Affect Maintainability: A Case Study on Change-Proneness" (GPCE 2017)
  
## Tool Support

Data collection for this study was performed by
[IfdefRevolver](https://github.com/wfenske/IfdefRevolver/),
the Github project this page belongs to.
The entry point to the analysis is the Bourne shell script
`ifdefrevolve-project.sh`, located in `src/main/scripts`.
Type `ifdefrevolve-project.sh -h` at the command line to
get a help screen explaining the command line options.
This script relies on a number of helper scripts, which reside in
the same directory.
To get everything to run, this directory should be in the `PATH`
variable in your environment.

More information can be found on the [tool support page.](toolsupport.html)

## Snapshot Size Experiments

Functions can change from one snapshot to the next.  For example,
functions are modified (becoming longer or shorter), and new functions
are added, existing ones are deleted or moved to new files.  All those
changes invalidate (to some extent) the static metrics, which we
collect only once per snapshot.  We also lose some information about
functions being added later on or moved to new files also.

To estimate how small we need to make our snapshots in order to get
accurate results and not miss too much information, we conducted an
initial experiment on `OpenLDAP`.  On a separate page, we list the
[results of the snapshot size experiment.](estimate-of-changes-to-functions-depending-on-snapshot-size/)


## Data
  
The data for all eight subject systems used in the study is
[available here.](data/ifdefs-vs-changes-data.tar.gz) The data is
available both in `.csv` format and as R data sets (files named
`allData.rdata`).

## Subject Systems

The names and repository URLs of the subject systems are listed below.

Subject Name | Repository URL
------------ | --------------
Apache | `git@github.com:apache/httpd.git`
BusyBox | `git://busybox.net/busybox.git`
glibc | `git://sourceware.org/git/glibc.git`
libxml2 | `git@github.com:GNOME/libxml2.git`
OpenLDAP | `https://github.com/osstech-jp/openldap`
OpenVPN | `git@github.com:OpenVPN/openvpn.git`
Pidgin | `git@github.com:tieto/pidgin.git`
SQLite | `git://repo.or.cz/sqlite.git`
