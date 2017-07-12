---
layout: default
title: Tool Support for "How Preprocessor Annotations (Do Not) Affect Maintainability"
---

# IfdefRevolver

Examines correlations between CPP annotations (a.k.a. #ifdefs) and
negative effects, such as an increased proneness to faults or changes.

## Dependencies

IfdefRevolver depends on a number of other tools, some of them tools
developed by ourselves, some of them customized third-party tools, and
some third-party tools, used as is.

### Own tools and customized third-party tools

* [cppstats](https://github.com/wfenske/cppstats)

  Customized to improve performance for evolutionary analyses.  Also,
  some bugs were fixed.

* [Skunk](https://github.com/wfenske/Skunk)

  Initially developed to detect variability-aware code smells, this
  version contains enhancements to report more metrics and to produce
  cleaner output in CSV files.  Function signature parsing was also
  improved.

* [repodriller](https://github.com/wfenske/repodriller)

  Enhanced to improve performance on large repositories with many
  commits.  Specifically, the time-consuming identification of
  branches for a commit was made optional.

### Other third-party tools

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
