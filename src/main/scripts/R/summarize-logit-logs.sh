#!/bin/sh
grep -e '^\*\*\*' -e '^SIZE' -e AB -e AF -e LF -e OR -e ANY "$@" -e 'Residual Deviance:' -e 'Null Deviance:'|grep -v -e 'Goodness of Fit' -e 'Model 2:'
