@echo off

cd %~dp0\..
java -cp lib/winston.jar gov.usgs.volcanoes.winston.PlotHelicorder %1 %2 %3 %4 %5 %6 %7 %8 %9
