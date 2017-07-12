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

* [Skunk](https://github.com/wfenske/Skunk)

  Initially developed to detect variability-aware code smells, this
  version contains enhancements to report more metrics and to produce
  cleaner output in CSV files.  Function signature parsing was also
  improved.

* [repodriller](https://github.com/wfenske/repodriller)

  Enhanced to improve performance on large repositories with many
  commits.  Specifically, the time-consuming identification of
  branches for a commit was made optional.

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

### Compatibility

`Ifdevrevolver` was sucessfully used under the following systems:

* Linux (Ubuntu, kernel 4.10.0-26-generic, x86\_64)

* macOS (kernel 16.6.0, x86_64)

Other Unix-like systems will probably work. Windows has not been tested. 
	
[Home](/IfdefRevolver/)
