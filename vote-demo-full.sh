#!/bin/bash

if [ -f /usr/share/vestige/vestigessh ]; then
  export PATH=/usr/share/vestige/:$PATH
else
  export PATH=/Applications/Vestige.app/Contents/Resources/vestige_home/:$PATH
fi

vestigessh stop vpl
vestigessh stop vp
vestigessh stop state
vestigessh stop vreg
vestigessh stop citizen
vestigessh uninstall vreg
vestigessh uninstall state
vestigessh uninstall vpl
vestigessh uninstall vp
vestigessh uninstall citizen
vestigessh install lo vote-rmiregistry 1.0.0 vreg
vestigessh install lo vote-state 1.0.0 state
vestigessh install lo vote-polling-station 1.0.0 vpl
vestigessh install lo vote-party 1.0.0 vp
vestigessh install lo vote-citizen 1.0.0 citizen
vestigessh start vreg
sleep 1
vestigessh start state
sleep 7
vestigessh start vp
sleep 4
vestigessh start vpl
sleep 4
vestigessh start citizen