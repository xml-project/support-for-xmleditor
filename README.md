# Support for xml editor
An adapter for running XProc pipeline in Oxygen using MorganaXProc

**This adapter is for MorganaXProc only. It does not work with MorganaXProc-III editions.**

Using this adapter you will be able to run [XProc](http://xproc.org/) pipelines directly in Oxygen using [MorganaXProc](http://www.xml-project.com/morganaxproc/). Syntax checks as you type are supported also.

## Installation

1. Download lastest version of [MorganaXProc](http://www.xml-project.com/morganaxproc/) if you have not done yet.
1. Create a new folder called "MorganaXProc" inside Oxygen's folder lib/xproc.
1. Put file "engine.xml" from this repository into lib/xproc/MorganaXProc.
1. Create folder "lib" in lib/xproc/MorganaXProc.
1. Put the following files into this folder:
    * OxygenAdapter.jar (from this repository).
    * xproc-config.xml (from this repository).
    * MorganaXProc.jar (from the MorganaXProc distribution).
    * folder MorganaXProc_lib (from the MorganaXProc distribution) and
    * folder Extensions (from the MorganaXProc distribution).
1. Restart Oxygen.

This description (and the software of course) was checked with Oxygen 18.1 (build 2017020917) and MorganaXProc 1.0.6.

Hints:
* To use <p:xslt /> properly, you have to select either SaxonXSLTAdapter or Saxon97XSLTAdapter for <mox:XSLTConnector /> in xproc-config.xml.
* <p:xsl-formatter /> currently has some issues because different versions of Apache(TM) FOP are expected by Oxygen and MorganaXProc.

**Important:**
Please note that this software is **experimental**, so please be careful using it.

If you encounter any problem, please file an issue.
