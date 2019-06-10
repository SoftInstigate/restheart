#!/bin/bash
find . -type d -name .git -exec sh -c "cd \"{}\"/../ && pwd && git $1" \;
