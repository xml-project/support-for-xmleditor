# support-for-xmleditor
An adapter for running XProc pipeline in Oxygen using MorganaXProc

Using this adapter, you will be able to run [XProc](http://xproc.org/) pipelines directly in Oxygen using [MorganaXProc](http://www.xml-project.com/morganaxproc/). Syntax checks as you type are supported also.

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

