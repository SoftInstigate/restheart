#!/bin/bash
curl -H 'Cache-Control: no-cache' https://raw.githubusercontent.com/fossas/fossa-cli/master/install.sh | bash
fossa init FOSSA_API_KEY=2fad7584cff11f3b49381e650be03be4 fossa