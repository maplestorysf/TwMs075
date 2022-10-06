@echo off
@title MapleTW
color 0B
set CLASSPATH=.;dist\*;lib\*
java -server -Dnet.sf.odinms.wzpath=wz server.Start
pause