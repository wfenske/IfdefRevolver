---
layout: default
title: Tool Support for "How Preprocessor Annotations (Do Not) Affect Maintainability"
---

# Tool Support

## Dependencies

IfdefRevolver depends on a number of other tools, some of them tools
developed by ourselves, some of them customized third-party tools, and
some third-party tools, used as is.

### Own tools and customized third-party tools

* [cppstats](https://github.com/wfenske/cppstats)

  Customized to improve performance for evolutionary analyses.  Also,
  some bugs were fixed. 

  Be sure to check out the `skunk` branch!  This branch contains
  the aforementioned customizations.

* [Skunk](https://github.com/wfenske/Skunk)

  Initially developed to detect variability-aware code smells, this
  version contains enhancements to report more metrics and to produce
  cleaner output in CSV files.  Function signature parsing was also
  improved. 

  Be sure to check out the `skunk` branch!  This branch contains
  changed and new functionality that IfdefRevolver depends on.

* [repodriller](https://github.com/wfenske/repodriller)

  Enhanced to improve performance on large repositories with many
  commits.  Specifically, the time-consuming identification of
  branches for a commit was made optional. 

  Again, use the code in the `skunk` branch!

### Other Third-Party Tools

* [srcML](http://www.srcml.org/)

  Version used: Trunk 19109c, from May 22 2014

* [CSVKit](https://github.com/wireservice/csvkit)

  Set of command-line tools to work with CSV files.  Used extensively
  to aggregate and transform intermediate results.

  Version used: 1.0.2 (2016)

* [R](https://www.r-project.org/)

  Used for statistical computations and figure creation.  The R
  scripts in `src/main/script` (extension `.R`) require it.

  Version used: 3.3.2 (2016-10-31)

* GMake

  GNU's make implementation

* realpath

  Command that resolves symlinks and relative directories.  Available
  out of the box under most Linuxes but not part of the standard Mac
  OS X installation.  A Mac-compatible version can be installed as
  part of the GNU coreutils.

### Compatibility

`Ifdevrevolver` was successfully used under the following systems:

* Linux (Ubuntu, kernel 4.10.0-26-generic, x86\_64)

* macOS (kernel 16.6.0, x86_64)

Other Unix-like systems will probably work. Windows has not been tested. 

## Installation & Running the Analysis

Please see the instructions at
[IfdefRevolver](https://github.com/wfenske/IfdefRevolver/).
