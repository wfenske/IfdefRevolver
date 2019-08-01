# IfdefRevolver

Examines correlations between CPP annotations (a.k.a. `#ifdef`s) and
negative effects, such as an increased proneness to faults or changes.

## Dependencies

IfdefRevolver depends on a number of other tools, some of them tools
developed by ourselves, some of them customized third-party tools, and
some third-party tools, used as is.

### Own tools and customized third-party tools

* cppstats: https://github.com/wfenske/cppstats

  Customized to improve performance for evolutionary analyses.  Also,
  some bugs were fixed. 

  Be sure to check out the `skunk` branch!  This branch contains
  the aforementioned customizations.

* Skunk: https://github.com/wfenske/Skunk

  Initially developed to detect variability-aware code smells, this
  version contains enhancements to report more metrics and to produce
  cleaner output in CSV files.  Function signature parsing was also
  improved.

* repodriller: https://github.com/wfenske/repodriller

  Enhanced to improve performance on large repositories with many
  commits.  Specifically, the time-consuming identification of
  branches for a commit was made optional.

  Again, use the code in the `skunk` branch!

### Other third-party tools

* srcML: http://www.srcml.org/

  Version used: Trunk 19109c, from May 22 2014

* CSVKit: https://github.com/wireservice/csvkit

  Set of command-line tools to work with CSV files.  Used extensively
  to aggregate and transform intermediate results.

  Version used: 1.0.2 (2016)
  
* xsltproc: http://xmlsoft.org/XSLT/xsltproc.html

  A command line xslt processor, used internally by `cppstats`, e.g.,
  to remove comments.

* R: https://www.r-project.org/

  Used for statistical computations and figure creation.  The R
  scripts in `src/main/script` (extension `.R`) require it.

  Version used: 3.3.2 (2016-10-31) 

## Compatibility

`Ifdevrevolver` was successfully used under the following systems:

* Linux (Ubuntu, kernel 4.10.0-26-generic, x86\_64)

* macOS (kernel 16.6.0, x86_64)

Other Unix-like systems will probably work. Windows has not been tested. 

## Installation

1. Install srcML

   Make sure it is in your `PATH`, i.e., that `src2srcml` and
   `srcml2src` reside in a directory that is in the list of
   directories in your `PATH` environment variable.
   
1. Install xsltproc

   Under the Ubuntu distribution of Linux, the package `xsltproc`
   provides this program.  Again, make sure that `xsltproc` is in your
   `PATH`.  Otherwise, `cppstats` will terminate with errno 2 ("No
   such file or directory"), and the stackstrace will contain a call
   to `deleteComments`, which in turn calls `runBashCommand`.

1. Install cppstats

   Be sure to checkout out the correct fork and branch (see above).

   I installed cppstats using Python version 2.7.6, a somewhat dated
   Python version, because cppstats does not work with Python 3.
   Cppstats has a number of dependencies, such as `enum` (version 
   0.4.4 works), `pyparsing`, `statlib`, `lxml`, `pyparsing`.  Most,
   if not all of them, can be installed via Pythons `pip` packet
   manager.  Assuming Python is installed under `/opt/python-2.7.6`,
   the command would be

   `sudo -H /opt/python-2.7.6/bin/pip install pyparsing`

   After installing the prerequisites, install Cppstats itself via

   `sudo -H /opt/python-2.7.6/bin/python setup.py install`
	
   The main program, `cppstats`, also needs to be in your `PATH`.
   
1. Install CSVKit

   CSVKit is a collection of programs, among them `csvsql`, `csvcut`,
   `csvjoin`.  If these are missing, the analysis phase of
   IfdefRevolver will fail with an error along the lines of:
   
   `function_change_hunks2function_change_commits.sh: csvsql: not found`
   
   Being a Python package, CSVKit can be installed via `pip`:
   
   `sudo -H pip install csvkit`
	
   Make sure it is in your `PATH` so that the IfdefRevolver scripts
   find it at runtime.  Check whether it works by typing
   
   `csvsql --help`

1. Install Skunk

   Installation works via Maven, i.e., go to the checkout, then change
   to the `Skunk` subdirectory (`cd Skunk`), then do

   `mvn install`

   Add the directory containing the script `skunk.sh` to your `PATH`.

1. Install repodriller

   Be sure to checkout out the correct fork and branch (see above).

   Installation works via Maven, i.e., go to the checkout, then do

   `mvn install`

1. Install IfdefRevolver 

   Installation works via Maven, i.e., go to the checkout, then do

   `mvn install`

   Add the IfdefRevolver's subdirectory `src/main/scripts` to your
   `PATH`.  E.g., if you checked out IfdefRevolver to
   `~/src/IfdefRevolver`, then add
   `~/src/IfdefRevolver/src/main/scripts` to your `PATH` variable.

## Running

1. Create a folder where you want to do your analysis and change to it.

1. Create a subfolder `repos` inside that folder.

1. Copy the smell detection definitions from Skunk:

   1. Create a directory `smellconfigs`

   1. Copy
      `<IfdefRevolver>/src/main/resources/smellconfigs/AnnotationBundle_low_thresholds.csm`
      to `smellconfigs/AnnotationBundle.csm`

   1. Copy some variant of `AnnotationFile.csm` from the same source
      directory to `smellconfigs/AnnotationFile.csm` (It's not
      actually used at this time.)

   1. Copy some variant of `LargeFeature.csm` from the same source
      directory to `smellconfigs/LargeFeature.csm` (It's not
      actually used at this time.)

1. Clone a repository:

   1. Go to the `repos` folder.

   1. Clone the repo, e.g.

      `git clone git@github.com:MyProject/myproject.git`
	  
   1. Change back up (`cd ..`)

1. Run `ifdefrevolver`:

   `ifdefrevolve-project.sh -p myproject`

   This will start the analysis. It will create a directory with the
   same name as the project, and this directory contains
   subdirectories `snapshots` (for project checkouts), `logs` (for log
   data), and `results` (for analysis results such as changes, CPP use, etc.).

   In case anything goes wrong:

   - You'll see some output such as

     ``make: *** [myproject/results/.analysis_successful] Error 2``

   - Inspect the log messages are in the `logs` subdirectory of the
	 project directory. Different log
	 files are created for different phases of the analysis process:

     1. lscommits.log
     1. lsparentcommits.log
     1. checkout.log
	 1. analyze.log

	 Find the one with the most recent timestamp to get to the bottom
	 of the error that last stopped you.

   - After fixing the problem (most likely a helper program not being
     found), you can continue the analysis with the same command you
     used to start it in the first place, e.g.:

     `ifdefrevolve-project.sh -p myproject`
	 
1. Analysis is done when the command finished without an error code
   and without suspicious output.  `analysis.log` should conclude with
   a line saying something like
   
   ``
   INFO 10:42:42 Successfully output aggregated change data for all 42 windows.
   make[1]: Leaving directory '/home/user/projects'
   ``

1. Start the statistical analyses

   ``make -f <IfdefRevolver>/src/main/scripts/stat-project.mk PROJECT=myproject``
