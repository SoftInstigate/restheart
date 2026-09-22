#!/usr/bin/env bash
# GraalPy on the JVM (no standalone distribution needed). A venv created with
# this script gets bin/python -> this script, and pip works through it.
LIB=/pylauncher/lib
exec java --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow \
  -Dpolyglot.engine.userResourceCache=/cache -Dpolyglot.engine.WarnInterpreterOnly=false \
  -Dorg.graalvm.launcher.executablename="$0" \
  -cp "$LIB/*" com.oracle.graal.python.shell.GraalPythonMain "$@"
