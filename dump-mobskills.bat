@echo off
@title Dump
set CLASSPATH=.;dist\*;lib\*
java -server -Dnet.sf.odinms.wzpath=wz tools.wztosql.DumpMobSkills
pause