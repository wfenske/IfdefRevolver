#!/usr/bin/env sh

dependent=${1:?Missing dependent variable. Specify either COMMITS, LCH, COMMITSratio, or LCHratio as the first positional argument}

##set -x

csvsql -d ',' -q '"' --table fl,fc,cnd,neg,loc --query "
select fl.system,
       fl.cliffd  'fl',
       fc.cliffd  'fc',
       cnd.cliffd 'cnd',
       neg.cliffd 'neg',
       loc.cliffd 'loc'
from fl
     join fc  on fl.system=fc.system
     join cnd on fl.system=cnd.system
     join neg on fl.system=neg.system
     left join loc on fl.system=loc.system and loc.d='${dependent}' and loc.i='LOC'
where
     fl.d='${dependent}'  and fl.i='FL'   and
     fc.d='${dependent}'  and fc.i='FC'   and
     cnd.d='${dependent}' and cnd.i='ND'  and
     neg.d='${dependent}' and neg.i='NEG'
" fisher-summary.csv fisher-summary.csv fisher-summary.csv fisher-summary.csv fisher-summary.csv
