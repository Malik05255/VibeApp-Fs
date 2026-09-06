package com.malik.lmai.plugin;

interface IPluginInspector {
    String dumpViewTree(String optionsJson);
    String executeAction(String actionJson);
    String captureScreenshot(String optionsJson);
}
